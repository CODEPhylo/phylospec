package org.phylospec.errors;

public interface ErrorEventListener {
    void errorDetected(Error error);

    default void warningDetected(Error warning) {}
}
