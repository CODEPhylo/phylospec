package org.phylospec.engines;

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
            assertTrue(support.test(overload), "Yule with " + getArgumentNames(overload));
        }
    }

    @Test
    public void testArgumentOrderIsIgnored() {
        // the component library declares `siteRates` before `branchRates`, the engine the other
        // way around. calls name their arguments, so the order carries no meaning

        Generator phyloCtmc = getOverloadWithArgument("PhyloCTMC", "qMatrix");
        assertTrue(support.test(phyloCtmc));
    }

    @Test
    public void testUnofferedArgumentIsNotCovered() {
        // the engine offers `qMatrix` but never heard of `siteQMatrices`, so it does not implement
        // this overload

        Generator siteQMatrices = getOverloadWithArgument("PhyloCTMC", "siteQMatrices");
        assertFalse(support.test(siteQMatrices));

        // the same for the `date` overload of fromNexus, where the engine only offers `age`

        Generator fromNexusByDate = getOverloadWithArgument("fromNexus", "date");
        assertFalse(support.test(fromNexusByDate));

        Generator fromNexusByAge = getOverloadWithArgument("fromNexus", "age");
        assertTrue(support.test(fromNexusByAge));
    }

    @Test
    public void testUnimplementedGeneratorIsNotCovered() {
        for (Generator overload : componentResolver.resolveGenerator("LogNormal")) {
            assertFalse(support.test(overload));
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

        assertFalse(EngineSupport.of(engine).test(jc69));
    }

    /* calls */

    @Test
    public void testCallWithNamedArguments() {
        assertTrue(support.onCalls()
                .test(new Expr.Call(
                        "Yule",
                        new Expr.AssignedArgument("birthRate", new Expr.Literal(1.0)),
                        new Expr.AssignedArgument("taxa", new Expr.Variable("data")))));

        assertTrue(support.onCalls()
                .test(new Expr.Call(
                        "Yule",
                        new Expr.AssignedArgument("birthRate", new Expr.Literal(1.0)),
                        new Expr.AssignedArgument("rootAge", new Expr.Literal(2.0)),
                        new Expr.AssignedArgument("taxa", new Expr.Variable("data")))));
    }

    @Test
    public void testCallWithQualifiedName() {
        assertTrue(support.onCalls()
                .test(new Expr.Call(
                        "phylospec.distributions.tree.Yule",
                        new Expr.AssignedArgument("birthRate", new Expr.Literal(1.0)),
                        new Expr.AssignedArgument("taxa", new Expr.Variable("data")))));
    }

    @Test
    public void testCallWithUnnamedArguments() {
        // `tree` is passed positionally and `qMatrix` by the name of the variable

        assertTrue(support.onCalls()
                .test(new Expr.Call(
                        "PhyloCTMC",
                        new Expr.AssignedArgument(new Expr.Literal(1.0)),
                        new Expr.AssignedArgument(new Expr.Variable("qMatrix")))));
    }

    @Test
    public void testCallWithSingleUnnamedVariable() {
        // `exp(mean)` is ambiguous: `mean` either names the argument or fills the first one. the
        // engine calls the argument `x`, so only the second reading works

        assertTrue(support.onCalls().test(new Expr.Call("exp", new Expr.AssignedArgument(new Expr.Variable("mean")))));

        // and the reading by name works as well

        assertTrue(support.onCalls().test(new Expr.Call("exp", new Expr.AssignedArgument(new Expr.Variable("x")))));
    }

    @Test
    public void testCallPassingAnUnofferedArgument() {
        assertFalse(support.onCalls()
                .test(new Expr.Call(
                        "Yule",
                        new Expr.AssignedArgument("birthRate", new Expr.Literal(1.0)),
                        new Expr.AssignedArgument("taxa", new Expr.Variable("data")),
                        new Expr.AssignedArgument("originTime", new Expr.Literal(3.0)))));
    }

    @Test
    public void testCallMissingARequiredArgument() {
        assertFalse(support.onCalls()
                .test(new Expr.Call("Yule", new Expr.AssignedArgument("birthRate", new Expr.Literal(1.0)))));
    }

    @Test
    public void testCallOfAnUnimplementedGenerator() {
        assertFalse(support.onCalls()
                .test(new Expr.Call("LogNormal", new Expr.AssignedArgument("mean", new Expr.Literal(1.0)))));
    }

    /* statements */

    @Test
    public void testStatementWithoutCalls() {
        assertTrue(
                support.onStatements().test(parse("use phylospec.distributions").getFirst()));
    }

    @Test
    public void testStatementWithNestedCalls() {
        // the nested LogNormal is not implemented, so the whole statement is not supported

        Stmt unsupported = parse("Tree tree ~ Yule(birthRate~LogNormal(mean=0.2, logSd=1.0), taxa=taxa(data))")
                .getFirst();
        assertFalse(support.onStatements().test(unsupported));

        Stmt supported =
                parse("Tree tree ~ Yule(birthRate=0.2, taxa=taxa(data))").getFirst();
        assertTrue(support.onStatements().test(supported));
    }

    @Test
    public void testModelIsEveryStatement() {
        List<Stmt> model = parse("""
                Alignment data = fromNexus("primates.nex")
                QMatrix qMatrix = jc69()
                Alignment alignment ~ PhyloCTMC(tree, qMatrix) observed as data
                """);

        assertTrue(model.stream().allMatch(support.onStatements()));
    }

    /* unclaimed */

    @Test
    public void testUnclaimedSupportsEverything() {
        EngineSupport unclaimed = EngineSupport.unclaimed();

        assertTrue(unclaimed.test(getOverloadWithArgument("PhyloCTMC", "siteQMatrices")));
        assertTrue(unclaimed
                .onCalls()
                .test(new Expr.Call("LogNormal", new Expr.AssignedArgument("mean", new Expr.Literal(1.0)))));
        assertTrue(unclaimed
                .onStatements()
                .test(parse("Real x ~ LogNormal(mean=0.2, logSd=1.0)").getFirst()));
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
