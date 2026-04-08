package tiling;

import org.phylospec.ast.*;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.VariableResolver;

import java.util.*;

/**
 * Visits an AST and determines the best tiling for each statement by selecting the lowest-weight
 * tile that matches. Statements consumed by a multi-statement tile are skipped at the top level
 * so they are not tiled a second time.
 */
public class EvaluateTiles implements AstVisitor<Tile<?>, Tile<?>, Tile<?>> {

    private final List<Tile<?>> tiles;

    // memoisation caches: all candidates per node, and the single best candidate per node
    private final Map<AstNode, Set<Tile<?>>> evaluatedTiles;
    private final Map<AstNode, Tile<?>> bestEvaluatedTiles;
    private final VariableResolver variableResolver;
    private final StochasticityResolver stochasticityResolver;

    // statements that have already been claimed by a tile covering multiple statements
    private final Set<Stmt> consumedStatements;

    public EvaluateTiles(List<Tile<?>> tiles, VariableResolver variableResolver, StochasticityResolver stochasticityResolver) {
        this.tiles = tiles;
        this.variableResolver = variableResolver;
        this.stochasticityResolver = stochasticityResolver;
        this.evaluatedTiles = new HashMap<>();
        this.bestEvaluatedTiles = new HashMap<>();
        this.consumedStatements = new HashSet<>();
    }

    /**
     * Returns the best (lowest-weight) tile for each top-level statement in the list.
     * Iteration goes from last to first so that a tile can eagerly consume preceding statements
     * before those statements are visited independently.
     *
     * @param statements the top-level statements to tile
     * @return one best tile per unconsumed statement, in source order
     */
    public List<Tile<?>> getBestTiling(List<Stmt> statements) {
        // we start with the last statements and go backwards

        List<Tile<?>> bestTiles = new ArrayList<>();

        for (int i = statements.size() - 1; i >= 0; i--) {
            Stmt stmt = statements.get(i);

            if (this.consumedStatements.contains(stmt)) continue;

            Tile<?> bestTile = stmt.accept(this);
            bestTiles.addFirst(bestTile);
        }

        return bestTiles;
    }

    /**
     * Computes the best tiling for the given statements and applies each tile in order,
     * building up a {@link BEASTState} that represents the fully-generated BEAST model.
     *
     * @param stmts the top-level statements to tile and apply
     * @return the accumulated BEAST model state after all tiles have been applied
     */
    public BEASTState applyBestTiling(List<Stmt> stmts) {
        List<Tile<?>> bestTilingComposition = this.getBestTiling(stmts);

        BEASTState beastState = new BEASTState();

        for (Tile<?> bestTiling : bestTilingComposition) {
            bestTiling.apply(beastState);
        }

        return beastState;
    }

    /* visitor helpers */

    /**
     * Finds the best tile for {@code node} by asking every registered tile to attempt a match,
     * then returning the one with the lowest weight. Results are memoised so the same node is
     * never evaluated twice.
     *
     * @param node the AST node to tile
     * @return the lowest-weight tile that matched, or {@code null} if no tile matched
     */
    private Tile<?> visitNode(AstNode node) {
        if (this.bestEvaluatedTiles.containsKey(node)) {
            return this.bestEvaluatedTiles.get(node);
        }

        int lowestWeight = Integer.MAX_VALUE;
        Tile<?> bestEvaluatedTile = null;

        this.evaluatedTiles.putIfAbsent(node, new HashSet<>());

        for (Tile<?> tile : this.tiles) {
            Set<? extends Tile<?>> evaluatedTiles = tile.tryToTile(
                    node, this.evaluatedTiles, this.variableResolver, this.stochasticityResolver
            );

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

    /* visitor methods */

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
    public Tile<?> visitDecoratedStmt(Stmt.Decorated stmt) {
        stmt.decorator.accept(this);
        stmt.statement.accept(this);
        return this.visitNode(stmt);
    }

    @Override
    public Tile<?> visitImport(Stmt.Import stmt) {
        return this.visitNode(stmt);
    }

    @Override
    public Tile<?> visitIndexedStmt(Stmt.Indexed indexed) {
        indexed.statement.accept(this);
        for (Expr.Variable index : indexed.indices) {
            index.accept(this);
        }
        for (Expr range : indexed.ranges) {
            range.accept(this);
        }
        return this.visitNode(indexed);
    }

    @Override
    public Tile<?> visitObservedAsStmt(Stmt.ObservedAs observedAs) {
        observedAs.stmt.accept(this);
        observedAs.observedAs.accept(this);
        return this.visitNode(observedAs);
    }

    @Override
    public Tile<?> visitObservedBetweenStmt(Stmt.ObservedBetween observedBetween) {
        observedBetween.stmt.accept(this);
        observedBetween.observedFrom.accept(this);
        observedBetween.observedTo.accept(this);
        return this.visitNode(observedBetween);
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
        // we try to jump to the definition statement of this variable to allow a tiling to cover multiple statements

        Stmt variableDefinitionStmt = this.variableResolver.resolveVariable(expr);

        if (variableDefinitionStmt != null) {
            this.consumedStatements.add(variableDefinitionStmt);
            Tile<?> bestTile = variableDefinitionStmt.accept(this);

            // we re-use the found tiles from the definition statement for the variable

            this.evaluatedTiles.put(
                    expr, this.evaluatedTiles.get(variableDefinitionStmt)
            );
            this.bestEvaluatedTiles.put(
                    expr, this.bestEvaluatedTiles.get(variableDefinitionStmt)
            );

            return bestTile;
        } else {
            // this is a scoped index variable, we visit it normally
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

    @Override
    public Tile<?> visitAtomicType(AstType.Atomic expr) {
        return this.visitNode(expr);
    }

    @Override
    public Tile<?> visitGenericType(AstType.Generic expr) {
        for (AstType typeParameter : expr.typeParameters) {
            typeParameter.accept(this);
        }
        return this.visitNode(expr);
    }

}
