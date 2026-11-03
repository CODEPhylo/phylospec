package org.phylospec.typeresolver;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.phylospec.ast.Stmt;
import org.phylospec.ast.transformers.EvaluateLiterals;
import org.phylospec.ast.transformers.RemoveGroupings;
import org.phylospec.components.Argument;
import org.phylospec.components.ComponentResolver;
import org.phylospec.components.Generator;
import org.phylospec.lexer.Lexer;
import org.phylospec.parser.Parser;

class GeneratorTypeInferenceTest {

    @ParameterizedTest
    @ValueSource(strings = {"Integer", "Real", "PositiveInteger", "PositiveReal"})
    void repeatRetainsAlternativeNumericInterpretations(String type) throws Exception {
        ComponentResolver components = components();
        ResolvedTypeSet result = resolveExpression("Vector<" + type + "> values = repeat(value=2,num=3)", components);
        Set<String> elementNames = result.stream()
                .map(value -> value.getParameterTypes().get("T").getName())
                .collect(Collectors.toSet());
        assertEquals(Set.of("phylospec.types.PositiveInteger", "phylospec.types.PositiveReal"), elementNames);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Integer", "Real"})
    void namedValuesKeepTheirDeclaredFamily(String type) throws Exception {
        ComponentResolver components = components();
        ResolvedTypeSet result = resolveExpression(
                type + " value = 2\nVector<" + type + "> values = repeat(value=value,num=3)", components);
        assertTrue(TypeUtils.canBeAssignedTo(
                result, ResolvedType.fromString("Vector<" + type + ">", components), components));
        String other = type.equals("Integer") ? "Real" : "Integer";
        assertFalse(TypeUtils.canBeAssignedTo(
                result, ResolvedType.fromString("Vector<" + other + ">", components), components));
    }

    @Test
    void repeatStillSupportsNonNumericTypes() throws Exception {
        ComponentResolver components = components();
        ResolvedTypeSet result = resolveExpression("Vector<Boolean> values = repeat(value=true,num=3)", components);
        assertTrue(
                TypeUtils.canBeAssignedTo(result, ResolvedType.fromString("Vector<Boolean>", components), components));
    }

    @Test
    void alternativesFromDifferentArgumentsAreCombinedRatherThanFlattened() throws Exception {
        ComponentResolver components = components();
        Generator generator = generator("Vector<T>", argument("a", "T"), argument("b", "T"));
        ResolvedTypeSet alternatives = types(components, "PositiveInteger", "PositiveReal");
        var result =
                TypeUtils.resolveGeneratedType(generator, Map.of("a", alternatives, "b", alternatives), components);
        assertEquals(
                Set.of("phylospec.types.PositiveInteger", "phylospec.types.PositiveReal"),
                result.generatedTypeSet().stream()
                        .map(type -> type.getParameterTypes().get("T").getName())
                        .collect(Collectors.toSet()));
        assertEquals(alternatives, result.resolvedArguments().get("a"));
        assertEquals(alternatives, result.resolvedArguments().get("b"));
    }

    @Test
    void anotherArgumentCanSelectTheIntegerInterpretation() throws Exception {
        ComponentResolver components = components();
        Generator generator = generator("Vector<T>", argument("a", "T"), argument("b", "T"));
        var result = TypeUtils.resolveGeneratedType(
                generator,
                Map.of("a", types(components, "PositiveInteger", "PositiveReal"), "b", types(components, "Integer")),
                components);
        assertEquals(1, result.generatedTypeSet().size());
        assertEquals(
                "phylospec.types.Integer",
                result.generatedTypeSet()
                        .iterator()
                        .next()
                        .getParameterTypes()
                        .get("T")
                        .getName());
    }

    @Test
    void incompatibleArgumentsReportATypeErrorInsteadOfConstructingANullType() throws Exception {
        ComponentResolver components = components();
        Generator generator = generator("Vector<T>", argument("a", "T"), argument("b", "T"));
        assertThrows(
                TypeError.class,
                () -> TypeUtils.resolveGeneratedType(
                        generator,
                        Map.of("a", types(components, "Integer"), "b", types(components, "Real")),
                        components));
    }

    @Test
    void repeatedTypeParameterWithinOneInterpretationStillRequiresACommonType() throws Exception {
        ComponentResolver components = components();
        Generator generator = generator("Vector<T>", argument("value", "Map<T,T>"));
        assertThrows(
                TypeError.class,
                () -> TypeUtils.resolveGeneratedType(
                        generator, Map.of("value", types(components, "Map<Integer,Real>")), components));

        var result = TypeUtils.resolveGeneratedType(
                generator, Map.of("value", types(components, "Map<PositiveInteger,Integer>")), components);
        assertEquals(
                Set.of("phylospec.types.Integer"),
                result.generatedTypeSet().stream()
                        .map(type -> type.getParameterTypes().get("T").getName())
                        .collect(Collectors.toSet()));
    }

    private static ResolvedTypeSet resolveExpression(String source, ComponentResolver components) {
        Lexer lexer = new Lexer(source);
        lexer.registerEventListener(error -> fail(error.toStdOutString(source)));
        Parser parser = new Parser(lexer.scanTokens());
        parser.registerEventListener(error -> fail(error.toStdOutString(source)));
        var statements = new EvaluateLiterals().transform(new RemoveGroupings().transform(parser.parse()));
        TypeResolver resolver = new TypeResolver(components);
        resolver.visitStatements(statements);
        return resolver.resolveTypeSet(((Stmt.Assignment) statements.getLast()).expression);
    }

    private static ComponentResolver components() throws Exception {
        return new ComponentResolver(ComponentResolver.loadCoreComponentLibraries());
    }

    private static ResolvedTypeSet types(ComponentResolver components, String... names) {
        ResolvedTypeSet result = new ResolvedTypeSet();
        for (String name : names) result.addAll(ResolvedType.fromString(name, components));
        return result;
    }

    private static Generator generator(String resultType, Argument... arguments) {
        Generator generator = new Generator();
        generator.setName("genericTest");
        generator.setGeneratedType(resultType);
        generator.setTypeParameters(List.of("T"));
        generator.setArguments(List.of(arguments));
        return generator;
    }

    private static Argument argument(String name, String type) {
        Argument argument = new Argument();
        argument.setName(name);
        argument.setType(type);
        argument.setRequired(true);
        return argument;
    }
}
