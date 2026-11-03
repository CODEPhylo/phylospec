package tiles.misc;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.spec.type.IntScalar;
import beast.base.spec.type.RealScalar;
import beastconfig.BEASTState;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.phylospec.ast.AstType;
import org.phylospec.ast.Expr;
import org.phylospec.ast.Stmt;
import org.phylospec.components.ComponentLibrary;
import org.phylospec.components.ComponentResolver;
import org.phylospec.components.Type;
import org.phylospec.lexer.Lexer;
import org.phylospec.parser.Parser;
import org.phylospec.tiling.EvaluateTiles;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.errors.FailedTilingAttempt;
import org.phylospec.tiling.tiles.Tile;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;
import tiles.BeastCoreTileLibrary;

class AssignmentNumericTypeTest {

    @ParameterizedTest
    @CsvSource({"Integer, true", "Real, false", "Count, true", "Rate, false", "Age, false"})
    void loggerRetainsDeclaredScalarFamilyForIntegerLiterals(String declaration, boolean integer) throws Exception {
        assertScalarFamily(logged(declaration + " value = 2", coreComponents()), integer);
    }

    @ParameterizedTest
    @CsvSource({"phylospec.types.Integer, true", "phylospec.types.Real, false"})
    void qualifiedAstTypesRetainDeclaredScalarFamily(String declaration, boolean integer) throws Exception {
        String shortName = declaration.substring(declaration.lastIndexOf('.') + 1);
        List<Stmt> statements = parseLogged(shortName + " value = 2");
        ((Stmt.Assignment) statements.getFirst()).type.name = declaration;
        assertScalarFamily(logged(statements, coreComponents()), integer);
    }

    @ParameterizedTest
    @CsvSource({"Integer, true", "Real, false", "Count, true", "Rate, false"})
    void variableAliasesRetainTheirDeclarationFamily(String declaration, boolean integer) throws Exception {
        assertScalarFamily(
                logged(declaration + " original = 2\n" + declaration + " value = original", coreComponents()), integer);
    }

    @ParameterizedTest
    @CsvSource({"CountAliasAgain, true", "RealAlias, false"})
    void customTypeAliasesUseTheActiveComponentResolver(String declaration, boolean integer) throws Exception {
        assertScalarFamily(logged("use test.numeric\n" + declaration + " value = 2", customComponents()), integer);
    }

    @Test
    void resolvedNamesIncludeAliasAndInheritanceChainsWithoutEnteringSerialization() throws Exception {
        AstType type = new AstType.Atomic("test.numeric.CountAliasAgain");
        ObjectMapper mapper = new ObjectMapper();
        String originalJson = mapper.writeValueAsString(type);

        type.accept(new TypeResolver(customComponents()));

        assertEquals(
                Set.of(
                        "test.numeric.CountAliasAgain",
                        "test.numeric.CountAlias",
                        "phylospec.types.Count",
                        "phylospec.types.NonNegativeInteger",
                        "phylospec.types.Integer"),
                type.getResolvedTypeNames());
        assertEquals(originalJson, mapper.writeValueAsString(type));
        assertThrows(UnsupportedOperationException.class, () -> type.getResolvedTypeNames()
                .add("extra"));

        Set<String> names = new HashSet<>(Set.of("phylospec.types.Real"));
        type.setResolvedTypeNames(names);
        names.clear();
        assertEquals(Set.of("phylospec.types.Real"), type.getResolvedTypeNames());
    }

    @Test
    void unresolvedGenericDeclarationsDoNotForceParentParametersToResolve() throws Exception {
        AstType type = new AstType.Atomic("SquareMatrix");
        type.accept(new TypeResolver(coreComponents()));
        assertEquals(Set.of("phylospec.types.SquareMatrix"), type.getResolvedTypeNames());
    }

    @Test
    void nestedUnresolvedGenericsDoNotForceParentParametersToResolve() throws Exception {
        for (AstType type : List.of(
                new AstType.Generic("Vector", new AstType.Atomic("SquareMatrix")),
                new AstType.Generic("Vector", new AstType.Generic("Vector", new AstType.Atomic("SquareMatrix"))))) {
            type.accept(new TypeResolver(coreComponents()));
            assertEquals(Set.of("phylospec.types.Vector"), type.getResolvedTypeNames());
        }

        AstType matrix = new AstType.Generic("Matrix", new AstType.Atomic("Vector"));
        matrix.accept(new TypeResolver(coreComponents()));
        assertEquals(Set.of("phylospec.types.Matrix", "phylospec.types.Vector"), matrix.getResolvedTypeNames());
    }

    @Test
    void vectorElementFamiliesAreNotScalarDeclarationFamilies() throws Exception {
        AstType type = new AstType.Generic("Vector", new AstType.Atomic("Integer"));
        type.accept(new TypeResolver(coreComponents()));
        assertEquals(Set.of("phylospec.types.Vector"), type.getResolvedTypeNames());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Integer", "Real"})
    void boxedLiteralRepresentationsRemainAvailable(String declaration) throws Exception, FailedTilingAttempt {
        AstType type = new AstType.Atomic(declaration);
        type.accept(new TypeResolver(coreComponents()));
        Set<Tile<?, BEASTState>> candidates = literalAssignments(type);

        assertTrue(candidates.stream().anyMatch(tile -> tile.getTypeToken().equals(TypeToken.of(Integer.class))));
        assertTrue(candidates.stream().anyMatch(tile -> tile.getTypeToken().equals(TypeToken.of(Double.class))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Integer", "Real"})
    void incompatibleNativeScalarReportsTheDeclaredType(String declaration) throws Exception, FailedTilingAttempt {
        AstType type = new AstType.Atomic(declaration);
        type.accept(new TypeResolver(coreComponents()));
        Expr.Literal literal = new Expr.Literal(2);
        Stmt.Assignment statement = new Stmt.Assignment(type, "value", literal);
        VariableResolver variables = new VariableResolver(List.of(statement));
        StochasticityResolver stochasticity = new StochasticityResolver();
        stochasticity.visitStatements(List.of(statement));
        Set<Tile<?, BEASTState>> literals = new LiteralTile<>().tryToTile(literal, Map.of(), variables, stochasticity);
        TypeToken<?> oppositeFamily =
                declaration.equals("Integer") ? TypeToken.of(RealScalar.class) : TypeToken.of(IntScalar.class);
        literals.removeIf(tile -> !oppositeFamily.isAssignableFrom(tile.getTypeToken()));
        assertFalse(literals.isEmpty());

        FailedTilingAttempt.RejectedBoundary error =
                assertThrows(FailedTilingAttempt.RejectedBoundary.class, () -> new AssignmentTile()
                        .tryToTile(statement, Map.of(literal, literals), variables, stochasticity));
        assertTrue(error.getReason().contains("declared numeric type '" + declaration + "'"));
    }

    @Test
    void missingOrNonScalarMetadataDoesNotFilterNativeFamilies() throws FailedTilingAttempt {
        for (Set<String> names : List.of(
                Set.<String>of(),
                Set.of("test.types.Any"),
                Set.of("phylospec.types.Integer", "phylospec.types.Real"))) {
            AstType type = new AstType.Atomic("Any");
            type.setResolvedTypeNames(names);
            Set<Tile<?, BEASTState>> candidates = literalAssignments(type);
            assertTrue(candidates.stream()
                    .anyMatch(tile -> TypeToken.of(IntScalar.class).isAssignableFrom(tile.getTypeToken())));
            assertTrue(candidates.stream()
                    .anyMatch(tile -> TypeToken.of(RealScalar.class).isAssignableFrom(tile.getTypeToken())));
        }
    }

    private static Set<Tile<?, BEASTState>> literalAssignments(AstType type) throws FailedTilingAttempt {
        Expr.Literal literal = new Expr.Literal(2);
        Stmt.Assignment statement = new Stmt.Assignment(type, "value", literal);
        VariableResolver variables = new VariableResolver(List.of(statement));
        StochasticityResolver stochasticity = new StochasticityResolver();
        stochasticity.visitStatements(List.of(statement));
        Set<Tile<?, BEASTState>> literals = new LiteralTile<>().tryToTile(literal, Map.of(), variables, stochasticity);
        return new AssignmentTile().tryToTile(statement, Map.of(literal, literals), variables, stochasticity);
    }

    private static void assertScalarFamily(Object value, boolean integer) {
        if (integer) {
            assertEquals(2, assertInstanceOf(IntScalar.class, value).get());
        } else {
            assertEquals(2.0, assertInstanceOf(RealScalar.class, value).get(), 0.0);
        }
    }

    private static Object logged(String declarations, ComponentResolver components) throws Exception {
        return logged(parseLogged(declarations), components);
    }

    private static List<Stmt> parseLogged(String declarations) {
        String source = declarations + """

                mcmc {
                    Logger trace = fileLogger(logEvery=1, file="assignment.log", parameters=[value])
                }
                """;
        var lexer = new Lexer(source);
        lexer.registerEventListener(error -> fail(error.toStdOutString(source)));
        var parser = new Parser(lexer.scanTokens());
        parser.registerEventListener(error -> fail(error.toStdOutString(source)));
        return parser.parse();
    }

    private static Object logged(List<Stmt> statements, ComponentResolver components) throws Exception {
        new TypeResolver(components).visitStatements(statements);
        var stochasticity = new StochasticityResolver();
        stochasticity.visitStatements(statements);
        var evaluator = new EvaluateTiles<>(
                new BeastCoreTileLibrary().getTiles(), new VariableResolver(statements), stochasticity);
        evaluator.getBestTiling(statements);
        BEASTState graph = evaluator.applyBestTiling(new BEASTState("assignment-numeric-test"));
        graph.initializeBEASTObjects();
        assertTrue(graph.stateNodes.isEmpty());
        assertTrue(graph.priorDistributions.isEmpty());
        assertTrue(graph.operators.isEmpty());
        return graph.fileLoggerSpecs.getFirst().parameters().getFirst();
    }

    private static ComponentResolver coreComponents() throws Exception {
        return new ComponentResolver(ComponentResolver.loadCoreComponentLibraries());
    }

    private static ComponentResolver customComponents() throws Exception {
        List<ComponentLibrary> libraries = new ArrayList<>(ComponentResolver.loadCoreComponentLibraries());
        ComponentLibrary aliases = new ComponentLibrary();
        aliases.setTypes(List.of(
                alias("CountAlias", "phylospec.types.Count"),
                alias("CountAliasAgain", "CountAlias"),
                alias("RealAlias", "phylospec.types.Real")));
        libraries.add(aliases);
        return new ComponentResolver(libraries);
    }

    private static Type alias(String name, String target) {
        Type type = new Type();
        type.setName(name);
        type.setNamespace("test.numeric");
        type.setAlias(target);
        return type;
    }
}
