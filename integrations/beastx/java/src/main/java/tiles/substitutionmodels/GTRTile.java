package tiles.substitutionmodels;

import dr.evolution.datatype.Nucleotides;
import dr.evomodel.substmodel.FrequencyModel;
import dr.evomodel.substmodel.nucleotide.GTR;
import dr.inference.model.Parameter;
import org.phylospec.ast.Expr;
import org.phylospec.domain.PositiveReal;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.RealScalar;
import org.phylospec.types.Simplex;
import tiling.BeastXState;

import java.util.IdentityHashMap;

public class GTRTile extends GeneratorTile<GTR, BeastXState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "gtr";
    }

    GeneratorTileInput<RealScalar<PositiveReal>, BeastXState> rateACInput = new GeneratorTileInput<>("rateAC");
    GeneratorTileInput<RealScalar<PositiveReal>, BeastXState> rateAGInput = new GeneratorTileInput<>("rateAG");
    GeneratorTileInput<RealScalar<PositiveReal>, BeastXState> rateATInput = new GeneratorTileInput<>("rateAT");
    GeneratorTileInput<RealScalar<PositiveReal>, BeastXState> rateCGInput = new GeneratorTileInput<>("rateCG");
    GeneratorTileInput<RealScalar<PositiveReal>, BeastXState> rateCTInput = new GeneratorTileInput<>("rateCT");
    GeneratorTileInput<RealScalar<PositiveReal>, BeastXState> rateGTInput = new GeneratorTileInput<>("rateGT");
    GeneratorTileInput<Simplex, BeastXState> baseFrequenciesInput = new GeneratorTileInput<>("baseFrequencies");

    @Override
    public GTR applyTile(BeastXState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        RealScalar<PositiveReal> rateAC = this.rateACInput.apply(beastState, indexVariables);
        RealScalar<PositiveReal> rateAG = this.rateAGInput.apply(beastState, indexVariables);
        RealScalar<PositiveReal> rateAT = this.rateATInput.apply(beastState, indexVariables);
        RealScalar<PositiveReal> rateCG = this.rateCGInput.apply(beastState, indexVariables);
        RealScalar<PositiveReal> rateCT = this.rateCTInput.apply(beastState, indexVariables);
        RealScalar<PositiveReal> rateGT = this.rateGTInput.apply(beastState, indexVariables);
        Simplex baseFrequencies = this.baseFrequenciesInput.apply(beastState, indexVariables);

        if (baseFrequencies == null) {
            throw new IllegalArgumentException("GTR requires baseFrequencies input.");
        }

        if (baseFrequencies.size() != 4) {
            throw new IllegalArgumentException(
                    "GTR requires exactly four nucleotide base frequencies: A, C, G, T."
            );
        }

        if (!baseFrequencies.isValid()) {
            throw new IllegalArgumentException(
                    "GTR baseFrequencies must be a valid simplex: all values must be in [0, 1] and sum to 1."
            );
        }

        FrequencyModel frequencies = new FrequencyModel(
                Nucleotides.INSTANCE,
                baseFrequencies.getDoubleArray()
        );

        return new GTR(
                new Parameter.Default(rateAC.get()),
                new Parameter.Default(rateAG.get()),
                new Parameter.Default(rateAT.get()),
                new Parameter.Default(rateCG.get()),
                new Parameter.Default(rateCT.get()),
                new Parameter.Default(rateGT.get()),
                frequencies
        );
    }
}