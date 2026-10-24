package com.mf.mylauncher.core;

/** Cooperative cancellation contract for installer work and active file transfers. */
@FunctionalInterface
public interface CancellationToken {
    CancellationToken NONE = () -> false;

    boolean isCancelled();
}
