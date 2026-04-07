package tiles.substitutionmodels;

import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.Real;
import beast.base.spec.evolution.substitutionmodel.GTR;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.RealVector;
import beast.base.spec.type.Simplex;
import tiles.GeneratorTile;
import tiling.BEASTState;
import tiling.Tile;

public class GTRTile extends GeneratorTile<GTR> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "gtr";
    }

    // rates in order: AC, AG, AT, CG, CT, GT
    Input<RealVector<Real>> rateMatrixInput = new Input<>("rateMatrix");
    Input<Simplex> baseFrequenciesInput = new Input<>("baseFrequencies");

    @Override
    public GTR applyTile(BEASTState beastState) {
        RealVector<Real> rates = this.rateMatrixInput.apply(beastState);
        Simplex baseFrequencies = this.baseFrequenciesInput.apply(beastState);

        RealScalarParam<PositiveReal> rateAC = new RealScalarParam<>(rates.get(0), PositiveReal.INSTANCE);
        RealScalarParam<PositiveReal> rateAG = new RealScalarParam<>(rates.get(1), PositiveReal.INSTANCE);
        RealScalarParam<PositiveReal> rateAT = new RealScalarParam<>(rates.get(2), PositiveReal.INSTANCE);
        RealScalarParam<PositiveReal> rateCG = new RealScalarParam<>(rates.get(3), PositiveReal.INSTANCE);
        RealScalarParam<PositiveReal> rateCT = new RealScalarParam<>(rates.get(4), PositiveReal.INSTANCE);
        RealScalarParam<PositiveReal> rateGT = new RealScalarParam<>(rates.get(5), PositiveReal.INSTANCE);

        GTR gtr = new GTR();
        gtr.initByName(
                "rateAC", rateAC, "rateAG", rateAG, "rateAT", rateAT,
                "rateCG", rateCG, "rateCT", rateCT, "rateGT", rateGT,
                "frequencies", baseFrequencies
        );

        return gtr;
    }

    @Override
    protected Tile<?> createInstance() {
        return new GTRTile();
    }

}
