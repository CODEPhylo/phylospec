package tiling;

import beastconfig.BEASTState;
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
    private final List<Tile<?>> operatorTiles;

    private List<Stmt> queryStatements;
    private List<Tile<?>> bestTiles;

    // memoisation caches: all candidates per node, and the single best candidate per node
    private final IdentityHashMap<AstNode, Set<Tile<?>>> evaluatedTiles;
    private final IdentityHashMap<AstNode, Tile<?>> bestEvaluatedTiles;
    private final List<Tile<?>> matchedOperatorTiles;
    private final VariableResolver variableResolver;
    private final StochasticityResolver stochasticityResolver;

    // statements that have already been claimed by a tile covering multiple statements
    private final Set<Stmt> consumedStatements;

    // best failure per node (RejectedBoundary > Rejected > RejectedCascade, Irrelevant ignored)
    private final IdentityHashMap<AstNode, FailedTilingAttempt> bestFailures;

    // all nodes that failed to tile, recorded in visit order (children before parents)
    private final List<AstNode> failedNodesInVisitOrder;

    public EvaluateTiles(List<Tile<?>> tiles, List<Tile<?>> operatorTiles, VariableResolver variableResolver, StochasticityResolver stochasticityResolver) {
        this.tiles = tiles;
        this.operatorTiles = operatorTiles;
        this.variableResolver = variableResolver;
        this.stochasticityResolver = stochasticityResolver;
        this.evaluatedTiles = new IdentityHashMap<>();
        this.bestEvaluatedTiles = new IdentityHashMap<>();
        this.consumedStatements = new HashSet<>();
        this.bestFailures = new IdentityHashMap<>();
        this.failedNodesInVisitOrder = new ArrayList<>();
        this.matchedOperatorTiles = new ArrayList<>();
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
        this.queryStatements = statements;

        // we start with the last statements and go backwards

        List<Tile<?>> bestTiles = new ArrayList<>();

        for (int i = statements.size() - 1; i >= 0; i--) {
            Stmt stmt = statements.get(i);

            if (this.consumedStatements.contains(stmt)) continue;

            // snapshot the failure list before visiting so we can scope the search to this statement
            int failedCountBefore = this.failedNodesInVisitOrder.size();
            Tile<?> bestTile = stmt.accept(this);

            if (bestTile == null) {
                this.throwDeepestFailure(failedCountBefore);
            }

            bestTiles.addFirst(bestTile);
        }

        this.bestTiles = bestTiles;
        return bestTiles;
    }

    /**
     * Computes the best tiling and applies each tile in order,
     * building up a {@link BEASTState} that represents the fully-generated BEAST 2.8 model.
     *
     * @return the accumulated BEAST 2.8 model state after all tiles have been applied
     */
    public BEASTState applyBestTiling(BEASTState beastState) {
        for (Tile<?> bestTiling : this.bestTiles) {
            bestTiling.apply(beastState);
        }

        // perform operator tiling

        for (Tile<?> operatorTile : this.matchedOperatorTiles) {
            operatorTile.applyTile(beastState);
        }

        return beastState;
    }

    /* visitor helpers */

    /**
     * Finds the best tile for {@code node} by asking every registered tile to attempt a match,
     * then returning the one with the lowest weight. Results are memoised so the same node is
     * never evaluated twice.
     * When no tile matches, the highest-priority failure is stored in {@link #bestFailures} for
     * later root-cause analysis.
     */
    private Tile<?> visitNode(AstNode node) {
        if (this.bestEvaluatedTiles.containsKey(node)) {
            return this.bestEvaluatedTiles.get(node);
        }

        int lowestWeight = Integer.MAX_VALUE;
        Tile<?> bestEvaluatedTile = null;

        this.evaluatedTiles.putIfAbsent(node, new HashSet<>());

        // we track the best failure seen across all tiles for this node

        FailedTilingAttempt.RejectedBoundary bestBoundary = null;
        FailedTilingAttempt.Rejected bestRejected = null;
        FailedTilingAttempt.RejectedCascade bestCascade = null;

        for (Tile<?> tile : this.tiles) {
            Set<Tile<?>> evaluatedTiles;
            try {
                evaluatedTiles = tile.tryToTile(
                        node, this.evaluatedTiles, this.variableResolver, this.stochasticityResolver
                );
            } catch (FailedTilingAttempt.Irrelevant e) {
                continue;
            } catch (FailedTilingAttempt e) {
                if (e instanceof FailedTilingAttempt.RejectedBoundary rb && bestBoundary == null) bestBoundary = rb;
                else if (e instanceof FailedTilingAttempt.Rejected r && bestRejected == null) bestRejected = r;
                else if (e instanceof FailedTilingAttempt.RejectedCascade rc && bestCascade == null) bestCascade = rc;
                continue;
            }

            this.evaluatedTiles.get(node).addAll(evaluatedTiles);

            for (Tile<?> evaluatedTile : evaluatedTiles) {
                if (evaluatedTile.getWeight() < lowestWeight) {
                    lowestWeight = evaluatedTile.getWeight();
                    bestEvaluatedTile = evaluatedTile;
                }
            }
        }

        if (bestEvaluatedTile == null) {
            // record in visit order so findDeepestFailure can scan children-first
            this.failedNodesInVisitOrder.add(node);

            // store highest-priority failure for the scan to inspect
            if (bestBoundary != null) this.bestFailures.put(node, bestBoundary);
            else if (bestRejected != null) this.bestFailures.put(node, bestRejected);
            else if (bestCascade != null) this.bestFailures.put(node, bestCascade);
        }

        this.bestEvaluatedTiles.put(node, bestEvaluatedTile);

        // match operator tiles

        for (Tile<?> operatorTile : this.operatorTiles) {
            try {
                this.matchedOperatorTiles.addAll(
                    operatorTile.tryToTile(
                            node, this.evaluatedTiles, this.variableResolver, this.stochasticityResolver
                    )
                );
            } catch (FailedTilingAttempt ignored) {
                continue;
            }
        }

        return bestEvaluatedTile;
    }

    /**
     * Scans failed nodes recorded since {@code fromIndex} in visit order (children before parents)
     * and returns the first one that carries a {@code Rejected} or {@code RejectedBoundary}.
     * Because children are visited before parents, the first match is the deepest actionable
     * failure — everything above it failed only as a cascade consequence.
     */
    private FailedTilingAttempt throwDeepestFailure(int fromIndex) {
        for (int i = fromIndex; i < this.failedNodesInVisitOrder.size(); i++) {
            AstNode failedNode = this.failedNodesInVisitOrder.get(i);
            FailedTilingAttempt failure = this.bestFailures.get(failedNode);
            if (i == this.failedNodesInVisitOrder.size() - 1 || failure instanceof FailedTilingAttempt.Rejected || failure instanceof FailedTilingAttempt.RejectedBoundary) {
                String reason = switch (failure) {
                    case FailedTilingAttempt.Rejected r -> r.getReason();
                    case FailedTilingAttempt.RejectedBoundary rb -> rb.getReason();
                    case null, default -> "BEAST 2.8 does not support this operation.";
                };
                throw new TilingError(failedNode, "Unsupported operation.", reason);
            }
        }
        return null;
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
            argument.accept(this);
        }
        return this.visitNode(expr);
    }

    @Override
    public Tile<?> visitAssignedArgument(Expr.AssignedArgument expr) {
        expr.expression.accept(this);
        return this.visitNode(expr);
    }

    @Override
    public Tile<?> visitDrawnArgument(Expr.DrawnArgument expr) {
        expr.expression.accept(this);
        return this.visitNode(expr);
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
