package tiles.substitutionmodels;

import dr.evolution.datatype.Nucleotides;
import dr.evomodel.substmodel.FrequencyModel;
import dr.evomodel.substmodel.nucleotide.HKY;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.Simplex;
import tiling.BeastXState;

import java.util.IdentityHashMap;

public class F81Tile extends GeneratorTile<HKY, BeastXState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "f81";
    }

    GeneratorTileInput<Simplex, BeastXState> baseFrequenciesInput =
            new GeneratorTileInput<>("baseFrequencies");

    @Override
    public HKY applyTile(BeastXState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        Simplex baseFrequencies = this.baseFrequenciesInput.apply(beastState, indexVariables);

        FrequencyModel frequencies = new FrequencyModel(
                Nucleotides.INSTANCE,
                baseFrequencies.getDoubleArray()
        );

        return new HKY(1.0, frequencies);
    }
}