package org.phylospec.engines;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
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

    private static final String SPECIFICATION_RESOURCE = "/test-engine-specification.json";

    private static EngineSupport support;
    private static ComponentResolver componentResolver;

    @BeforeAll
    public static void loadEngineAndComponents() throws IOException {
        try (InputStream specificationStream = EngineSupportTest.class.getResourceAsStream(SPECIFICATION_RESOURCE)) {
            EngineSpecificationSchema engine =
                    new ObjectMapper().readValue(specificationStream, EngineSpecificationSchema.class);
            support = new EngineSupport(engine);
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

        assertFalse(new EngineSupport(engine).supports(jc69).isFullySupported());
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
        assertFalse(new EngineSupport(engine).supports(jc69).isFullySupported());
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

        // LogNormal needs two required arguments, so a single unnamed argument is not supported

        assertEquals(
                EngineSupport.Support.NO_SUPPORT,
                support.supports(new Expr.Call("LogNormal", new Expr.AssignedArgument(new Expr.Literal(1.0))))
                        .support());
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

    /* several engines */

    @Test
    public void testEnginesAreTakenTogether() {
        // neither engine implements both generators, but between them they cover the model

        EngineSupport engines = new EngineSupport(List.of(
                engine("treeEngine", engineGenerator("Yule", null, "birthRate", "taxa")),
                engine("distributionEngine", engineGenerator("LogNormal", null, "meanlog", "sdlog"))));

        assertTrue(engines.supports(parse("""
                Real rate ~ LogNormal(meanlog=0.2, sdlog=1.0)
                Tree tree ~ Yule(birthRate=rate, taxa=taxa)
                """)).isFullySupported());
    }

    @Test
    public void testOneEngineHasToTakeTheWholeCall() {
        // a call is run by a single engine, so two engines that each offer one of the arguments do
        // not add up to an engine that offers both

        EngineSupport engines = new EngineSupport(List.of(
                engine("birthRateEngine", engineGenerator("Yule", null, "birthRate")),
                engine("taxaEngine", engineGenerator("Yule", null, "taxa"))));

        EngineSupport.CallSupport yule = engines.supports(new Expr.Call(
                "Yule",
                new Expr.AssignedArgument("birthRate", new Expr.Literal(1.0)),
                new Expr.AssignedArgument("taxa", new Expr.Variable("data"))));

        assertFalse(yule.isFullySupported());

        // both arguments are offered by some engine, so neither of them is to blame on its own

        assertEquals(EngineSupport.Support.PARTIAL_SUPPORT, yule.support());
        assertEquals(List.of(true, true), yule.argumentSupport());
    }

    @Test
    public void testOverloadsOfTheSameEngine() {
        // an engine may list a generator twice to declare two shapes it takes

        EngineSupport engine = new EngineSupport(engine(
                "overloadingEngine",
                engineGenerator("PhyloCTMC", null, "tree", "qMatrix", "siteRates", "branchRates"),
                engineGenerator("PhyloCTMC", null, "tree", "siteQMatrices", "siteRates", "branchRates")));

        assertTrue(
                engine.supports(getOverloadWithArgument("PhyloCTMC", "qMatrix")).isFullySupported());
        assertTrue(engine.supports(getOverloadWithArgument("PhyloCTMC", "siteQMatrices"))
                .isFullySupported());
    }

    /* namespaces */

    @Test
    public void testMatchingNamespaceIsCovered() {
        Generator jc69 = componentResolver.resolveGenerator("jc69").getFirst();

        EngineSupport engine =
                new EngineSupport(engine("namespacedEngine", engineGenerator("jc69", jc69.getNamespace())));

        assertTrue(engine.supports(jc69).isFullySupported());
    }

    @Test
    public void testNamespaceIsIgnoredWhereOnlyOneSideNamesIt() {
        // an engine specification generally leaves the namespace out, so a namespace only tells the
        // two apart where both sides give one

        EngineSupport namespaced =
                new EngineSupport(engine("namespacedEngine", engineGenerator("exp", "some.other.library", "x")));

        assertTrue(namespaced
                .supports(new Expr.Call("exp", new Expr.AssignedArgument("x", new Expr.Literal(1.0))))
                .isFullySupported());

        // and the other way around: the engine names no namespace, the call does

        assertTrue(support.supports(
                        new Expr.Call("some.other.library.exp", new Expr.AssignedArgument("x", new Expr.Literal(1.0))))
                .isFullySupported());
    }

    /* things without parts */

    @Test
    public void testGeneratorWithoutArguments() {
        EngineSupport.GeneratorSupport jc69 =
                support.supports(componentResolver.resolveGenerator("jc69").getFirst());

        assertTrue(jc69.isFullySupported());
        assertEquals(EngineSupport.Support.FULL_SUPPORT, jc69.support());
        assertTrue(jc69.argumentSupport().isEmpty());
    }

    @Test
    public void testUnimplementedGeneratorWithoutArguments() {
        // there is no argument to offer, so an engine that does not implement it offers nothing

        EngineSupport engine = new EngineSupport(engine("emptyEngine", engineGenerator("somethingElse", null)));

        EngineSupport.CallSupport jc69 = engine.supports(new Expr.Call("jc69"));

        assertFalse(jc69.isFullySupported());
        assertEquals(EngineSupport.Support.NO_SUPPORT, jc69.support());
        assertTrue(jc69.argumentSupport().isEmpty());
    }

    @Test
    public void testModelWithoutCalls() {
        EngineSupport.ModelSupport empty = support.supports(List.of());

        assertTrue(empty.isFullySupported());
        assertEquals(EngineSupport.Support.FULL_SUPPORT, empty.support());
        assertTrue(empty.callSupport().isEmpty());
    }

    /* order and identity */

    @Test
    public void testArgumentSupportKeepsTheDeclaredOrder() {
        Generator phyloCtmc = getOverloadWithArgument("PhyloCTMC", "siteQMatrices");

        EngineSupport.GeneratorSupport siteQMatrices = support.supports(phyloCtmc);

        assertEquals(
                getArgumentNames(phyloCtmc),
                List.copyOf(siteQMatrices.argumentSupport().keySet()));
    }

    @Test
    public void testEqualCallsAreReportedSeparately() {
        // two calls of a model can be equal to each other, so they are kept in a list

        EngineSupport.ModelSupport model = support.supports(parse("""
                QMatrix first = jc69()
                QMatrix second = jc69()
                """));

        assertEquals(2, model.callSupport().size());
        assertTrue(model.isFullySupported());
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

    private static EngineSpecificationSchema engine(String name, Generator__1... generators) {
        EngineSpecificationSchema engine = new EngineSpecificationSchema();
        engine.setName(name);
        engine.setEngineVersion("1.0.0");
        engine.setGenerators(List.of(generators));
        return engine;
    }

    private static Generator__1 engineGenerator(String name, String namespace, String... argumentNames) {
        Generator__1 generator = new Generator__1();
        generator.setName(name);
        generator.setNamespace(namespace);
        generator.setArguments(Arrays.stream(argumentNames)
                .map(argument -> engineArgument(argument, true))
                .toList());
        return generator;
    }
}
