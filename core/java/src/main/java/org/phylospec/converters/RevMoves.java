package org.phylospec.converters;

import org.phylospec.components.ComponentResolver;
import org.phylospec.typeresolver.ResolvedType;
import org.phylospec.typeresolver.TypeUtils;

public class RevMoves {
    static StringBuilder getMoveStatement(String variableName, ResolvedType type, ComponentResolver componentResolver) {
        if (covers("Probability", type, componentResolver)) {
            return buildMove(variableName, "mvBetaProbability");
        } else if (covers("PositiveReal", type, componentResolver)) {
            return buildMove(variableName, "mvScaleBactrian");
        } else if (covers("Real", type, componentResolver)) {
            return buildMove(variableName, "mvSlideBactrian");
        } else if (covers("Simplex", type, componentResolver)) {
            return buildMove(variableName, "mvDirichletSimplex");
        }
        return null;
    }

    private static boolean covers(String typeString, ResolvedType type, ComponentResolver componentResolver) {
        return TypeUtils.covers(ResolvedType.fromString(typeString, componentResolver), type, componentResolver);
    }

    private static StringBuilder buildMove(String variableName, String moveName) {
        return new StringBuilder().append("moves.append( ").append(moveName).append("( ").append(variableName).append(" ) )");
    }
}
