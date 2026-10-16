package com.mf.mylauncher.core;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Fails closed unless the installed client/libraries are verified and the selected Java runtime
 * reports the major version required by this Minecraft version.
 *
 * This is a prerequisite check, not proof that Android's graphics/native runtime can launch the game.
 */
public final class LaunchPreflight {
    private final JavaRuntimeProbe runtimeProbe;

    public LaunchPreflight() {
        this(new JavaRuntimeProbe());
    }

    LaunchPreflight(JavaRuntimeProbe runtimeProbe) {
        if (runtimeProbe == null) throw new IllegalArgumentException("Java runtime probe is required");
        this.runtimeProbe = runtimeProbe;
    }

    public Result inspect(VersionDetails details, LibraryPlatform platform, Path gameDirectory,
                          Path javaExecutable, CancellationToken cancellation, long timeoutMillis)
            throws IOException, InterruptedException {
        if (details == null) throw new IllegalArgumentException("Version details are required");
        if (javaExecutable == null) throw new IllegalArgumentException("Java executable is required");
        if (cancellation == null) throw new IllegalArgumentException("Cancellation token is required");

        ClasspathResolver.Result classpath = ClasspathResolver.resolve(details, platform, gameDirectory);
        JavaRuntimeProbe.RuntimeInfo runtime = runtimeProbe.inspect(javaExecutable, gameDirectory,
                details.getJavaMajorVersion(), cancellation, timeoutMillis);
        return new Result(classpath, runtime);
    }

    public static final class Result {
        private final ClasspathResolver.Result classpath;
        private final JavaRuntimeProbe.RuntimeInfo runtime;

        private Result(ClasspathResolver.Result classpath, JavaRuntimeProbe.RuntimeInfo runtime) {
            this.classpath = classpath;
            this.runtime = runtime;
        }

        public ClasspathResolver.Result getClasspath() { return classpath; }
        public JavaRuntimeProbe.RuntimeInfo getRuntime() { return runtime; }
        public int getRequiredJavaMajorVersion() { return runtime.getMajorVersion(); }
    }
}
