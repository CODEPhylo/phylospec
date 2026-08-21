package tiles.substitutionmodels;

import dr.evolution.datatype.Nucleotides;
import dr.evomodel.substmodel.FrequencyModel;
import dr.evomodel.substmodel.nucleotide.GTR;
import dr.inference.model.Parameter;
import dr.inference.model.Variable;
import dr.inference.model.VectorSliceParameter;
import java.util.IdentityHashMap;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.Simplex;
import tiling.BeastXState;
import tiling.params.BeastXSimplexParam;

/** Maps a joint six-dimensional relative-rate simplex to BEAST X GTR inputs. */
public class GTRRelativeRatesTile extends GeneratorTile<GTR, BeastXState> {

    private final GeneratorTileInput<Simplex, BeastXState> relativeRatesInput =
            new GeneratorTileInput<>("relativeRates");
    private final GeneratorTileInput<Simplex, BeastXState> baseFrequenciesInput =
            new GeneratorTileInput<>("baseFrequencies");

    @Override
    public String getPhyloSpecGeneratorName() {
        return "gtr";
    }

    @Override
    public GTR applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        Simplex relativeRates = relativeRatesInput.apply(beastState, indexVariables);
        Simplex baseFrequencies = baseFrequenciesInput.apply(beastState, indexVariables);

        if (relativeRates.size() != 6) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "GTR requires exactly six relative rates.",
                    "Provide a six-dimensional relativeRates simplex in AC, AG, AT, CG, CT, GT order.");
        }
        if (baseFrequencies.size() != 4) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "GTR requires exactly four nucleotide base frequencies: A, C, G, T.",
                    "Provide a four-dimensional baseFrequencies simplex in A, C, G, T order.");
        }

        Parameter ratesParameter = parameter(relativeRates);
        return new GTR(
                slice(ratesParameter, 0),
                slice(ratesParameter, 1),
                slice(ratesParameter, 2),
                slice(ratesParameter, 3),
                slice(ratesParameter, 4),
                slice(ratesParameter, 5),
                frequencyModel(baseFrequencies));
    }

    private static Variable<Double> slice(Parameter parameter, int index) {
        VectorSliceParameter slice = new VectorSliceParameter(null, index);
        slice.addParameter(parameter);
        return slice;
    }

    private static Parameter parameter(Simplex simplex) {
        if (simplex instanceof BeastXSimplexParam beastXSimplex) {
            return beastXSimplex.getParameter();
        }
        return new Parameter.Default(simplex.getDoubleArray());
    }

    private static FrequencyModel frequencyModel(Simplex baseFrequencies) {
        if (baseFrequencies instanceof BeastXSimplexParam beastXBaseFrequencies) {
            return new FrequencyModel(
                    Nucleotides.INSTANCE,
                    beastXBaseFrequencies.getParameter());
        }
        return new FrequencyModel(Nucleotides.INSTANCE, baseFrequencies.getDoubleArray());
    }
}
