package org.phylospec.tiling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DimensionUnifierTest {

    @Test
    public void literalDimensionsMatchWhenEqual() {
        DimensionUnifier unifier = new DimensionUnifier();

        assertEquals(
                DimensionUnifier.Result.MATCH,
                unifier.unify(Dimension.literal(4), Dimension.literal(4))
        );
    }

    @Test
    public void literalDimensionsMismatchWhenDifferent() {
        DimensionUnifier unifier = new DimensionUnifier();

        assertEquals(
                DimensionUnifier.Result.MISMATCH,
                unifier.unify(Dimension.literal(4), Dimension.literal(20))
        );
    }

    @Test
    public void variableBindsToLiteralAndMatchesSameLiteral() {
        DimensionUnifier unifier = new DimensionUnifier();

        assertEquals(
                DimensionUnifier.Result.MATCH,
                unifier.unify(Dimension.variable("D"), Dimension.literal(4))
        );

        assertEquals(
                DimensionUnifier.Result.MATCH,
                unifier.unify(Dimension.variable("D"), Dimension.literal(4))
        );

        assertEquals(
                Dimension.literal(4),
                unifier.resolve(Dimension.variable("D"))
        );
    }

    @Test
    public void variableBindsToLiteralAndRejectsDifferentLiteral() {
        DimensionUnifier unifier = new DimensionUnifier();

        assertEquals(
                DimensionUnifier.Result.MATCH,
                unifier.unify(Dimension.variable("D"), Dimension.literal(4))
        );

        assertEquals(
                DimensionUnifier.Result.MISMATCH,
                unifier.unify(Dimension.variable("D"), Dimension.literal(20))
        );
    }

    @Test
    public void symbolicDimensionsMatchWhenExpressionsAreEqual() {
        DimensionUnifier unifier = new DimensionUnifier();

        assertEquals(
                DimensionUnifier.Result.MATCH,
                unifier.unify(
                        Dimension.symbolic("numStates(alignment)"),
                        Dimension.symbolic("numStates(alignment)")
                )
        );
    }

    @Test
    public void differentSymbolicDimensionsAreUnknownForNow() {
        DimensionUnifier unifier = new DimensionUnifier();

        assertEquals(
                DimensionUnifier.Result.UNKNOWN,
                unifier.unify(
                        Dimension.symbolic("numStates(alignment)"),
                        Dimension.symbolic("numStates(otherAlignment)")
                )
        );
    }

    @Test
    public void unknownDimensionProducesUnknownResult() {
        DimensionUnifier unifier = new DimensionUnifier();

        assertEquals(
                DimensionUnifier.Result.UNKNOWN,
                unifier.unify(Dimension.variable("D"), Dimension.unknown())
        );
    }
}
