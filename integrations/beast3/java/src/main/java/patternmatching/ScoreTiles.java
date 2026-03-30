package patternmatching;

import org.phylospec.ast.*;
import org.phylospec.typeresolver.TypeResolver;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScoreTile implements AstVisitor<Integer, Integer, Integer> {

    private final TypeResolver typeResolver;
    private final List<Tile> tiles;

    private final Map<AstNode, List<ScoredTile>> scoredTiles;

    public ScoreTile(List<Tile> tiles, TypeResolver typeResolver) {
        this.tiles = tiles;
        this.typeResolver = typeResolver;
        this.scoredTiles = new HashMap<>();
    }

    @Override
    public Integer visitLiteral(Expr.Literal expr) {
        return this.visitExpr(expr);
    }

    @Override
    public Integer visitStringTemplate(Expr.StringTemplate expr) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Integer visitVariable(Expr.Variable expr) {
        return this.visitExpr(expr);
    }

    @Override
    public Integer visitUnary(Expr.Unary expr) {
        expr.right.accept(this);
        return this.visitExpr(expr);
    }

    @Override
    public Integer visitBinary(Expr.Binary expr) {
        expr.left.accept(this);
        expr.right.accept(this);
        return this.visitExpr(expr);
    }

    @Override
    public Integer visitCall(Expr.Call expr) {
        for (Expr.Argument argument : expr.arguments) {
            argument.expression.accept(this);
        }
        return this.visitExpr(expr);
    }

    @Override
    public Integer visitAssignedArgument(Expr.AssignedArgument expr) {
        return this.visitExpr(expr.expression);
    }

    @Override
    public Integer visitDrawnArgument(Expr.DrawnArgument expr) {
        return this.visitExpr(expr.expression);
    }

    @Override
    public Integer visitGrouping(Expr.Grouping expr) {
        return this.visitExpr(expr.expression);
    }

    @Override
    public Integer visitArray(Expr.Array expr) {
        for (Expr element : expr.elements) {
            element.accept(this);
        }
        return this.visitExpr(expr);
    }

    @Override
    public Integer visitIndex(Expr.Index expr) {
        expr.object.accept(this);
        for (Expr index : expr.indices) {
            index.accept(this);
        }
        return this.visitExpr(expr);
    }

    @Override
    public Integer visitRange(Expr.Range range) {
        range.from.accept(this);
        range.to.accept(this);
        return this.visitExpr(range);
    }

    private Integer visitExpr(Expr expr) {
        for (Tile tile : this.tiles) {
            if (tile.canOperate(expr, this.scoredTiles)) {

            }
        }
    }

    /*
     * Unused visitors
     */

    @Override
    public Integer visitDecoratedStmt(Stmt.Decorated stmt) {
        return stmt.statement.accept(this);
    }

    @Override
    public Integer visitAssignment(Stmt.Assignment stmt) {
        return stmt.expression.accept(this);
    }

    @Override
    public Integer visitDraw(Stmt.Draw stmt) {
        return stmt.expression.accept(this);
    }

    @Override
    public Integer visitImport(Stmt.Import stmt) {
        return 0;
    }

    @Override
    public Integer visitIndexedStmt(Stmt.Indexed indexed) {
        return indexed.statement.accept(this);
    }

    @Override
    public Integer visitObservedAsStmt(Stmt.ObservedAs observedAs) {
        return observedAs.stmt.accept(this);
    }

    @Override
    public Integer visitObservedBetweenStmt(Stmt.ObservedBetween observedBetween) {
        return observedBetween.stmt.accept(this);
    }

    @Override
    public Integer visitAtomicType(AstType.Atomic expr) {
        return 0;
    }

    @Override
    public Integer visitGenericType(AstType.Generic expr) {
        return 0;
    }

}
