package tiles.misc;

import beast.base.spec.domain.Int;
import beast.base.spec.domain.UnitInterval;
import beast.base.spec.inference.parameter.VectorElement;
import beast.base.spec.type.IntScalar;
import beast.base.spec.type.RealScalar;
import beast.base.spec.type.RealVector;
import beast.base.spec.type.Simplex;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import org.phylospec.ast.AstNode;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.errors.FailedTilingAttempt;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.AstNodeTile;
import org.phylospec.tiling.tiles.Tile;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.VariableResolver;

/** A live scalar view; the vector retains sole ownership of its state, prior and operators. */
public final class VectorIndexTile extends AstNodeTile<RealScalar<?>, Expr.Index, BEASTState> {
    AstNodeTileInput<RealVector<?>, Expr.Index, BEASTState> vectorInput =
            new AstNodeTileInput<>("vector", expression -> expression.object);
    AstNodeTileInput<IntScalar<? extends Int>, Expr.Index, BEASTState> indexInput = new AstNodeTileInput<>(
            "index",
            expression -> expression.indices.getFirst(),
            Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));

    @Override
    public Set<Tile<?, BEASTState>> tryToTile(
            AstNode node,
            Map<AstNode, Set<Tile<?, BEASTState>>> inputs,
            VariableResolver variables,
            StochasticityResolver stochasticity)
            throws FailedTilingAttempt {
        if (node instanceof Expr.Index index && index.indices.size() != 1) {
            throw new FailedTilingAttempt.Rejected("A numeric vector requires exactly one index.");
        }
        var candidates = super.tryToTile(node, inputs, variables, stochasticity);
        if (stochasticity.getStochasticity(((Expr.Index) node).indices.getFirst()) != Stochasticity.CONSTANT) {
            candidates.removeIf(
                    tile -> !IndexVariableTile.class.isInstance(((VectorIndexTile) tile).indexInput.getTile()));
            if (candidates.isEmpty()) {
                throw new FailedTilingAttempt.Rejected(
                        "Numeric vector indices must be fixed constants or loop indices.");
            }
        }
        return candidates;
    }

    @Override
    public RealScalar<?> applyTile(BEASTState state, IdentityHashMap<Expr.Variable, Integer> indices) {
        RealVector<?> vector = vectorInput.apply(state, indices);
        int index = indexInput.apply(state, indices).get();
        if (index < 1 || index > vector.size()) {
            throw new TileApplicationError(
                    getRootNode(),
                    "Vector index " + index + " is out of bounds.",
                    "PhyloSpec uses 1-based indexing; choose an index from 1 to " + vector.size() + ".");
        }
        VectorElement<?> element = new VectorElement<>();
        state.setInput(element, element.vectorInput, vector);
        state.setInput(element, element.indexInput, index - 1);
        state.initBEASTObject(element);
        return element;
    }

    @Override
    public TypeToken<?> getTypeToken() {
        TypeToken<?> vectorType = vectorInput.getTypeToken();
        TypeToken<?> domain = TypeToken.firstConcreteTypeArg(vectorType);
        if (TypeToken.of(Simplex.class).isAssignableFrom(vectorType)) domain = TypeToken.of(UnitInterval.class);
        return domain == null ? super.getTypeToken() : TypeToken.parameterized(RealScalar.class, domain.getType());
    }
}
