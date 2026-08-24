package org.phylospec.engines;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.phylospec.ast.Expr;
import org.phylospec.ast.Stmt;
import org.phylospec.components.Argument;
import org.phylospec.components.Argument__1;
import org.phylospec.components.ComponentResolver;
import org.phylospec.components.EngineSpecificationSchema;
import org.phylospec.components.Generator;
import org.phylospec.components.Generator__1;
import org.phylospec.lexer.Lexer;
import org.phylospec.parser.Parser;

public class EngineSupportTest {

    private static final Path SPECIFICATION_PATH =
            Path.of("src/test/java/org/phylospec/engines/test-engine-specification.json");

    private static EngineSupport support;
    private static ComponentResolver componentResolver;

    @BeforeAll
    public static void loadEngineAndComponents() throws IOException {
        try (InputStream specificationStream = Files.newInputStream(SPECIFICATION_PATH)) {
            EngineSpecificationSchema engine =
                    new ObjectMapper().readValue(specificationStream, EngineSpecificationSchema.class);
            support = EngineSupport.of(engine);
        }

        componentResolver = new ComponentResolver(ComponentResolver.loadCoreComponentLibraries());
    }

    /* generators */

    @Test
    public void testOneEntryCoversBothOverloads() {
        // the component library declares the optional rootAge by having a second overload, while
        // the engine declares it with `required: false`. so the single entry covers both overloads

        List<Generator> overloads = componentResolver.resolveGenerator("Yule");
        assertTrue(overloads.stream().anyMatch(overload -> overload.getArguments().stream()
                .anyMatch(argument -> argument.getName().equals("rootAge"))));

        for (Generator overload : overloads) {
            assertTrue(support.supports(overload).isFullySupported(), "Yule with " + getArgumentNames(overload));
        }
    }

    @Test
    public void testArgumentOrderIsIgnored() {
        // the component library declares `siteRates` before `branchRates`, the engine the other
        // way around. calls name their arguments, so the order carries no meaning

        Generator phyloCtmc = getOverloadWithArgument("PhyloCTMC", "qMatrix");
        assertTrue(support.supports(phyloCtmc).isFullySupported());
    }

    @Test
    public void testUnofferedArgumentIsNotCovered() {
        // the engine offers `qMatrix` but never heard of `siteQMatrices`, so it does not implement
        // this overload

        Generator siteQMatrices = getOverloadWithArgument("PhyloCTMC", "siteQMatrices");
        assertFalse(support.supports(siteQMatrices).isFullySupported());

        // the same for the `date` overload of fromNexus, where the engine only offers `age`

        Generator fromNexusByDate = getOverloadWithArgument("fromNexus", "date");
        assertFalse(support.supports(fromNexusByDate).isFullySupported());

        Generator fromNexusByAge = getOverloadWithArgument("fromNexus", "age");
        assertTrue(support.supports(fromNexusByAge).isFullySupported());
    }

    @Test
    public void testUnimplementedGeneratorIsNotCovered() {
        for (Generator overload : componentResolver.resolveGenerator("LogNormal")) {
            assertFalse(support.supports(overload).isFullySupported());
        }
    }

    @Test
    public void testInsistedArgumentHasToBeDeclared() {
        // the engine requires an argument the component library does not declare at all, so the
        // two are not the same component

        Generator__1 demanding = new Generator__1();
        demanding.setName("jc69");
        demanding.setArguments(List.of(engineArgument("rates", true)));

        EngineSpecificationSchema engine = new EngineSpecificationSchema();
        engine.setName("demandingEngine");
        engine.setEngineVersion("1.0.0");
        engine.setGenerators(List.of(demanding));

        Generator jc69 = componentResolver.resolveGenerator("jc69").getFirst();
        assertTrue(jc69.getArguments().isEmpty());

        assertFalse(EngineSupport.of(engine).supports(jc69).isFullySupported());
    }

    @Test
    public void testGeneratorOfAnotherNamespaceIsNotCovered() {
        // both sides name the namespace, and they disagree, so this is not the same component

        Generator__1 foreign = new Generator__1();
        foreign.setName("jc69");
        foreign.setNamespace("some.other.library");
        foreign.setArguments(List.of());

        EngineSpecificationSchema engine = new EngineSpecificationSchema();
        engine.setName("foreignEngine");
        engine.setEngineVersion("1.0.0");
        engine.setGenerators(List.of(foreign));

        Generator jc69 = componentResolver.resolveGenerator("jc69").getFirst();
        assertFalse(EngineSupport.of(engine).supports(jc69).isFullySupported());
    }

    /* calls */

    @Test
    public void testCallWithNamedArguments() {
        assertTrue(support.supports(new Expr.Call(
                        "Yule",
                        new Expr.AssignedArgument("birthRate", new Expr.Literal(1.0)),
                        new Expr.AssignedArgument("taxa", new Expr.Variable("data"))))
                .isFullySupported());

        assertTrue(support.supports(new Expr.Call(
                        "Yule",
                        new Expr.AssignedArgument("birthRate", new Expr.Literal(1.0)),
                        new Expr.AssignedArgument("rootAge", new Expr.Literal(2.0)),
                        new Expr.AssignedArgument("taxa", new Expr.Variable("data"))))
                .isFullySupported());
    }

    @Test
    public void testCallWithQualifiedName() {
        assertTrue(support.supports(new Expr.Call(
                        "phylospec.distributions.tree.Yule",
                        new Expr.AssignedArgument("birthRate", new Expr.Literal(1.0)),
                        new Expr.AssignedArgument("taxa", new Expr.Variable("data"))))
                .isFullySupported());
    }

    @Test
    public void testCallWithArgumentsNamedByVariables() {
        // neither argument is named, but a variable names the argument it is passed to

        assertTrue(support.supports(new Expr.Call(
                        "PhyloCTMC",
                        new Expr.AssignedArgument(new Expr.Variable("tree")),
                        new Expr.AssignedArgument(new Expr.Variable("qMatrix"))))
                .isFullySupported());
    }

    @Test
    public void testCallWithPositionalArgument() {
        // a value that names no argument of its own can only be passed to a generator with a
        // single required argument, as nothing else says where it goes

        assertTrue(support.supports(new Expr.Call("exp", new Expr.AssignedArgument(new Expr.Literal(1.0))))
                .isFullySupported());

        assertFalse(support.supports(new Expr.Call(
                        "PhyloCTMC",
                        new Expr.AssignedArgument(new Expr.Literal(1.0)),
                        new Expr.AssignedArgument(new Expr.Variable("qMatrix"))))
                .isFullySupported());
    }

    @Test
    public void testCallWithSingleUnnamedVariable() {
        // `exp(mean)` is ambiguous: `mean` either names the argument or fills the first one. the
        // engine calls the argument `x`, so only the second reading works

        assertTrue(support.supports(new Expr.Call("exp", new Expr.AssignedArgument(new Expr.Variable("mean"))))
                .isFullySupported());

        // and the reading by name works as well

        assertTrue(support.supports(new Expr.Call("exp", new Expr.AssignedArgument(new Expr.Variable("x"))))
                .isFullySupported());
    }

    @Test
    public void testCallPassingAnUnofferedArgument() {
        assertFalse(support.supports(new Expr.Call(
                        "Yule",
                        new Expr.AssignedArgument("birthRate", new Expr.Literal(1.0)),
                        new Expr.AssignedArgument("taxa", new Expr.Variable("data")),
                        new Expr.AssignedArgument("originTime", new Expr.Literal(3.0))))
                .isFullySupported());
    }

    @Test
    public void testCallMissingARequiredArgument() {
        assertFalse(
                support.supports(new Expr.Call("Yule", new Expr.AssignedArgument("birthRate", new Expr.Literal(1.0))))
                        .isFullySupported());
    }

    @Test
    public void testCallOfAnUnimplementedGenerator() {
        assertFalse(
                support.supports(new Expr.Call("LogNormal", new Expr.AssignedArgument("mean", new Expr.Literal(1.0))))
                        .isFullySupported());
    }

    /* statements */

    @Test
    public void testStatementWithoutCalls() {
        assertTrue(support.supports(parse("use phylospec.distributions")).isFullySupported());
    }

    @Test
    public void testStatementWithNestedCalls() {
        // the nested LogNormal is not implemented, so the whole statement is not supported

        List<Stmt> unsupported = parse("Tree tree ~ Yule(birthRate~LogNormal(mean=0.2, logSd=1.0), taxa=taxa(data))");
        assertFalse(support.supports(unsupported).isFullySupported());

        List<Stmt> supported = parse("Tree tree ~ Yule(birthRate=0.2, taxa=taxa(data))");
        assertTrue(support.supports(supported).isFullySupported());
    }

    @Test
    public void testModelIsEveryStatement() {
        List<Stmt> model = parse("""
                Alignment data = fromNexus("primates.nex")
                QMatrix qMatrix = jc69()
                Alignment alignment ~ PhyloCTMC(tree, qMatrix) observed as data
                """);

        assertTrue(support.supports(model).isFullySupported());
    }

    /* support levels */

    @Test
    public void testPartiallyImplementedGenerator() {
        // the engine offers `tree` but not `siteQMatrices`, so it implements part of the overload

        EngineSupport.GeneratorSupport siteQMatrices =
                support.supports(getOverloadWithArgument("PhyloCTMC", "siteQMatrices"));
        assertEquals(EngineSupport.Support.PARTIAL_SUPPORT, siteQMatrices.support());
        assertEquals(true, siteQMatrices.argumentSupport().get("tree"));
        assertEquals(false, siteQMatrices.argumentSupport().get("siteQMatrices"));

        // an unimplemented generator has no argument the engine could offer

        EngineSupport.GeneratorSupport logNormal =
                support.supports(componentResolver.resolveGenerator("LogNormal").getFirst());
        assertEquals(EngineSupport.Support.NO_SUPPORT, logNormal.support());
        assertTrue(logNormal.argumentSupport().values().stream().noneMatch(supported -> supported));
    }

    @Test
    public void testPartiallySupportedCall() {
        // the engine implements Yule but has never heard of `originTime`

        Expr.Argument originTime = new Expr.AssignedArgument("originTime", new Expr.Literal(3.0));
        EngineSupport.CallSupport yule = support.supports(new Expr.Call(
                "Yule",
                new Expr.AssignedArgument("birthRate", new Expr.Literal(1.0)),
                new Expr.AssignedArgument("taxa", new Expr.Variable("data")),
                originTime));

        assertEquals(EngineSupport.Support.PARTIAL_SUPPORT, yule.support());
        assertEquals(List.of(true, true, false), yule.argumentSupport());

        // a call of an unimplemented generator is not partially supported, however familiar the
        // names of its arguments look

        EngineSupport.CallSupport logNormal =
                support.supports(new Expr.Call("LogNormal", new Expr.AssignedArgument("mean", new Expr.Literal(1.0))));
        assertEquals(EngineSupport.Support.NO_SUPPORT, logNormal.support());
    }

    @Test
    public void testPartiallySupportedModel() {
        // Yule is implemented and the nested LogNormal is not

        EngineSupport.ModelSupport model =
                support.supports(parse("Tree tree ~ Yule(birthRate~LogNormal(mean=0.2, logSd=1.0), taxa=taxa(data))"));

        assertEquals(EngineSupport.Support.PARTIAL_SUPPORT, model.support());
        assertEquals(3, model.callSupport().size());
        assertEquals(
                1,
                model.callSupport().stream()
                        .filter(call -> call.support() == EngineSupport.Support.NO_SUPPORT)
                        .count());
    }

    /* unclaimed */

    @Test
    public void testUnclaimedSupportsEverything() {
        EngineSupport unclaimed = EngineSupport.of();

        assertTrue(unclaimed
                .supports(getOverloadWithArgument("PhyloCTMC", "siteQMatrices"))
                .isFullySupported());
        assertTrue(unclaimed
                .supports(new Expr.Call("LogNormal", new Expr.AssignedArgument("mean", new Expr.Literal(1.0))))
                .isFullySupported());
        assertTrue(unclaimed
                .supports(parse("Real x ~ LogNormal(mean=0.2, logSd=1.0)"))
                .isFullySupported());
    }

    /* helper functions */

    private static List<Stmt> parse(String source) {
        return new Parser(new Lexer(source).scanTokens()).parse();
    }

    private static Generator getOverloadWithArgument(String generatorName, String argumentName) {
        return componentResolver.resolveGenerator(generatorName).stream()
                .filter(overload -> getArgumentNames(overload).contains(argumentName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "There is no overload of '" + generatorName + "' with an argument '" + argumentName + "'."));
    }

    private static List<String> getArgumentNames(Generator generator) {
        return generator.getArguments().stream().map(Argument::getName).toList();
    }

    private static Argument__1 engineArgument(String name, boolean required) {
        Argument__1 argument = new Argument__1();
        argument.setName(name);
        argument.setRequired(required);
        argument.setCanBeStochastic(false);
        return argument;
    }
}
