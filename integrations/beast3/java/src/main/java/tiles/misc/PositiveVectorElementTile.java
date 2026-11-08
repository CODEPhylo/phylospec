package tiles.misc;

import beast.base.spec.domain.PositiveReal;
import beast.base.spec.inference.parameter.VectorElement;
import beast.base.spec.type.IntScalar;
import beast.base.spec.type.RealScalar;
import beast.base.spec.type.RealVector;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.Set;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.AstNodeTile;
import org.phylospec.typeresolver.Stochasticity;

/** A one-based, live view of an element of a positive BEAST vector. */
public final class PositiveVectorElementTile extends AstNodeTile<RealScalar<PositiveReal>, Expr.Index, BEASTState> {
    AstNodeTileInput<RealVector<PositiveReal>, Expr.Index, BEASTState> vectorInput =
            new AstNodeTileInput<>("vector", expr -> expr.object);
    AstNodeTileInput<IntScalar<?>, Expr.Index, BEASTState> indexInput = new AstNodeTileInput<>(
            "index", expr -> expr.indices.getFirst(), Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));

    @Override
    public RealScalar<PositiveReal> applyTile(BEASTState state, IdentityHashMap<Expr.Variable, Integer> indices) {
        if (getRootNode().indices.size() != 1) {
            throw new TileApplicationError(getRootNode(), "A vector takes one index.", "Use r[i].");
        }
        var vector = vectorInput.apply(state, indices);
        int index = indexInput.apply(state, indices).get();
        if (index < 1 || index > vector.size()) {
            throw new TileApplicationError(
                    getRootNode(),
                    "Vector index " + index + " is out of bounds.",
                    "PhyloSpec uses 1-based indexing. Use an index between 1 and " + vector.size() + ".");
        }
        return new VectorElement<>(vector, index - 1);
    }
}
