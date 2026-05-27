package tiling;

import dr.inference.model.Parameter;
import dr.inference.model.Statistic;
import org.phylospec.lexer.TokenType;
import org.phylospec.types.RealScalar;

import java.util.LinkedHashMap;
import java.util.Map;

public record BeastXRPNCalculationResult(
        String calculation,
        Map<String, Statistic> variables
) {

    public static BeastXRPNCalculationResult combine(
            TokenType operator,
            BeastXRPNCalculationResult left,
            BeastXRPNCalculationResult right
    ) {
        Map<String, Statistic> variables =
                new LinkedHashMap<>();

        variables.putAll(left.variables);
        variables.putAll(right.variables);

        String calculation =
                left.calculation
                        + " "
                        + right.calculation
                        + " "
                        + TokenType.getLexeme(operator);

        return new BeastXRPNCalculationResult(calculation, variables);
    }

    public static BeastXRPNCalculationResult combineUnary(
            String operator,
            BeastXRPNCalculationResult value
    ) {
        return new BeastXRPNCalculationResult(
                value.calculation + " " + operator,
                new LinkedHashMap<>(value.variables)
        );
    }

    public static BeastXRPNCalculationResult from(
            RealScalar<?> scalar,
            BeastXState beastState
    ) {
        if (scalar instanceof BeastXRealScalarParam<?> beastXScalar) {
            return from(beastXScalar.getParameter(), beastState);
        }

        return from(new Parameter.Default(scalar.get()), beastState);
    }

    public static BeastXRPNCalculationResult from(
            Statistic statistic,
            BeastXState beastState
    ) {
        if (statistic.getId() == null) {
            statistic.setId(beastState.getAvailableID("var"));
        }

        Map<String, Statistic> variables =
                new LinkedHashMap<>();

        variables.put(statistic.getId(), statistic);

        return new BeastXRPNCalculationResult(
                statistic.getId(),
                variables
        );
    }
}