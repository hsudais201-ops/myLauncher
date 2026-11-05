package com.flame.launcher.content

/**
 * Resolves the dependency graph for explicitly selected content versions.
 *
 * The lookup callback must return a version compatible with the target instance.
 * A null dependency versionId lets the provider choose its preferred compatible
 * version; a non-blank versionId is treated as a strict pin.
 */
object MinecraftContentDependencyResolver {
    private const val MAX_RESOLVED_PROJECTS = 256

    data class ProjectKey(
        val provider: ContentProviderId,
        val projectId: String
    )

    sealed interface Result {
        data class Resolved(
            /** Dependencies appear before the projects that require them. */
            val installOrder: List<MinecraftContentVersion>
        ) : Result

        data class Failed(val reason: Failure) : Result
    }

    sealed interface Failure {
        data object NoRootVersions : Failure
        data class InvalidVersionReference(
            val provider: ContentProviderId,
            val projectId: String,
            val versionId: String
        ) : Failure
        data class InvalidReference(val owner: ProjectKey, val dependencyProjectId: String) : Failure
        data class MissingRequiredDependency(val project: ProjectKey, val dependencyProjectId: String) : Failure
        data class ProviderLookupFailed(
            val project: ProjectKey,
            val dependencyProjectId: String,
            val message: String
        ) : Failure
        data class InvalidLookupResult(val expected: ProjectKey, val returned: ProjectKey) : Failure
        data class PinnedVersionMismatch(
            val project: ProjectKey,
            val expectedVersionId: String,
            val actualVersionId: String
        ) : Failure
        data class ConflictingVersions(
            val project: ProjectKey,
            val firstVersionId: String,
            val secondVersionId: String
        ) : Failure
        data class CircularDependency(val project: ProjectKey) : Failure
        data class IncompatibleDependency(val project: ProjectKey, val incompatibleProject: ProjectKey) : Failure
        data object TooManyProjects : Failure
    }

    fun resolve(
        roots: List<MinecraftContentVersion>,
        findVersion: (
            provider: ContentProviderId,
            projectId: String,
            versionId: String?
        ) -> MinecraftContentVersion?
    ): Result {
        if (roots.isEmpty()) return Result.Failed(Failure.NoRootVersions)

        val selected = LinkedHashMap<ProjectKey, MinecraftContentVersion>()
        val visiting = HashSet<ProjectKey>()
        val visited = HashSet<ProjectKey>()
        val installOrder = mutableListOf<MinecraftContentVersion>()

        // Register every explicit user selection before resolving any dependencies.
        // This prevents an unpinned dependency from silently replacing a selected version.
        for (root in roots) {
            if (root.projectId.isBlank() || root.versionId.isBlank()) {
                return Result.Failed(
                    Failure.InvalidVersionReference(root.provider, root.projectId, root.versionId)
                )
            }
            val key = ProjectKey(root.provider, root.projectId)
            val existing = selected[key]
            if (existing != null && existing.versionId != root.versionId) {
                return Result.Failed(
                    Failure.ConflictingVersions(key, existing.versionId, root.versionId)
                )
            }
            selected[key] = root
            if (selected.size > MAX_RESOLVED_PROJECTS) {
                return Result.Failed(Failure.TooManyProjects)
            }
        }

        fun visit(version: MinecraftContentVersion): Failure? {
            if (version.projectId.isBlank() || version.versionId.isBlank()) {
                return Failure.InvalidVersionReference(
                    version.provider,
                    version.projectId,
                    version.versionId
                )
            }

            val key = ProjectKey(version.provider, version.projectId)
            val existing = selected[key]
            if (existing != null && existing.versionId != version.versionId) {
                return Failure.ConflictingVersions(key, existing.versionId, version.versionId)
            }
            if (key in visiting) return Failure.CircularDependency(key)
            if (key in visited) return null
            if (selected.size >= MAX_RESOLVED_PROJECTS && key !in selected) {
                return Failure.TooManyProjects
            }

            selected[key] = version
            visiting += key

            for (dependency in version.dependencies) {
                when (dependency.type) {
                    DependencyType.REQUIRED -> {
                        if (dependency.projectId.isBlank()) {
                            return Failure.InvalidReference(key, dependency.projectId)
                        }

                        val dependencyKey = ProjectKey(version.provider, dependency.projectId)
                        val requestedVersionId = dependency.versionId?.takeIf(String::isNotBlank)
                        val alreadySelected = selected[dependencyKey]
                        if (alreadySelected != null) {
                            if (requestedVersionId != null &&
                                requestedVersionId != alreadySelected.versionId
                            ) {
                                return Failure.ConflictingVersions(
                                    dependencyKey,
                                    alreadySelected.versionId,
                                    requestedVersionId
                                )
                            }
                            if (dependencyKey in visiting) {
                                return Failure.CircularDependency(dependencyKey)
                            }
                            if (dependencyKey !in visited) {
                                val failure = visit(alreadySelected)
                                if (failure != null) return failure
                            }
                            continue
                        }

                        val resolved = try {
                            findVersion(version.provider, dependency.projectId, requestedVersionId)
                        } catch (exception: Exception) {
                            return Failure.ProviderLookupFailed(
                                key,
                                dependency.projectId,
                                exception.message ?: exception.javaClass.simpleName
                            )
                        } ?: return Failure.MissingRequiredDependency(key, dependency.projectId)

                        if (resolved.provider != version.provider ||
                            resolved.projectId != dependency.projectId
                        ) {
                            return Failure.InvalidLookupResult(
                                dependencyKey,
                                ProjectKey(resolved.provider, resolved.projectId)
                            )
                        }
                        if (resolved.projectId.isBlank() || resolved.versionId.isBlank()) {
                            return Failure.InvalidVersionReference(
                                resolved.provider,
                                resolved.projectId,
                                resolved.versionId
                            )
                        }
                        if (requestedVersionId != null && resolved.versionId != requestedVersionId) {
                            return Failure.PinnedVersionMismatch(
                                dependencyKey,
                                requestedVersionId,
                                resolved.versionId
                            )
                        }

                        val failure = visit(resolved)
                        if (failure != null) return failure
                    }

                    DependencyType.INCOMPATIBLE,
                    DependencyType.OPTIONAL,
                    DependencyType.EMBEDDED -> Unit
                }
            }

            visiting -= key
            visited += key
            installOrder += version
            return null
        }

        for (root in roots) {
            val failure = visit(root)
            if (failure != null) return Result.Failed(failure)
        }

        // An incompatible declaration blocks only when both projects are selected.
        // Merely browsing a project must not reject it because of unrelated content.
        for ((key, version) in selected) {
            for (dependency in version.dependencies) {
                if (dependency.type != DependencyType.INCOMPATIBLE) continue
                if (dependency.projectId.isBlank()) {
                    return Result.Failed(Failure.InvalidReference(key, dependency.projectId))
                }
                val incompatibleKey = ProjectKey(version.provider, dependency.projectId)
                if (incompatibleKey in selected) {
                    return Result.Failed(Failure.IncompatibleDependency(key, incompatibleKey))
                }
            }
        }

        return Result.Resolved(installOrder)
    }
}
