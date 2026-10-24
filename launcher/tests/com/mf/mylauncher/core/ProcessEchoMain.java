package com.mf.mylauncher.core;

/** Tiny, harmless child main class packaged into a temporary JAR for launch-pipeline integration tests. */
public final class ProcessEchoMain {
    private ProcessEchoMain() { }

    public static void main(String[] args) {
        System.out.println(String.join("|", args));
    }
}
