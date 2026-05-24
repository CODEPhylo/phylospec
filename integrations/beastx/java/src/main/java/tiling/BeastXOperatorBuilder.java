package tiling;

import dr.inference.model.Parameter;
import dr.inference.operators.AdaptationMode;
import dr.inference.operators.DeltaExchangeOperator;
import dr.inference.operators.MCMCOperator;
import dr.inference.operators.RandomWalkOperator;
import dr.inference.operators.ScaleOperator;
import org.phylospec.domain.NonNegativeReal;
import org.phylospec.domain.PositiveReal;
import org.phylospec.domain.UnitInterval;
import org.phylospec.tiling.TypeToken;
import org.phylospec.types.RealScalar;
import org.phylospec.types.RealVector;
import org.phylospec.types.Simplex;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BeastXOperatorBuilder {

    private static final double DEFAULT_WEIGHT = 1.0;
    private static final double DEFAULT_WINDOW_SIZE = 1.0;
    private static final double DEFAULT_SCALE_FACTOR = 0.75;

    private static final TypeToken<?> SIMPLEX =
            new TypeToken<Simplex>() {};

    private static final TypeToken<?> POSITIVE_REAL_SCALAR =
            new TypeToken<RealScalar<? extends PositiveReal>>() {};

    private static final TypeToken<?> POSITIVE_REAL_VECTOR =
            new TypeToken<RealVector<? extends PositiveReal>>() {};

    private static final TypeToken<?> NON_NEGATIVE_REAL_SCALAR =
            new TypeToken<RealScalar<? extends NonNegativeReal>>() {};

    private static final TypeToken<?> UNIT_INTERVAL_REAL_SCALAR =
            new TypeToken<RealScalar<UnitInterval>>() {};

    public List<MCMCOperator> build(BeastXState beastState) {
        List<MCMCOperator> operators =
                new ArrayList<>();

        for (Map.Entry<Parameter, TypeToken<?>> entry : beastState.stateNodes.entrySet()) {
            Parameter parameter =
                    entry.getKey();

            TypeToken<?> typeToken =
                    entry.getValue();

            operators.add(buildOperator(parameter, typeToken));
        }

        return operators;
    }

    private MCMCOperator buildOperator(Parameter parameter, TypeToken<?> typeToken) {
        if (SIMPLEX.isAssignableFrom(typeToken)) {
            return buildDeltaExchangeOperator(parameter);
        }

        if (POSITIVE_REAL_SCALAR.isAssignableFrom(typeToken)
                || POSITIVE_REAL_VECTOR.isAssignableFrom(typeToken)) {
            return buildScaleOperator(parameter);
        }

        if (NON_NEGATIVE_REAL_SCALAR.isAssignableFrom(typeToken)
                || UNIT_INTERVAL_REAL_SCALAR.isAssignableFrom(typeToken)) {
            return buildRandomWalkOperator(parameter);
        }

        return buildRandomWalkOperator(parameter);
    }

    private MCMCOperator buildScaleOperator(Parameter parameter) {
        return new ScaleOperator(
                parameter,
                DEFAULT_SCALE_FACTOR,
                AdaptationMode.DEFAULT,
                DEFAULT_WEIGHT
        );
    }

    private MCMCOperator buildRandomWalkOperator(Parameter parameter) {
        return new RandomWalkOperator(
                parameter,
                DEFAULT_WINDOW_SIZE,
                RandomWalkOperator.BoundaryCondition.reflecting,
                DEFAULT_WEIGHT,
                AdaptationMode.DEFAULT
        );
    }

    private MCMCOperator buildDeltaExchangeOperator(Parameter parameter) {
        return new DeltaExchangeOperator(
                parameter,
                DEFAULT_WEIGHT
        );
    }
}