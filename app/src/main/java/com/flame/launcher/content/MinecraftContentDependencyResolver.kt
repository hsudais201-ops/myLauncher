package com.flame.launcher.content

/**
 * Resolves the dependency graph for explicitly selected content versions.
 *
 * The lookup callback must return a version compatible with the target instance.
 * A null dependency versionId lets the provider choose its preferred compatible
 * version; a non-null versionId is treated as a strict pin.
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
        data class InvalidReference(val owner: ProjectKey, val dependencyProjectId: String) : Failure
        data class MissingRequiredDependency(val project: ProjectKey, val dependencyProjectId: String) : Failure
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

        // Register every explicit selection first, so an unpinned dependency
        // can reuse a version the user has already selected as another root.
        for (root in roots) {
            val key = ProjectKey(root.provider, root.projectId)
            val existing = selected[key]
            if (existing != null && existing.versionId != root.versionId) {
                return Result.Failed(
                    Failure.ConflictingVersions(key, existing.versionId, root.versionId)
                )
            }
            selected[key] = root
        }

        fun visit(version: MinecraftContentVersion): Failure? {
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
                if (dependency.type != DependencyType.REQUIRED) continue
                if (dependency.projectId.isBlank()) {
                    return Failure.InvalidReference(key, dependency.projectId)
                }

                val dependencyKey = ProjectKey(version.provider, dependency.projectId)
                val alreadySelected = selected[dependencyKey]
                if (alreadySelected != null) {
                    val pinned = dependency.versionId
                    if (pinned != null && pinned != alreadySelected.versionId) {
                        return Failure.ConflictingVersions(
                            dependencyKey,
                            alreadySelected.versionId,
                            pinned
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

                val resolved = findVersion(
                    version.provider,
                    dependency.projectId,
                    dependency.versionId
                ) ?: return Failure.MissingRequiredDependency(key, dependency.projectId)

                if (resolved.provider != version.provider ||
                    resolved.projectId != dependency.projectId
                ) {
                    return Failure.InvalidLookupResult(
                        dependencyKey,
                        ProjectKey(resolved.provider, resolved.projectId)
                    )
                }
                if (dependency.versionId != null && resolved.versionId != dependency.versionId) {
                    return Failure.PinnedVersionMismatch(
                        dependencyKey,
                        dependency.versionId,
                        resolved.versionId
                    )
                }

                val failure = visit(resolved)
                if (failure != null) return failure
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

        // Incompatible edges only block when the conflicting project is also
        // part of the selected roots or their required dependency closure.
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
