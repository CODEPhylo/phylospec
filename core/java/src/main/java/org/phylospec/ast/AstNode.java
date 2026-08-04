package org.phylospec.ast;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.phylospec.lexer.Range;

@JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, include = JsonTypeInfo.As.PROPERTY, property = "nodeType")
public abstract class AstNode {
    private Range range;

    public void attachRange(Range range) {
        this.range = range;
    }

    public Range getRange() {
        return this.range;
    }
}
