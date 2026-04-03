package tiles;

import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.Real;
import beast.base.spec.inference.distribution.Normal;
import beast.base.spec.inference.operator.ScaleOperator;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.RealScalar;
import patternmatching.*;

import java.util.Set;

public class NormalTile extends GeneratorTile<EvaluatedDistribution<Normal>> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "Normal";
    }

    Input<RealScalar<Real>> meanInput = new Input<>("mean");
    Input<RealScalar<PositiveReal>> sdInput = new Input<>("sd");

    @Override
    public EvaluatedDistribution<Normal> applyTile(BEASTState beastState) {
        RealScalar<Real> mean = this.meanInput.apply(beastState);
        RealScalar<PositiveReal> sd = this.sdInput.apply(beastState);

        RealScalarParam<Real> state = new RealScalarParam<>();
        Normal distribution = new Normal(state, mean, sd);

        ScaleOperator operator = new ScaleOperator();
        operator.determindClassOfInputs();
        operator.parameterInput.setValue(state, null);
        operator.m_pWeight.setValue(1.0, null);

        return new EvaluatedDistribution<>(
                distribution, state, new TypeToken<RealScalar<Real>>() {}.getType(), Set.of(operator)
        );
    }

    @Override
    protected Tile<EvaluatedDistribution<Normal>> createInstance() {
        return new NormalTile();
    }
}
