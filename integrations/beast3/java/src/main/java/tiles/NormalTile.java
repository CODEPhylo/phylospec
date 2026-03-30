package tiles;

import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.Real;
import beast.base.spec.inference.distribution.Normal;
import beast.base.spec.inference.parameter.RealScalarParam;
import patternmatching.GeneratorTile;
import patternmatching.TypeToken;

public class NormalTile extends GeneratorTile<Normal> {

    Input<RealScalarParam<Real>> mean = new Input<>("mean", new TypeToken<>() {});
    Input<RealScalarParam<PositiveReal>> sd = new Input<>("sd", new TypeToken<>() {});

    @Override
    public String getGeneratorName() {
        return "Normal";
    }

    @Override
    protected Normal operateTile() {
        RealScalarParam<Real> state = new RealScalarParam<>();
        return new Normal(state, this.mean.getValue(), this.sd.getValue());
    }

}
