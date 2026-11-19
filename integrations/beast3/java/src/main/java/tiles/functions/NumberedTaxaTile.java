package tiles.functions;

import beast.base.spec.domain.Int;
import beast.base.spec.type.IntScalar;
import beastconfig.BEASTState;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;
import tiles.input.TaxaData;
import tiles.input.TaxonValue;

/**
 * Creates {@code num} taxa named "1", "2", ..., "num", all sampled at age zero. This mirrors
 * LPhy's {@code taxa(names=1:num)} and lets simulations specify only the number of taxa.
 */
public class NumberedTaxaTile extends GeneratorTile<List<TaxonValue>, BEASTState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "taxa";
    }

    @Override
    public Optional<String> getNamespace() {
        return Optional.of("phylospec.functions.accessor");
    }

    GeneratorTileInput<IntScalar<? extends Int>, BEASTState> numInput =
            new GeneratorTileInput<>("num", Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));

    @Override
    public TaxaData applyTile(BEASTState state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        int num = numInput.apply(state, indexVariables).get();
        if (num <= 0) {
            throw new TileApplicationError(
                    getRootNode(),
                    "taxa requires a positive number of taxa.",
                    "Use a fixed integer num of at least 1.");
        }

        List<TaxonValue> values = new ArrayList<>(num);
        for (int i = 1; i <= num; i++) {
            values.add(new TaxonValue(String.valueOf(i), null, 0.0));
        }
        return TaxaData.fromList(state, values);
    }
}
