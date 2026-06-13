package tiling;

import beast.base.evolution.alignment.Alignment;
import beast.base.evolution.tree.TreeDistribution;
import beast.base.inference.Distribution;
import beast.base.inference.StateNode;
import beast.base.spec.domain.Int;
import beast.base.spec.domain.PositiveInt;
import beast.base.spec.inference.distribution.Normal;
import beast.base.spec.inference.distribution.ScalarDistribution;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.inference.parameter.RealScalarParam;
import org.junit.jupiter.api.Test;
import org.phylospec.domain.PositiveReal;
import org.phylospec.domain.Real;
import org.phylospec.types.RealScalar;
import org.phylospec.types.Scalar;

import java.util.List;

public class TypeTokenTests {

    @Test
    public void testSameTypeIsAssignableFrom() {
        assert new TypeToken<Object>() {}.isAssignableFrom(new TypeToken<Object>() {});
        assert new TypeToken<Double>() {}.isAssignableFrom(new TypeToken<Double>() {});
        assert new TypeToken<RealScalar<Real>>() {}.isAssignableFrom(new TypeToken<RealScalar<Real>>() {});
        assert new TypeToken<List<RealScalar<Real>>>() {}.isAssignableFrom(new TypeToken<List<RealScalar<Real>>>() {});
    }

    @Test
    public void testTypeIsAssignableFromSubType() {
        assert new TypeToken<Object>() {}.isAssignableFrom(new TypeToken<Double>() {});
        assert new TypeToken<Number>() {}.isAssignableFrom(new TypeToken<Double>() {});
        assert new TypeToken<StateNode>() {}.isAssignableFrom(new TypeToken<Alignment>() {});
        assert new TypeToken<Distribution>() {}.isAssignableFrom(new TypeToken<TreeDistribution>() {});
        assert new TypeToken<Scalar<Real, Double>>() {}.isAssignableFrom(new TypeToken<RealScalar<Real>>() {});
        assert new TypeToken<ScalarDistribution<beast.base.spec.type.RealScalar<beast.base.spec.domain.Real>, Double>>() {}.isAssignableFrom(new TypeToken<Normal>() {});
    }

    @Test
    public void testCovariantTypeIsAssignableFromSubType() {
        assert new TypeToken<List<? extends Object>>() {}.isAssignableFrom(new TypeToken<List<Double>>() {});
        assert new TypeToken<UnboundDistribution<RealScalarParam<beast.base.spec.domain.Real>, ? extends ScalarDistribution<beast.base.spec.type.RealScalar<beast.base.spec.domain.Real>, Double>>>() {}.isAssignableFrom(new TypeToken<UnboundDistribution<RealScalarParam<beast.base.spec.domain.Real>, Normal>>() {});
    }

    @Test
    public void testInvariantTypeIsNotAssignableFromSubType() {
        assert !new TypeToken<List<Object>>() {}.isAssignableFrom(new TypeToken<List<Double>>() {});
        assert !new TypeToken<RealScalar<Real>>() {}.isAssignableFrom(new TypeToken<RealScalar<PositiveReal>>() {});
        assert !new TypeToken<RealScalar<PositiveReal>>() {}.isAssignableFrom(new TypeToken<RealScalar<Real>>() {});
        assert !new TypeToken<IntScalarParam<PositiveInt>>() {}.isAssignableFrom(new TypeToken<IntScalarParam<Int>>() {});
        assert !new TypeToken<IntScalarParam<Int>>() {}.isAssignableFrom(new TypeToken<IntScalarParam<PositiveInt>>() {});
    }

}
