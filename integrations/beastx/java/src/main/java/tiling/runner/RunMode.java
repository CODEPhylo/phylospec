package tiling.runner;

/**
 * Defines how far the BEAST X run pipeline should execute.
 */
public enum RunMode {
    BUILD_STATE,
    BUILD_MODEL,
    BUILD_MCMC,
    EXECUTE_MCMC
}
