package tiles.substitutionmodels;

import dr.evolution.datatype.Nucleotides;
import dr.evomodel.substmodel.FrequencyModel;
import dr.evomodel.substmodel.nucleotide.HKY;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.Simplex;
import tiling.BeastXState;
import tiling.params.BeastXSimplexParam;

import java.util.IdentityHashMap;

public class F81Tile extends GeneratorTile<HKY, BeastXState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "f81";
    }

    GeneratorTileInput<Simplex, BeastXState> baseFrequenciesInput =
            new GeneratorTileInput<>("baseFrequencies");

    public F81Tile() {
        this.baseFrequenciesInput.requireSize(
                4,
                "F81 requires exactly four nucleotide base frequencies: A, C, G, T."
        );
    }

    @Override
    public HKY applyTile(BeastXState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        Simplex baseFrequencies = this.baseFrequenciesInput.apply(beastState, indexVariables);

        if (baseFrequencies.size() != 4) {
            throw new IllegalArgumentException(
                    "F81 requires exactly four nucleotide base frequencies: A, C, G, T."
            );
        }

        if (!baseFrequencies.isValid()) {
            throw new IllegalArgumentException(
                    "F81 baseFrequencies must be a valid simplex: all values must be in [0, 1] and sum to 1."
            );
        }

        FrequencyModel frequencies =
                frequencyModel(baseFrequencies);

        return new HKY(1.0, frequencies);
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