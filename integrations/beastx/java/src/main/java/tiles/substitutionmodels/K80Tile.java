package tiles.substitutionmodels;

import dr.evolution.datatype.Nucleotides;
import dr.evomodel.substmodel.FrequencyModel;
import dr.evomodel.substmodel.nucleotide.HKY;
import org.phylospec.ast.Expr;
import org.phylospec.domain.PositiveReal;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.RealScalar;
import tiling.BeastXRealScalarParam;
import tiling.BeastXState;

import java.util.IdentityHashMap;

public class K80Tile extends GeneratorTile<HKY, BeastXState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "k80";
    }

    GeneratorTileInput<RealScalar<PositiveReal>, BeastXState> kappaInput =
            new GeneratorTileInput<>("kappa");

    @Override
    public HKY applyTile(BeastXState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        RealScalar<PositiveReal> kappaScalar =
                this.kappaInput.apply(beastState, indexVariables);

        FrequencyModel frequencies = new FrequencyModel(
                Nucleotides.INSTANCE,
                new double[]{0.25, 0.25, 0.25, 0.25}
        );

        if (kappaScalar instanceof BeastXRealScalarParam<?> beastXKappa) {
            return new HKY(beastXKappa.getParameter(), frequencies);
        }

        return new HKY(kappaScalar.get(), frequencies);
    }
}
