package org.phylospec.ast;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.phylospec.ast.Expr.Call.Parameter;

/**
 * Tests how the arguments of a call are mapped onto the parameters declared by the callee,
 * and in which order the possible failures are reported.
 */
public class ArgumentResolutionTest {

    /*
     * binding rules
     */

    @Test
    public void testExplicitNamesBind() {
        Map<String, Expr.Argument> bound =
                call(named("mean", literal(1.0)), named("sd", literal(2.0))).resolveArgumentNames(normal());

        assertEquals(List.of("mean", "sd"), List.copyOf(bound.keySet()));
    }

    @Test
    public void testVariableNamesBind() {
        Map<String, Expr.Argument> bound =
                call(unnamed(variable("sd")), unnamed(variable("mean"))).resolveArgumentNames(normal());

        // the arguments bind by variable name, independently of their position
        assertEquals(List.of("sd", "mean"), List.copyOf(bound.keySet()));
    }

    @Test
    public void testSingleValueBindsToSingleRequiredParameter() {
        Map<String, Expr.Argument> bound = call(unnamed(literal(10))).resolveArgumentNames(log());

        assertEquals(List.of("x"), List.copyOf(bound.keySet()));
    }

    @Test
    public void testSingleVariableBindsToSingleRequiredParameter() {
        // the variable name matches no parameter, so the single required parameter takes it
        Map<String, Expr.Argument> bound = call(unnamed(variable("myValue"))).resolveArgumentNames(log());

        assertEquals(List.of("x"), List.copyOf(bound.keySet()));
    }

    @Test
    public void testVariableNameWinsOverSingleRequiredParameter() {
        // 'base' names a parameter, so the value must not silently go to the required 'x'
        assertEquals(
                "x",
                assertThrows(ArgumentResolutionError.MissingRequired.class, () -> call(unnamed(variable("base")))
                                .resolveArgumentNames(log()))
                        .name);
    }

    /*
     * failure order: MissingName > DuplicateName > MissingRequired (when unexplained)
     *              > UnknownName > MissingRequired
     */

    @Test
    public void testMissingNameOutranksMissingRequired() {
        // two required parameters, so the unnamed value cannot be placed
        assertThrows(ArgumentResolutionError.MissingName.class, () -> call(unnamed(literal(10)))
                .resolveArgumentNames(hky()));
    }

    @Test
    public void testDuplicateNameOutranksMissingRequired() {
        assertEquals(
                "mean",
                assertThrows(ArgumentResolutionError.DuplicateName.class, () -> call(
                                        named("mean", literal(1.0)), named("mean", literal(2.0)))
                                .resolveArgumentNames(normal()))
                        .name);
    }

    @Test
    public void testMissingRequiredOutranksUnplaceableName() {
        // one argument cannot be placed, but two arguments are missing, so at least one of them
        // is missing on its own
        assertEquals(
                "clockRate",
                assertThrows(ArgumentResolutionError.MissingRequired.class, () -> call(unnamed(variable("myTree")))
                                .resolveArgumentNames(strictClockTile()))
                        .name);
    }

    @Test
    public void testUnknownNameOutranksMissingRequiredItExplains() {
        // the single missing argument is fully explained by the argument we could not place
        assertEquals(
                "a",
                assertThrows(ArgumentResolutionError.UnknownName.class, () -> call(
                                        unnamed(variable("a")), named("base", literal(2)))
                                .resolveArgumentNames(log()))
                        .name);
    }

    @Test
    public void testTypoInWrittenNameIsReportedAsUnknownName() {
        assertEquals(
                "a",
                assertThrows(ArgumentResolutionError.UnknownName.class, () -> call(named("a", literal(1)))
                                .resolveArgumentNames(log()))
                        .name);
    }

    @Test
    public void testMissingRequiredIsReportedWhenEverythingElseBinds() {
        assertEquals(
                "sd",
                assertThrows(ArgumentResolutionError.MissingRequired.class, () -> call(named("mean", literal(1.0)))
                                .resolveArgumentNames(normal()))
                        .name);
    }

    /*
     * helpers
     */

    private static List<Parameter> normal() {
        return List.of(new Parameter("mean", true), new Parameter("sd", true));
    }

    private static List<Parameter> log() {
        return List.of(new Parameter("x", true), new Parameter("base", false));
    }

    private static List<Parameter> hky() {
        return List.of(new Parameter("kappa", true), new Parameter("baseFrequencies", true));
    }

    /** The tile for StrictClock needs a clock rate, even though the generator does not. */
    private static List<Parameter> strictClockTile() {
        return List.of(new Parameter("clockRate", true), new Parameter("tree", true));
    }

    private static Expr.Call call(Expr.Argument... arguments) {
        return new Expr.Call("f", arguments);
    }

    private static Expr.Argument named(String name, Expr expression) {
        return new Expr.AssignedArgument(name, expression);
    }

    private static Expr.Argument unnamed(Expr expression) {
        return new Expr.AssignedArgument(expression);
    }

    private static Expr variable(String name) {
        return new Expr.Variable(name);
    }

    private static Expr literal(Object value) {
        return new Expr.Literal(value);
    }
}
