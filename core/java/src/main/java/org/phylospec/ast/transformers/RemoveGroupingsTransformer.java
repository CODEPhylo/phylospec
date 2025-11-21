package org.phylospec.ast.transformers;


import org.phylospec.ast.AstTransformer;
import org.phylospec.ast.Expr;

/**
 * This transformation removes all Grouping nodes from the syntax tree and replaces them
 * with the expression within the group.
 */
public class RemoveGroupingsTransformer extends AstTransformer {
    @Override
    public Expr visitGrouping(Expr.Grouping expr) {
        return expr.expression.accept(this);
    }
}
