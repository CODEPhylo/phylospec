package org.phylospec.ast;

public class TypeError extends RuntimeException {
    public TypeError(String message) {
        super(message);
    }
}
