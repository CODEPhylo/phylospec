package tiles;

import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.Real;
import beast.base.spec.inference.distribution.Normal;
import beast.base.spec.inference.operator.ScaleOperator;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.RealScalar;
import patternmatching.DistributionTile;
import patternmatching.EvaluatedDistribution;
import patternmatching.TypeToken;

import java.util.Set;

public class NormalTile extends DistributionTile<Normal> {

    Input<RealScalarParam<Real>> mean = new Input<>("mean", new TypeToken<>() {});
    Input<RealScalarParam<PositiveReal>> sd = new Input<>("sd", new TypeToken<>() {});

    @Override
    public String getGeneratorName() {
        return "Normal";
    }

    @Override
    protected EvaluatedDistribution<Normal> operateTile() {
        RealScalarParam<Real> state = new RealScalarParam<>();
        Normal distribution = new Normal(state, this.mean.getValue(), this.sd.getValue());

        ScaleOperator operator = new ScaleOperator();
        operator.determindClassOfInputs();
        operator.parameterInput.setValue(state, null);
        operator.m_pWeight.setValue(1.0, null);

        return new EvaluatedDistribution<>(distribution, state, new TypeToken<RealScalar<Real>>() {}.getType(), Set.of(operator));
    }

}
