package org.phylospec.repository;

import java.io.IOException;

/**
 * Thrown when a component repository can neither be reached nor found in the local cache.
 */
public class OfflineException extends IOException {

    public OfflineException(String message, Throwable cause) {
        super(message, cause);
    }
}
