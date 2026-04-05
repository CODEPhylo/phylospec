package patternmatching;

import org.phylospec.ast.*;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;

import java.util.*;

public class EvaluateTiles implements AstVisitor<Tile<?>, Tile<?>, Tile<?>> {

    private final TypeResolver typeResolver;
    private final List<Tile<?>> tiles;

    private final Map<AstNode, Set<Tile<?>>> evaluatedTiles;
    private final Map<AstNode, Tile<?>> bestEvaluatedTiles;
    private final VariableResolver variableResolver;

    private final Set<Stmt> consumedStatements;

    public EvaluateTiles(List<Tile<?>> tiles, TypeResolver typeResolver, VariableResolver variableResolver) {
        this.tiles = tiles;
        this.typeResolver = typeResolver;
        this.variableResolver = variableResolver;
        this.evaluatedTiles = new HashMap<>();
        this.bestEvaluatedTiles = new HashMap<>();
        this.consumedStatements = new HashSet<>();
    }

    public List<Tile<?>> getBestTiling(List<Stmt> statements) {
        // we start with the last statements and go backwards

        List<Tile<?>> bestTiles = new ArrayList<>();

        for (int i = statements.size() - 1; i >= 0 ; i--) {
            Stmt stmt = statements.get(i);

            if (this.consumedStatements.contains(stmt)) continue;

            Tile<?> bestTile = stmt.accept(this);
            bestTiles.addFirst(bestTile);
        }

        return bestTiles;
    }

    public BEASTState applyBestTiling(List<Stmt> stmts) {
        List<Tile<?>> bestTilingComposition = this.getBestTiling(stmts);

        BEASTState beastState = new BEASTState();

        for (Tile<?> bestTiling : bestTilingComposition) {
            bestTiling.applyTile(beastState);
        }

        return beastState;
    }

    @Override
    public Tile<?> visitAssignment(Stmt.Assignment stmt) {
        stmt.expression.accept(this);
        return this.visitNode(stmt);
    }

    @Override
    public Tile<?> visitDraw(Stmt.Draw stmt) {
        stmt.expression.accept(this);
        return this.visitNode(stmt);
    }

    @Override
    public Tile<?> visitLiteral(Expr.Literal expr) {
        return this.visitNode(expr);
    }

    @Override
    public Tile<?> visitStringTemplate(Expr.StringTemplate expr) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Tile<?> visitVariable(Expr.Variable expr) {
        Stmt variableDefinitionStmt = this.variableResolver.resolveVariable(expr);

        if (variableDefinitionStmt != null) {
            this.consumedStatements.add(variableDefinitionStmt);

            // we evaluate the tiles for the definition statement

            Tile<?> bestTile = variableDefinitionStmt.accept(this);

            // we re-use the tiles from the definition statement

            this.evaluatedTiles.put(
                    expr, this.evaluatedTiles.get(variableDefinitionStmt)
            );
            this.bestEvaluatedTiles.put(
                    expr, this.bestEvaluatedTiles.get(variableDefinitionStmt)
            );

            return bestTile;
        } else {
            return this.visitNode(expr);
        }
    }

    @Override
    public Tile<?> visitUnary(Expr.Unary expr) {
        expr.right.accept(this);
        return this.visitNode(expr);
    }

    @Override
    public Tile<?> visitBinary(Expr.Binary expr) {
        expr.left.accept(this);
        expr.right.accept(this);
        return this.visitNode(expr);
    }

    @Override
    public Tile<?> visitCall(Expr.Call expr) {
        for (Expr.Argument argument : expr.arguments) {
            argument.expression.accept(this);
        }
        return this.visitNode(expr);
    }

    @Override
    public Tile<?> visitAssignedArgument(Expr.AssignedArgument expr) {
        return this.visitNode(expr.expression);
    }

    @Override
    public Tile<?> visitDrawnArgument(Expr.DrawnArgument expr) {
        return this.visitNode(expr.expression);
    }

    @Override
    public Tile<?> visitGrouping(Expr.Grouping expr) {
        return this.visitNode(expr.expression);
    }

    @Override
    public Tile<?> visitArray(Expr.Array expr) {
        for (Expr element : expr.elements) {
            element.accept(this);
        }
        return this.visitNode(expr);
    }

    @Override
    public Tile<?> visitIndex(Expr.Index expr) {
        expr.object.accept(this);
        for (Expr index : expr.indices) {
            index.accept(this);
        }
        return this.visitNode(expr);
    }

    @Override
    public Tile<?> visitRange(Expr.Range range) {
        range.from.accept(this);
        range.to.accept(this);
        return this.visitNode(range);
    }

    private Tile<?> visitNode(AstNode node) {
        if (this.bestEvaluatedTiles.containsKey(node)) {
            return this.bestEvaluatedTiles.get(node);
        }

        int lowestWeight = Integer.MAX_VALUE;
        Tile<?> bestEvaluatedTile = null;

        this.evaluatedTiles.putIfAbsent(node, new HashSet<>());

        for (Tile<?> tile : this.tiles) {
            Set<? extends Tile<?>> evaluatedTiles = tile.tryToTile(node, this.evaluatedTiles, this.typeResolver, this.variableResolver);

            this.evaluatedTiles.get(node).addAll(evaluatedTiles);

            for (Tile<?> evaluatedTile : evaluatedTiles) {
                if (evaluatedTile.getWeight() < lowestWeight) {
                    lowestWeight = evaluatedTile.getWeight();
                    bestEvaluatedTile = evaluatedTile;
                }
            }
        }

        this.bestEvaluatedTiles.put(node, bestEvaluatedTile);
        return bestEvaluatedTile;
    }

    /*
     * Unused visitors
     */

    @Override
    public Tile<?> visitDecoratedStmt(Stmt.Decorated stmt) {
        return stmt.statement.accept(this);
    }

    @Override
    public Tile<?> visitImport(Stmt.Import stmt) {
        return null;
    }

    @Override
    public Tile<?> visitIndexedStmt(Stmt.Indexed indexed) {
        return indexed.statement.accept(this);
    }

    @Override
    public Tile<?> visitObservedAsStmt(Stmt.ObservedAs observedAs) {
        return observedAs.stmt.accept(this);
    }

    @Override
    public Tile<?> visitObservedBetweenStmt(Stmt.ObservedBetween observedBetween) {
        return observedBetween.stmt.accept(this);
    }

    @Override
    public Tile<?> visitAtomicType(AstType.Atomic expr) {
        return null;
    }

    @Override
    public Tile<?> visitGenericType(AstType.Generic expr) {
        return null;
    }

}
