package org.phylospec.converters;

import org.phylospec.components.ComponentResolver;
import org.phylospec.typeresolver.ResolvedType;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.typeresolver.TypeUtils;

class RevStmt {
    StringBuilder expression;

    RevStmt(StringBuilder expression) {
        this.expression = expression;
    }

    StringBuilder build() {
        return expression;
    }

    static class Assignment extends RevStmt {
        String variableName;
        Stochasticity stochasticity;
        ResolvedType type;
        ComponentResolver componentResolver;

        Assignment(String variableName, Stochasticity stochasticity, ResolvedType type, StringBuilder expression, ComponentResolver componentResolver) {
            super(expression);
            this.variableName = variableName;
            this.stochasticity = stochasticity;
            this.type = type;
            this.componentResolver = componentResolver;
        }

        StringBuilder build() {
            StringBuilder builder = new StringBuilder();
            builder.append(variableName);

            builder.append(
                    switch (stochasticity) {
                        case CONSTANT -> " <- ";
                        case DETERMINISTIC -> " := ";
                        case STOCHASTIC -> " ~ ";
                        case UNDEFINED ->
                                throw new RevConverter.RevConversionError("Undefined statement type. This should not happen.");
                    }
            );

            builder.append(expression).append("\n");

            StringBuilder moves = buildMoves();
            if (stochasticity == Stochasticity.STOCHASTIC && moves != null) builder.append(moves);

            return builder;
        }

        private StringBuilder buildMoves() {
            if (covers("Probability")) {
                return buildMove("mvBetaProbability");
            } else if (covers("PositiveReal")) {
                return buildMove("mvScaleBactrian");
            } else if (covers("Real")) {
                return buildMove("mvSlideBactrian");
            } else if (covers("Simplex")) {
                return buildMove("mvDirichletSimplex");
            }

            return null;
        }

        private StringBuilder buildMove(String moveName) {
            return new StringBuilder().append("moves.append( ").append(moveName).append("( ").append(variableName).append(" ) )\n");
        }

        private boolean covers(String typeString) {
            return TypeUtils.covers(ResolvedType.fromString(typeString, componentResolver), type, componentResolver);
        }
    }

}
