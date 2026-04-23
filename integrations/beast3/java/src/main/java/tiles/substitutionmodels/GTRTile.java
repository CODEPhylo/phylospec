package tiles.substitutionmodels;

import beast.base.spec.domain.PositiveReal;
import beast.base.spec.evolution.substitutionmodel.Frequencies;
import beast.base.spec.evolution.substitutionmodel.GTR;
import beast.base.spec.type.RealScalar;
import beast.base.spec.type.Simplex;
import org.phylospec.ast.Expr;
import tiles.GeneratorTile;
import beastconfig.BEASTState;

import java.util.IdentityHashMap;

public class GTRTile extends GeneratorTile<GTR> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "gtr";
    }

    GeneratorTileInput<RealScalar<PositiveReal>> rateACInput = new GeneratorTileInput<>("rateAC");
    GeneratorTileInput<RealScalar<PositiveReal>> rateAGInput = new GeneratorTileInput<>("rateAG");
    GeneratorTileInput<RealScalar<PositiveReal>> rateATInput = new GeneratorTileInput<>("rateAT");
    GeneratorTileInput<RealScalar<PositiveReal>> rateCGInput = new GeneratorTileInput<>("rateCG");
    GeneratorTileInput<RealScalar<PositiveReal>> rateCTInput = new GeneratorTileInput<>("rateCT");
    GeneratorTileInput<RealScalar<PositiveReal>> rateGTInput = new GeneratorTileInput<>("rateGT");
    GeneratorTileInput<Simplex> baseFrequenciesInput = new GeneratorTileInput<>("baseFrequencies");

    @Override
    public GTR applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        RealScalar<PositiveReal> rateAC = this.rateACInput.apply(beastState, indexVariables);
        RealScalar<PositiveReal> rateAG = this.rateAGInput.apply(beastState, indexVariables);
        RealScalar<PositiveReal> rateAT = this.rateATInput.apply(beastState, indexVariables);
        RealScalar<PositiveReal> rateCG = this.rateCGInput.apply(beastState, indexVariables);
        RealScalar<PositiveReal> rateCT = this.rateCTInput.apply(beastState, indexVariables);
        RealScalar<PositiveReal> rateGT = this.rateGTInput.apply(beastState, indexVariables);
        Simplex baseFrequencies = this.baseFrequenciesInput.apply(beastState, indexVariables);

        // initialize frequencies

        Frequencies frequencies = new Frequencies();
        beastState.setInput(frequencies, frequencies.frequenciesInput, baseFrequencies);

        // initialize GTR

        GTR gtr = new GTR();
        beastState.setInput(gtr, gtr.rateACInput, rateAC);
        beastState.setInput(gtr, gtr.rateAGInput, rateAG);
        beastState.setInput(gtr, gtr.rateATInput, rateAT);
        beastState.setInput(gtr, gtr.rateCGInput, rateCG);
        beastState.setInput(gtr, gtr.rateCTInput, rateCT);
        beastState.setInput(gtr, gtr.rateGTInput, rateGT);
        beastState.setInput(gtr, gtr.frequenciesInput, frequencies);

        return gtr;
    }

}
