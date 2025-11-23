package org.phylospec.ast;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.SIMPLE_NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "nodeType"
)
public abstract class AstNode { }
