package tiles.substitutionmodels;

import dr.evolution.datatype.Codons;
import dr.evomodel.substmodel.FrequencyModel;
import dr.evomodel.substmodel.codon.GY94CodonModel;
import dr.inference.model.Parameter;
import org.phylospec.ast.Expr;
import org.phylospec.domain.PositiveReal;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.RealScalar;
import org.phylospec.types.Simplex;
import tiling.BeastXRealScalarParam;
import tiling.BeastXState;

import java.util.IdentityHashMap;
import java.util.List;

public class GY94Tile extends GeneratorTile<GY94CodonModel, BeastXState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "gy94";
    }

    GeneratorTileInput<RealScalar<PositiveReal>, BeastXState> kappaInput =
            new GeneratorTileInput<>("kappa");

    GeneratorTileInput<RealScalar<PositiveReal>, BeastXState> omegaInput =
            new GeneratorTileInput<>("omega");

    GeneratorTileInput<Simplex, BeastXState> baseFrequenciesInput =
            new GeneratorTileInput<>("baseFrequencies");

    @Override
    public GY94CodonModel applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        RealScalar<PositiveReal> kappa =
                this.kappaInput.apply(beastState, indexVariables);

        RealScalar<PositiveReal> omega =
                this.omegaInput.apply(beastState, indexVariables);

        Simplex baseFrequencies =
                this.baseFrequenciesInput.apply(beastState, indexVariables);

        Codons codons = Codons.UNIVERSAL;
        int expectedFrequencyCount = codons.getStateCount();

        if (baseFrequencies.size() != expectedFrequencyCount) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "GY94 requires one stationary frequency for each non-stop codon.",
                    "Use a Simplex with " + expectedFrequencyCount + " codon frequencies.",
                    List.of("gy94(kappa=2.0, omega=0.5, baseFrequencies=repeat(0.01639344262295082, num=61))")
            );
        }

        if (!baseFrequencies.isValid()) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "GY94 baseFrequencies must be a valid simplex.",
                    "All values must be in [0, 1] and sum to 1.",
                    List.of("gy94(kappa=2.0, omega=0.5, baseFrequencies=repeat(0.01639344262295082, num=61))")
            );
        }

        FrequencyModel frequencyModel =
                new FrequencyModel(codons, baseFrequencies.getDoubleArray());

        return new GY94CodonModel(
                codons,
                toParameter(kappa),
                toParameter(omega),
                frequencyModel
        );
    }

    private static Parameter toParameter(RealScalar<PositiveReal> scalar) {
        if (scalar instanceof BeastXRealScalarParam<?> beastXScalar) {
            return beastXScalar.getParameter();
        }

        return new Parameter.Default(scalar.get());
    }
}