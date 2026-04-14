package tiles.substitutionmodels;

import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.Real;
import beast.base.spec.evolution.substitutionmodel.GTR;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.RealScalar;
import beast.base.spec.type.RealVector;
import beast.base.spec.type.Simplex;
import tiles.GeneratorTile;
import tiling.BEASTState;

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
    public GTR applyTile(BEASTState beastState) {
        RealScalar<PositiveReal> rateAC = this.rateACInput.apply(beastState);
        RealScalar<PositiveReal> rateAG = this.rateAGInput.apply(beastState);
        RealScalar<PositiveReal> rateAT = this.rateATInput.apply(beastState);
        RealScalar<PositiveReal> rateCG = this.rateCGInput.apply(beastState);
        RealScalar<PositiveReal> rateCT = this.rateCTInput.apply(beastState);
        RealScalar<PositiveReal> rateGT = this.rateGTInput.apply(beastState);
        Simplex baseFrequencies = this.baseFrequenciesInput.apply(beastState);

        GTR gtr = new GTR();
        gtr.initByName(
                "rateAC", rateAC, "rateAG", rateAG, "rateAT", rateAT,
                "rateCG", rateCG, "rateCT", rateCT, "rateGT", rateGT,
                "frequencies", baseFrequencies
        );

        return gtr;
    }

}
