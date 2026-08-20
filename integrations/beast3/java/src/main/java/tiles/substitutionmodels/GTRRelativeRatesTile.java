package tiles.substitutionmodels;

import beast.base.spec.domain.PositiveReal;
import beast.base.spec.evolution.substitutionmodel.Frequencies;
import beast.base.spec.evolution.substitutionmodel.GTR;
import beast.base.spec.inference.parameter.VectorElement;
import beast.base.spec.type.RealScalar;
import beast.base.spec.type.Simplex;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.tiles.GeneratorTile;

/** Maps a joint six-dimensional relative-rate simplex to BEAST 2.8 GTR inputs. */
public class GTRRelativeRatesTile extends GeneratorTile<GTR, BEASTState> {

    private final GeneratorTileInput<Simplex, BEASTState> relativeRatesInput =
            new GeneratorTileInput<>("relativeRates");
    private final GeneratorTileInput<Simplex, BEASTState> baseFrequenciesInput =
            new GeneratorTileInput<>("baseFrequencies");

    @Override
    public String getPhyloSpecGeneratorName() {
        return "gtr";
    }

    @Override
    public GTR applyTile(
            BEASTState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        Simplex relativeRates = relativeRatesInput.apply(beastState, indexVariables);
        Simplex baseFrequencies = baseFrequenciesInput.apply(beastState, indexVariables);

        if (relativeRates.size() != 6) {
            throw new IllegalArgumentException("GTR requires exactly six relative rates.");
        }

        Frequencies frequencies = new Frequencies();
        beastState.setInput(frequencies, frequencies.frequenciesInput, baseFrequencies);

        GTR gtr = new GTR();
        beastState.setInput(gtr, gtr.rateACInput, rate(relativeRates, 0));
        beastState.setInput(gtr, gtr.rateAGInput, rate(relativeRates, 1));
        beastState.setInput(gtr, gtr.rateATInput, rate(relativeRates, 2));
        beastState.setInput(gtr, gtr.rateCGInput, rate(relativeRates, 3));
        beastState.setInput(gtr, gtr.rateCTInput, rate(relativeRates, 4));
        beastState.setInput(gtr, gtr.rateGTInput, rate(relativeRates, 5));
        beastState.setInput(gtr, gtr.frequenciesInput, frequencies);
        return gtr;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static RealScalar<PositiveReal> rate(Simplex relativeRates, int index) {
        return (RealScalar) new VectorElement(relativeRates, index);
    }
}
