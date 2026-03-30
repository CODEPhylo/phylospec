package patternmatching;

import org.phylospec.ast.*;
import org.phylospec.typeresolver.TypeResolver;

import java.util.*;

public class EvaluateTiles implements AstVisitor<Object, Object, Object> {

    private final TypeResolver typeResolver;
    private final List<Tile> tiles;

    private final Map<AstNode, Set<EvaluatedTile>> evaluatedTiles;

    public EvaluateTiles(List<Tile> tiles, TypeResolver typeResolver) {
        this.tiles = tiles;
        this.typeResolver = typeResolver;
        this.evaluatedTiles = new HashMap<>();
    }

    @Override
    public Object visitLiteral(Expr.Literal expr) {
        return this.visitExpr(expr);
    }

    @Override
    public Object visitStringTemplate(Expr.StringTemplate expr) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object visitVariable(Expr.Variable expr) {
        return this.visitExpr(expr);
    }

    @Override
    public Object visitUnary(Expr.Unary expr) {
        expr.right.accept(this);
        return this.visitExpr(expr);
    }

    @Override
    public Object visitBinary(Expr.Binary expr) {
        expr.left.accept(this);
        expr.right.accept(this);
        return this.visitExpr(expr);
    }

    @Override
    public Object visitCall(Expr.Call expr) {
        for (Expr.Argument argument : expr.arguments) {
            argument.expression.accept(this);
        }
        return this.visitExpr(expr);
    }

    @Override
    public Object visitAssignedArgument(Expr.AssignedArgument expr) {
        return this.visitExpr(expr.expression);
    }

    @Override
    public Object visitDrawnArgument(Expr.DrawnArgument expr) {
        return this.visitExpr(expr.expression);
    }

    @Override
    public Object visitGrouping(Expr.Grouping expr) {
        return this.visitExpr(expr.expression);
    }

    @Override
    public Object visitArray(Expr.Array expr) {
        for (Expr element : expr.elements) {
            element.accept(this);
        }
        return this.visitExpr(expr);
    }

    @Override
    public Object visitIndex(Expr.Index expr) {
        expr.object.accept(this);
        for (Expr index : expr.indices) {
            index.accept(this);
        }
        return this.visitExpr(expr);
    }

    @Override
    public Object visitRange(Expr.Range range) {
        range.from.accept(this);
        range.to.accept(this);
        return this.visitExpr(range);
    }

    private Object visitExpr(Expr expr) {
        int bestScore = 0;
        Object bestGeneratedObject = null;

        for (Tile tile : this.tiles) {
            Set<EvaluatedTile> evaluatedTiles = tile.tryToTile(expr, this.evaluatedTiles, this.typeResolver);

            for (EvaluatedTile evaluatedTile : evaluatedTiles) {
                this.evaluatedTiles.computeIfAbsent(expr, x -> new HashSet<>()).add(evaluatedTile);

                if (bestScore < evaluatedTile.score()) {
                    bestScore = evaluatedTile.score();
                    bestGeneratedObject = evaluatedTile.generatedObject();
                }
            }
        }

        return bestGeneratedObject;
    }

    /*
     * Unused visitors
     */

    @Override
    public Object visitDecoratedStmt(Stmt.Decorated stmt) {
        return stmt.statement.accept(this);
    }

    @Override
    public Object visitAssignment(Stmt.Assignment stmt) {
        return stmt.expression.accept(this);
    }

    @Override
    public Object visitDraw(Stmt.Draw stmt) {
        return stmt.expression.accept(this);
    }

    @Override
    public Object visitImport(Stmt.Import stmt) {
        return null;
    }

    @Override
    public Object visitIndexedStmt(Stmt.Indexed indexed) {
        return indexed.statement.accept(this);
    }

    @Override
    public Object visitObservedAsStmt(Stmt.ObservedAs observedAs) {
        return observedAs.stmt.accept(this);
    }

    @Override
    public Object visitObservedBetweenStmt(Stmt.ObservedBetween observedBetween) {
        return observedBetween.stmt.accept(this);
    }

    @Override
    public Object visitAtomicType(AstType.Atomic expr) {
        return null;
    }

    @Override
    public Object visitGenericType(AstType.Generic expr) {
        return null;
    }

}
