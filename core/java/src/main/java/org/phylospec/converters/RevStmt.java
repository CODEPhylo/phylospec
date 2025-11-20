package org.phylospec.converters;

import org.phylospec.components.ComponentResolver;
import org.phylospec.typeresolver.ResolvedType;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.typeresolver.TypeUtils;

class RevStmt {
    String expression;
    boolean hasMoves = false;

    RevStmt(String expression) {
        this.expression = expression;
    }

    StringBuilder build() {
        return new StringBuilder(expression);
    }

    static class Assignment extends RevStmt {
        String variableName;
        Stochasticity stochasticity;
        String[] indices;
        ResolvedType type = null;
        ComponentResolver componentResolver = null;

        Assignment(String variableName, StringBuilder expression) {
            this(variableName, new String[] {}, expression);
        }

        Assignment(String variableName, String[] indices, StringBuilder expression) {
            this(variableName, indices, Stochasticity.DETERMINISTIC, null, expression, null);
        }

        Assignment(
                String variableName, String[] indices, Stochasticity stochasticity, ResolvedType type, StringBuilder expression,
                ComponentResolver componentResolver
        ) {
            super(expression.toString());
            this.variableName = variableName;
            this.indices = indices;
            this.stochasticity = stochasticity;
            this.type = type;
            this.componentResolver = componentResolver;

            hasMoves = stochasticity == Stochasticity.STOCHASTIC;
        }

        StringBuilder build() {
            StringBuilder builder = new StringBuilder(variableName);

            for (String index : indices) {
                builder.append("[").append(index).append("]");
            }

            builder.append(
                    switch (stochasticity) {
                        case CONSTANT -> " <- ";
                        case DETERMINISTIC -> " := ";
                        case STOCHASTIC -> " ~ ";
                        case UNDEFINED ->
                                throw new RevConverter.RevConversionError("Undefined statement type. This should not happen.");
                    }
            );

            builder.append(expression);

            if (hasMoves) {
                StringBuilder moves = buildMoves();
                if (moves != null) builder.append(moves);
            }

            return builder;
        }

        private StringBuilder buildMoves() {
            if (covers("Probability", type)) {
                return buildMove("mvBetaProbability");
            } else if (covers("PositiveReal", type)) {
                return buildMove("mvScaleBactrian");
            } else if (covers("Real", type)) {
                return buildMove("mvSlideBactrian");
            } else if (covers("Simplex", type)) {
                return buildMove("mvDirichletSimplex");
            } else if (covers("Tree", type)) {
                StringBuilder moves = buildMove("mvNarrow");
                moves.append(buildMove("mvNNI"));
                moves.append(buildMove("mvFNPR"));
                moves.append(buildMove("mvSubtreeScale"));
                moves.append(buildMove("mvNodeTimeSlideUniform"));
                moves.append(buildMove("mvRootTimeScaleBactrian"));
                moves.append(buildMove("mvTreeScale"));
                return moves;
            }

            ResolvedType vectorType = TypeUtils.recoverType("Vector", type, componentResolver);
            if (vectorType != null) {
                ResolvedType elementType = vectorType.getParameterTypes().get("T");
                if (covers("PositiveReal", elementType)) {
                    return buildMove("mvVectorSingleElementScale");
                } else if (covers("Real", elementType)) {
                    return buildMove("mvVectorSingleElementSlide");
                }
            }

            return null;
        }

        private StringBuilder buildMove(String moveName) {
            StringBuilder move = new StringBuilder();
            move.append("\nmoves.append( ").append(moveName).append("( ").append(variableName);
            for (String index : indices) {
                move.append("[").append(index).append("]");
            }
            move.append(" ) )");
            return move;
        }

        private boolean covers(String typeString, ResolvedType type) {
            return TypeUtils.covers(ResolvedType.fromString(typeString, componentResolver), type, componentResolver);
        }
    }
}
