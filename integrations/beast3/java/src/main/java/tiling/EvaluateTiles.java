package tiling;

import beastconfig.BEASTState;
import org.phylospec.Utils;
import org.phylospec.ast.*;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.VariableResolver;

import java.util.*;

/**
 * Visits an AST and determines the best tiling for each statement by selecting the lowest-weight
 * tile that matches. Statements consumed by a multi-statement tile are skipped at the top level
 * so they are not tiled a second time.
 */
public class EvaluateTiles implements AstVisitor<Void, Void, Void> {

    private final List<Tile<?>> tiles;
    private final List<Tile<?>> operatorTiles;

    private List<Tile<?>> bestTiles;

    private final IdentityHashMap<AstNode, Set<Tile<?>>> evaluatedTiles;
    private final List<Tile<?>> matchedOperatorTiles;

    private final VariableResolver variableResolver;
    private final StochasticityResolver stochasticityResolver;

    private Set<Expr.Variable> currentIndexVariables;

    // statements that have already been claimed by a tile covering multiple statements
    private final Set<Stmt> consumedStatements;

    // all non-Irrelevant failures per failed node, used to build the cascade DAG for root-cause analysis
    private final IdentityHashMap<AstNode, List<FailedTilingAttempt>> allFailures;

    // memoised cascade depths, computed lazily during error reporting
    private final IdentityHashMap<AstNode, Integer> depthCache;

    // sentinel depth for nodes that tiled successfully (they act as dead-ends in the cascade DAG)
    private static final int DEPTH_SUCCEEDED = Integer.MIN_VALUE;

    public EvaluateTiles(List<Tile<?>> tiles, List<Tile<?>> operatorTiles, VariableResolver variableResolver, StochasticityResolver stochasticityResolver) {
        this.tiles = tiles;
        this.operatorTiles = operatorTiles;
        this.variableResolver = variableResolver;
        this.stochasticityResolver = stochasticityResolver;
        this.currentIndexVariables = Collections.newSetFromMap(new IdentityHashMap<>());
        this.evaluatedTiles = new IdentityHashMap<>();
        this.consumedStatements = Collections.newSetFromMap(new IdentityHashMap<>());
        this.allFailures = new IdentityHashMap<>();
        this.depthCache = new IdentityHashMap<>();
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
        // we first find all matching tiles for every AstNode
        // we start with the last statements and go backwards

        List<List<Tile<?>>> possibleTiles = new ArrayList<>();

        for (int i = statements.size() - 1; i >= 0; i--) {
            Stmt stmt = statements.get(i);

            if (this.consumedStatements.contains(stmt)) {
                // this statement has already been consumed by a subsequent statement
                // this happens if a subsequent statement refers to the variable declared here
                continue;
            }

            // find all tilings for this statement

            stmt.accept(this);
            Set<Tile<?>> candidateTiles = this.evaluatedTiles.get(stmt);

            if (candidateTiles.isEmpty()) {
                // no valid tiling found
                this.throwDeepestFailure(stmt);
            }

            // remove all inconsistent tilings (tilings where the same AstNode maps is tiled with different tiles)

            candidateTiles.removeIf(x -> x.isInconsistent(new IdentityHashMap<>()));

            // sort them by weight (least first)

            List<Tile<?>> orderedCandidateTiles = new ArrayList<>(candidateTiles);
            orderedCandidateTiles.sort(Comparator.comparingInt(Tile::getWeight));

            possibleTiles.add(orderedCandidateTiles);
        }

        List<Tile<?>> bestTiles = new ArrayList<>();
        boolean[] foundBestTile = new boolean[] {false};

        Utils.visitOrderedCombinations(possibleTiles, tiles -> {
            if (foundBestTile[0]) {
                // we've already found the (greedy) best one
                return;
            }

            // check for consistency across the statement tiles

            IdentityHashMap<AstNode, Tile<?>> assignments = new IdentityHashMap<>();

            for (Tile<?> tile : tiles) {
                if (tile.isInconsistent(assignments)) return;
            }

            // we found a consistent tiling
            // we sorted the candidates by weight earlier, but there could still be a lower-weight consistent tiling
            // however, we just greedily pick the first consistent one because otherwise this is exponential and
            // might blow up quickly

            bestTiles.clear();
            bestTiles.addAll(tiles);
            foundBestTile[0] = true;
        });

        if (!foundBestTile[0]) {
            // no consistent tiling found
            // this is very rare
            throw new TileApplicationError(
                    "Unsupported operation.", "Your model is not supported by BEAST 2.8."
            );
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
            bestTiling.apply(beastState, new IdentityHashMap<>());
        }

        return beastState;
    }

    /* visitor helpers */

    /**
     * Finds the best tile for {@code node} by asking every registered tile to attempt a match,
     * then returning the one with the lowest weight. Results are memoised so the same node is
     * never evaluated twice.
     * When no tile matches, all non-{@link FailedTilingAttempt.Irrelevant} failures are stored
     * in {@link #allFailures} so the cascade DAG can be traversed during root-cause analysis.
     */
    private Void visitNode(AstNode node) {
        if (this.evaluatedTiles.containsKey(node)) {
            // we have already processed that node
            return null;
        }

        this.evaluatedTiles.putIfAbsent(node, new HashSet<>());
        List<FailedTilingAttempt> failures = new ArrayList<>();

        // we go through all tiles and try to apply them

        for (Tile<?> tile : this.tiles) {
            Set<Tile<?>> evaluatedTiles;
            try {
                evaluatedTiles = tile.tryToTile(
                        node, this.evaluatedTiles, this.variableResolver, this.stochasticityResolver
                );
            } catch (FailedTilingAttempt.Irrelevant e) {
                continue;
            } catch (FailedTilingAttempt e) {
                // this tile is relevant but couldn't be applied
                failures.add(e);
                continue;
            }

            // sanity check: check that all tiles have a root associated with them

            for (Tile<?> t : evaluatedTiles) {
                if (t.getRootNode() == null) t.setRootNode(node);
            }

            // add the index variables of the current scope

            for (Tile<?> t : evaluatedTiles) {
                if (t.getIndexVariables() == null) t.setIndexVariables(this.currentIndexVariables);
            }

            this.evaluatedTiles.get(node).addAll(evaluatedTiles);
        }

        if (this.evaluatedTiles.get(node).isEmpty()) {
            // none of the tiles fits
            // we store the failures for error recovery later
            this.allFailures.put(node, failures);
        }

        return null;
    }

    /* error handling */

    /**
     * Finds the deepest leaf failure reachable from {@code root} via the cascade DAG and throws
     * it as a {@link TileApplicationError}. When multiple leaves are tied at the same depth (independent
     * failures), the first in encounter order is reported.
     */
    private void throwDeepestFailure(AstNode root) {
        for (AstNode leaf : this.findErrorLeaves(root)) {
            throw new TileApplicationError(leaf, "Unsupported operation.", this.getBestReason(this.allFailures.get(leaf)));
        }
        // fallback: root failed but every tile threw Irrelevant (no tile targets this node type)
        throw new TileApplicationError(root, "Unsupported operation.", "BEAST 2.8 does not support this operation.");
    }

    /**
     * Traverses the failure cascade DAG from {@code node}, following only the deepest
     * {@link FailedTilingAttempt.RejectedCascade} chains at each step, and returns the set of
     * leaf nodes whose failure is a {@link FailedTilingAttempt.Rejected} or
     * {@link FailedTilingAttempt.RejectedBoundary}.
     * Multiple leaves are returned when two cascade paths are tied at maximum depth, indicating
     * genuinely independent failures.
     */
    private Set<AstNode> findErrorLeaves(AstNode node) {
        List<FailedTilingAttempt> failures = this.allFailures.get(node);

        if (failures == null && node instanceof Expr.Variable nodeVar) {
            // we check if there are failures when tiling the definition statement
            Stmt definition = this.variableResolver.resolveVariable(nodeVar);
            node = definition;
            failures = this.allFailures.get(node);
        }

        if (failures == null) {
            // node succeeded — dead end, contributes no leaf
            return Set.of();
        }

        // find the max depth among cascade targets that are themselves failed nodes

        int maxChildDepth = DEPTH_SUCCEEDED;
        for (FailedTilingAttempt f : failures) {
            if (f instanceof FailedTilingAttempt.RejectedCascade rc) {
                int d = this.getFailureDepth(rc.getOtherNode());
                if (d > maxChildDepth) maxChildDepth = d;
            }
        }

        if (maxChildDepth == DEPTH_SUCCEEDED) {
            // no cascades lead to failed children — this node is the leaf
            return Set.of(node);
        }

        // follow all cascades tied at the maximum depth

        Set<AstNode> leaves = new LinkedHashSet<>();
        for (FailedTilingAttempt f : failures) {
            if (f instanceof FailedTilingAttempt.RejectedCascade rc && this.getFailureDepth(rc.getOtherNode()) == maxChildDepth) {
                leaves.addAll(this.findErrorLeaves(rc.getOtherNode()));
            }
        }
        return leaves;
    }

    /**
     * Returns the cascade depth of {@code node}: the length of the longest chain of
     * {@link FailedTilingAttempt.RejectedCascade} pointers reachable from it before reaching a
     * leaf failure. Returns {@link #DEPTH_SUCCEEDED} for nodes that are not in
     * {@link #allFailures} (they tiled successfully and act as dead-ends).
     * Results are memoised in {@link #depthCache}.
     */
    private int getFailureDepth(AstNode node) {
        Integer cached = this.depthCache.get(node);
        if (cached != null) return cached;

        List<FailedTilingAttempt> failures = this.allFailures.get(node);

        if (failures == null && node instanceof Expr.Variable nodeVar) {
            // we check if there are failures when tiling the definition statement
            Stmt definition = this.variableResolver.resolveVariable(nodeVar);
            failures = this.allFailures.get(definition);
        }


        if (failures == null) {
            // node succeeded — cascade into it is a dead end
            this.depthCache.put(node, DEPTH_SUCCEEDED);
            return DEPTH_SUCCEEDED;
        }

        // we recursively find the failure with the highest cascade depth

        int maxChildDepth = DEPTH_SUCCEEDED;
        for (FailedTilingAttempt f : failures) {
            if (f instanceof FailedTilingAttempt.RejectedCascade rc) {
                int childDepth = this.getFailureDepth(rc.getOtherNode());
                if (childDepth > maxChildDepth) maxChildDepth = childDepth;
            }
        }

        // if no cascades reach a failed child, this node is a leaf at depth 0
        int d = (maxChildDepth == DEPTH_SUCCEEDED) ? 0 : maxChildDepth + 1;

        this.depthCache.put(node, d);
        return d;
    }

    /**
     * Picks the most informative reason string from the failure list of a leaf node.
     * {@link FailedTilingAttempt.RejectedBoundary} is preferred over
     * {@link FailedTilingAttempt.Rejected} because it also carries the specific sub-node where
     * the type incompatibility occurred.
     */
    private String getBestReason(List<FailedTilingAttempt> failures) {
        FailedTilingAttempt.RejectedBoundary boundary = null;
        FailedTilingAttempt.Rejected rejected = null;
        for (FailedTilingAttempt f : failures) {
            if (f instanceof FailedTilingAttempt.RejectedBoundary rb && boundary == null) boundary = rb;
            else if (f instanceof FailedTilingAttempt.Rejected r && rejected == null) rejected = r;
        }
        if (boundary != null) return boundary.getReason();
        if (rejected != null) return rejected.getReason();
        return "BEAST 2.8 does not support this operation.";
    }

    /* visitor methods */

    @Override
    public Void visitAssignment(Stmt.Assignment stmt) {
        stmt.expression.accept(this);
        return this.visitNode(stmt);
    }

    @Override
    public Void visitDraw(Stmt.Draw stmt) {
        stmt.expression.accept(this);
        return this.visitNode(stmt);
    }

    @Override
    public Void visitDecoratedStmt(Stmt.Decorated stmt) {
        stmt.decorator.accept(this);
        stmt.statement.accept(this);
        return this.visitNode(stmt);
    }

    @Override
    public Void visitImport(Stmt.Import stmt) {
        return this.visitNode(stmt);
    }

    @Override
    public Void visitIndexedStmt(Stmt.Indexed indexed) {
        if (!this.currentIndexVariables.isEmpty()) {
            throw new RuntimeException("Non-empty index variables found. This should not happen.");
        }
        this.currentIndexVariables.addAll(indexed.indices);
        indexed.statement.accept(this);
        this.currentIndexVariables = Collections.newSetFromMap(new IdentityHashMap<>());

        for (Expr.Variable index : indexed.indices) {
            index.accept(this);
        }
        for (Expr range : indexed.ranges) {
            range.accept(this);
        }

        return this.visitNode(indexed);
    }

    @Override
    public Void visitObservedAsStmt(Stmt.ObservedAs observedAs) {
        observedAs.stmt.accept(this);
        observedAs.observedAs.accept(this);
        return this.visitNode(observedAs);
    }

    @Override
    public Void visitObservedBetweenStmt(Stmt.ObservedBetween observedBetween) {
        observedBetween.stmt.accept(this);
        observedBetween.observedFrom.accept(this);
        observedBetween.observedTo.accept(this);
        return this.visitNode(observedBetween);
    }

    @Override
    public Void visitLiteral(Expr.Literal expr) {
        return this.visitNode(expr);
    }

    @Override
    public Void visitStringTemplate(Expr.StringTemplate expr) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Void visitVariable(Expr.Variable expr) {
        // we try to jump to the definition statement of this variable to allow a tiling to cover multiple statements

        Stmt variableDefinitionStmt = this.variableResolver.resolveVariable(expr);

        if (variableDefinitionStmt != null) {
            // reset the index variable scope because we enter another statement
            Set<Expr.Variable> oldIndexVariables = this.currentIndexVariables;
            this.currentIndexVariables = Collections.newSetFromMap(new IdentityHashMap<>());

            variableDefinitionStmt.accept(this);

            this.currentIndexVariables = oldIndexVariables;
            this.consumedStatements.add(variableDefinitionStmt);

            // we re-use the found tiles from the definition statement for the variable

            this.evaluatedTiles.put(
                    expr, this.evaluatedTiles.get(variableDefinitionStmt)
            );

            return null;
        } else {
            // this is a scoped index variable, we visit it normally
            return this.visitNode(expr);
        }
    }

    @Override
    public Void visitUnary(Expr.Unary expr) {
        expr.right.accept(this);
        return this.visitNode(expr);
    }

    @Override
    public Void visitBinary(Expr.Binary expr) {
        expr.left.accept(this);
        expr.right.accept(this);
        return this.visitNode(expr);
    }

    @Override
    public Void visitCall(Expr.Call expr) {
        for (Expr.Argument argument : expr.arguments) {
            argument.accept(this);
        }
        return this.visitNode(expr);
    }

    @Override
    public Void visitAssignedArgument(Expr.AssignedArgument expr) {
        expr.expression.accept(this);
        return this.visitNode(expr);
    }

    @Override
    public Void visitDrawnArgument(Expr.DrawnArgument expr) {
        expr.expression.accept(this);
        return this.visitNode(expr);
    }

    @Override
    public Void visitGrouping(Expr.Grouping expr) {
        return this.visitNode(expr.expression);
    }

    @Override
    public Void visitArray(Expr.Array expr) {
        for (Expr element : expr.elements) {
            element.accept(this);
        }
        return this.visitNode(expr);
    }

    @Override
    public Void visitIndex(Expr.Index expr) {
        expr.object.accept(this);
        for (Expr index : expr.indices) {
            index.accept(this);
        }
        return this.visitNode(expr);
    }

    @Override
    public Void visitRange(Expr.Range range) {
        range.from.accept(this);
        range.to.accept(this);
        return this.visitNode(range);
    }

    @Override
    public Void visitAtomicType(AstType.Atomic expr) {
        return this.visitNode(expr);
    }

    @Override
    public Void visitGenericType(AstType.Generic expr) {
        for (AstType typeParameter : expr.typeParameters) {
            typeParameter.accept(this);
        }
        return this.visitNode(expr);
    }

}
