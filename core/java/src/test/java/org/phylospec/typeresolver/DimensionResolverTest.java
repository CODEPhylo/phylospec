package org.phylospec.typeresolver;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.Dimension;

public class DimensionResolverTest {

    @Test
    public void arrayDimensionIsArrayLength() {
        DimensionResolver resolver = new DimensionResolver(new VariableResolver(List.of()));

        Expr.Array array =
                new Expr.Array(
                        List.of(
                                new Expr.Literal(0.2),
                                new Expr.Literal(0.2),
                                new Expr.Literal(0.2),
                                new Expr.Literal(0.2),
                                new Expr.Literal(0.2)));

        assertEquals(Dimension.literal(5), resolver.getDimension(array));
    }

    @Test
    public void repeatDimensionComesFromLiteralNumArgument() {
        DimensionResolver resolver = new DimensionResolver(new VariableResolver(List.of()));

        Expr.Call repeat =
                new Expr.Call(
                        "repeat",
                        new Expr.AssignedArgument("value", new Expr.Literal(0.25)),
                        new Expr.AssignedArgument("num", new Expr.Literal(4)));

        assertEquals(Dimension.literal(4), resolver.getDimension(repeat));
    }

    @Test
    public void dirichletDimensionComesFromConcentrationArgument() {
        DimensionResolver resolver = new DimensionResolver(new VariableResolver(List.of()));

        Expr.Call repeat =
                new Expr.Call(
                        "repeat",
                        new Expr.AssignedArgument("value", new Expr.Literal(1.0)),
                        new Expr.AssignedArgument("num", new Expr.Literal(4)));

        Expr.Call dirichlet =
                new Expr.Call("Dirichlet", new Expr.AssignedArgument("concentration", repeat));

        assertEquals(Dimension.literal(4), resolver.getDimension(dirichlet));
    }
}
