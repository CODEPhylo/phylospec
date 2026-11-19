package tiles.functions;

import dr.evolution.util.Taxa;
import dr.evolution.util.Taxon;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;
import tiling.BeastXState;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Creates {@code num} taxa named "1", "2", ..., "num", all sampled at age zero. This mirrors
 * LPhy's {@code taxa(names=1:num)} and lets simulations specify only the number of taxa.
 *
 * <p>Like taxa read from an alignment without tip dates, the created taxa carry no date, so the
 * tree model stays undated.
 */
public class NumberedTaxaTile extends GeneratorTile<Taxa, BeastXState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "taxa";
    }

    GeneratorTileInput<Integer, BeastXState> numInput =
            new GeneratorTileInput<>(
                    "num",
                    Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
            );

    @Override
    public Taxa applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        int num =
                this.numInput.apply(beastState, indexVariables);

        if (num <= 0) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "taxa requires a positive number of taxa.",
                    "Use a fixed integer num of at least 1.",
                    List.of("Taxa taxa = taxa(num=10)")
            );
        }

        Taxa taxa =
                new Taxa();

        for (int i = 1; i <= num; i++) {
            taxa.addTaxon(new Taxon(String.valueOf(i)));
        }

        return taxa;
    }
}
