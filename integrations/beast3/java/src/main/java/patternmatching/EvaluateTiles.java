package patternmatching;

import beast.base.inference.Distribution;
import beast.base.inference.Operator;
import beast.base.inference.StateNode;
import org.phylospec.ast.*;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;

import java.util.*;

public class EvaluateTiles implements AstVisitor<EvaluatedTile, EvaluatedTile, EvaluatedTile> {

    private final TypeResolver typeResolver;
    private final List<Tile> tiles;

    private final Set<StateNode> stateNodes;
    private final HashMap<StateNode, Distribution> distributions;
    private final Set<Operator> operators;

    private final Map<AstNode, Set<EvaluatedTile>> evaluatedTiles;
    private final VariableResolver variableResolver;

    public EvaluateTiles(List<Tile> tiles, TypeResolver typeResolver, VariableResolver variableResolver) {
        this.tiles = tiles;
        this.typeResolver = typeResolver;
        this.variableResolver = variableResolver;
        this.evaluatedTiles = new HashMap<>();
        this.stateNodes = new HashSet<>();
        this.distributions = new HashMap<>();
        this.operators = new HashSet<>();
    }

    @Override
    public EvaluatedTile visitAssignment(Stmt.Assignment stmt) {
        stmt.expression.accept(this);
        return this.visitNode(stmt);
    }

    @Override
    public EvaluatedTile visitDraw(Stmt.Draw stmt) {
        stmt.expression.accept(this);
        return this.visitNode(stmt);
    }

    @Override
    public EvaluatedTile visitLiteral(Expr.Literal expr) {
        return this.visitNode(expr);
    }

    @Override
    public EvaluatedTile visitStringTemplate(Expr.StringTemplate expr) {
        throw new UnsupportedOperationException();
    }

    @Override
    public EvaluatedTile visitVariable(Expr.Variable expr) {
        return this.visitNode(expr);
    }

    @Override
    public EvaluatedTile visitUnary(Expr.Unary expr) {
        expr.right.accept(this);
        return this.visitNode(expr);
    }

    @Override
    public EvaluatedTile visitBinary(Expr.Binary expr) {
        expr.left.accept(this);
        expr.right.accept(this);
        return this.visitNode(expr);
    }

    @Override
    public EvaluatedTile visitCall(Expr.Call expr) {
        for (Expr.Argument argument : expr.arguments) {
            argument.expression.accept(this);
        }
        return this.visitNode(expr);
    }

    @Override
    public EvaluatedTile visitAssignedArgument(Expr.AssignedArgument expr) {
        return this.visitNode(expr.expression);
    }

    @Override
    public EvaluatedTile visitDrawnArgument(Expr.DrawnArgument expr) {
        return this.visitNode(expr.expression);
    }

    @Override
    public EvaluatedTile visitGrouping(Expr.Grouping expr) {
        return this.visitNode(expr.expression);
    }

    @Override
    public EvaluatedTile visitArray(Expr.Array expr) {
        for (Expr element : expr.elements) {
            element.accept(this);
        }
        return this.visitNode(expr);
    }

    @Override
    public EvaluatedTile visitIndex(Expr.Index expr) {
        expr.object.accept(this);
        for (Expr index : expr.indices) {
            index.accept(this);
        }
        return this.visitNode(expr);
    }

    @Override
    public EvaluatedTile visitRange(Expr.Range range) {
        range.from.accept(this);
        range.to.accept(this);
        return this.visitNode(range);
    }

    private EvaluatedTile visitNode(AstNode node) {
        int lowestWeight = Integer.MAX_VALUE;
        EvaluatedTile bestEvaluatedTile = null;

        for (Tile tile : this.tiles) {
            Set<EvaluatedTile> evaluatedTiles = tile.tryToTile(node, this.evaluatedTiles, this.typeResolver, this.variableResolver);

            for (EvaluatedTile evaluatedTile : evaluatedTiles) {
                this.evaluatedTiles.computeIfAbsent(node, x -> new HashSet<>()).add(evaluatedTile);

                if (evaluatedTile.weight() < lowestWeight) {
                    lowestWeight = evaluatedTile.weight();
                    bestEvaluatedTile = evaluatedTile;
                }
            }
        }

        if (bestEvaluatedTile != null) {
            // add new state nodes, distributions, and operators

            this.stateNodes.addAll(bestEvaluatedTile.newStateNodes());
            this.distributions.putAll(bestEvaluatedTile.newDistributions());
            this.operators.addAll(bestEvaluatedTile.newOperators());
        }

        return bestEvaluatedTile;
    }

    /*
     * Unused visitors
     */

    @Override
    public EvaluatedTile visitDecoratedStmt(Stmt.Decorated stmt) {
        return stmt.statement.accept(this);
    }

    @Override
    public EvaluatedTile visitImport(Stmt.Import stmt) {
        return null;
    }

    @Override
    public EvaluatedTile visitIndexedStmt(Stmt.Indexed indexed) {
        return indexed.statement.accept(this);
    }

    @Override
    public EvaluatedTile visitObservedAsStmt(Stmt.ObservedAs observedAs) {
        return observedAs.stmt.accept(this);
    }

    @Override
    public EvaluatedTile visitObservedBetweenStmt(Stmt.ObservedBetween observedBetween) {
        return observedBetween.stmt.accept(this);
    }

    @Override
    public EvaluatedTile visitAtomicType(AstType.Atomic expr) {
        return null;
    }

    @Override
    public EvaluatedTile visitGenericType(AstType.Generic expr) {
        return null;
    }

}
