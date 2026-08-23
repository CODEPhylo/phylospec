package tiles.substitutionmodels;

import dr.evolution.datatype.Nucleotides;
import dr.evomodel.substmodel.FrequencyModel;
import dr.evomodel.substmodel.nucleotide.HKY;
import org.phylospec.ast.Expr;
import org.phylospec.domain.PositiveReal;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.RealScalar;
import org.phylospec.types.Simplex;
import tiling.BeastXState;
import tiling.operators.ParameterRole;
import tiling.params.BeastXRealScalarParam;
import tiling.params.BeastXSimplexParam;

import java.util.IdentityHashMap;

public class HKYTile extends GeneratorTile<HKY, BeastXState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "hky";
    }

    GeneratorTileInput<RealScalar<PositiveReal>, BeastXState> kappaInput =
            new GeneratorTileInput<>("kappa");

    GeneratorTileInput<Simplex, BeastXState> baseFrequenciesInput =
            new GeneratorTileInput<>("baseFrequencies");

    @Override
    public HKY applyTile(BeastXState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        RealScalar<PositiveReal> kappaScalar =
                this.kappaInput.apply(beastState, indexVariables);

        Simplex baseFrequencies =
                this.baseFrequenciesInput.apply(beastState, indexVariables);

        if (baseFrequencies.size() != 4) {
            throw new IllegalArgumentException(
                    "HKY requires exactly four nucleotide base frequencies: A, C, G, T."
            );
        }

        if (!baseFrequencies.isValid()) {
            throw new IllegalArgumentException(
                    "HKY baseFrequencies must be a valid simplex: all values must be in [0, 1] and sum to 1."
            );
        }

        FrequencyModel frequencies =
                frequencyModel(baseFrequencies);

        if (baseFrequencies instanceof BeastXSimplexParam beastXBaseFrequencies) {
            beastState.addParameterRole(
                    beastXBaseFrequencies.getParameter(),
                    ParameterRole.SUBSTITUTION_SIMPLEX);
        }

        if (kappaScalar instanceof BeastXRealScalarParam<?> beastXKappa) {
            beastState.addParameterRole(
                    beastXKappa.getParameter(),
                    ParameterRole.SUBSTITUTION_SCALE);
            return new HKY(beastXKappa.getParameter(), frequencies);
        }

        return new HKY(kappaScalar.get(), frequencies);
    }

    private static FrequencyModel frequencyModel(Simplex baseFrequencies) {
        if (baseFrequencies instanceof BeastXSimplexParam beastXBaseFrequencies) {
            return new FrequencyModel(
                    Nucleotides.INSTANCE,
                    beastXBaseFrequencies.getParameter()
            );
        }

        return new FrequencyModel(
                Nucleotides.INSTANCE,
                baseFrequencies.getDoubleArray()
        );
    }
}
