package org.phylospec.typeresolver.properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.phylospec.typeresolver.properties.TypePropertyNames.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.phylospec.ast.Stmt;
import org.phylospec.components.ComponentResolver;
import org.phylospec.errors.Error;
import org.phylospec.errors.ErrorEventListener;
import org.phylospec.lexer.Lexer;
import org.phylospec.parser.Parser;
import org.phylospec.typeresolver.ResolvedType;
import org.phylospec.typeresolver.ResolvedTypeSet;
import org.phylospec.typeresolver.TypeError;
import org.phylospec.typeresolver.TypeResolver;

class TypePropertyAliasTest {

    @Test
    void drawnVectorLengthSurvivesAliasAndCopyAndFeedsAccessorAndIndexCheck() throws IOException {
        Resolution result = resolve("""
                Vector<Rate> rates ~ DiscreteGammaInv(shape=1.0, numCategories=4, numSites=7)
                Vector<PositiveReal> numericRates ~ DiscreteGammaInv(shape=1.0, numCategories=4, numSites=7)
                Vector<Rate> copied = numericRates
                NonNegativeInteger length = num(rates)
                Rate outside = rates[8]
                """);

        assertRepresentations(result.types("rates"), "Vector<Rate>", "Vector<PositiveReal>");
        assertProperty(result.types("rates"), NUM, 7);
        assertProperty(result.types("numericRates"), NUM, 7);
        assertProperty(result.types("copied"), NUM, 7);
        assertProperty(result.types("length"), VALUE, 7);
        assertEquals(1, result.warnings().size());
        assertEquals(
                "The index might be out of range.", result.warnings().getFirst().description());
        assertEquals(
                "Use an index between 1 and 7.", result.warnings().getFirst().hint());
    }

    @Test
    void vectorLiteralAndAliasToNumericCopyRetainLength() throws IOException {
        Resolution result = resolve("""
                Vector<Rate> rates = [1.5, 2.5, 3.5]
                Vector<PositiveReal> numeric = rates
                Vector<Rate> copied = rates
                """);

        assertRepresentations(result.types("rates"), "Vector<Rate>", "Vector<PositiveReal>");
        for (String variable : List.of("rates", "numeric", "copied")) {
            assertProperty(result.types(variable), NUM, 3);
        }
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void scalarAliasesRetainKnownValuesInEveryRepresentation() throws IOException {
        Resolution result = resolve("""
                Rate rate = 3.5
                Age age = 0.0
                PositiveReal numericRate = rate
                NonNegativeReal numericAge = age
                """);

        assertRepresentations(result.types("rate"), "Rate", "PositiveReal");
        assertRepresentations(result.types("age"), "Age", "NonNegativeReal");
        assertProperty(result.types("rate"), VALUE, 3.5);
        assertProperty(result.types("numericRate"), VALUE, 3.5);
        assertProperty(result.types("age"), VALUE, 0);
        assertProperty(result.types("numericAge"), VALUE, 0);
    }

    @Test
    void nestedVectorAliasesRetainBothKnownLengthsWithoutAttachingLengthToScalars() throws IOException {
        Resolution result = resolve("""
                Vector<Vector<Rate>> rates = [[1.5, 2.5, 3.5], [4.5, 5.5, 6.5]]
                """);

        assertProperty(result.types("rates"), NUM, 2);
        for (ResolvedType type : result.types("rates")) {
            ResolvedType row = type.getParameterTypes().get("T");
            assertNumber(3, row.properties().get(NUM), row.toString());
            assertNull(row.getParameterTypes().get("T").properties().get(NUM));
        }
    }

    @Test
    void matrixAliasCopiesPropertiesOnlyBetweenCorrespondingStructuralLayers() throws IOException {
        Resolution result = resolve("""
                PositiveReal numeric[i, j] = 1.5 for i in 1:2 for j in 1:3
                Matrix<Rate> rates = numeric
                """);

        assertRepresentations(
                result.types("rates"),
                "Matrix<Rate>",
                "Matrix<PositiveReal>",
                "Vector<Vector<Rate>>",
                "Vector<Vector<PositiveReal>>");
        assertProperty(result.types("rates"), NUM_ROWS, 2);
        assertProperty(result.types("rates"), NUM_COLS, 3);
        for (ResolvedType type : result.types("rates")) {
            ResolvedType leaf = type;
            while (!leaf.getParameterTypes().isEmpty()) {
                leaf = leaf.getParameterTypes().get("T");
            }
            assertNull(leaf.properties().get(NUM), type.toString());
            assertNull(leaf.properties().get(NUM_ROWS), type.toString());
            assertNull(leaf.properties().get(NUM_COLS), type.toString());
            assertNumber(1.5, leaf.properties().get(VALUE), type.toString());
        }
    }

    @Test
    void unknownAndDisagreeingLengthsRemainUnknown() throws IOException {
        Resolution result = resolve("""
                NonNegativeInteger sites ~ Poisson(rate=4.0)
                Vector<Rate> unknown ~ DiscreteGammaInv(shape=1.0, numCategories=4, numSites=sites)
                Vector<Vector<Rate>> ragged = [[1.5, 2.5], [3.5, 4.5, 5.5]]
                """);

        for (ResolvedType type : result.types("unknown")) {
            assertNull(type.properties().get(NUM), type.toString());
        }
        assertProperty(result.types("ragged"), NUM, 2);
        for (ResolvedType type : result.types("ragged")) {
            assertNull(type.getParameterTypes().get("T").properties().get(NUM), type.toString());
        }
    }

    @Test
    void propertyPropagationDoesNotRelaxExistingNumericDomains() {
        for (String script : List.of(
                "Rate rate = 0.0",
                "Rate rate = -1.0",
                "Age age = -1.0",
                "Vector<Rate> rates = [1.5, 0.0]",
                "Real broad = 3.5\nRate rate = broad",
                "Vector<Real> broad = [1.5, 2.5]\nVector<Rate> rates = broad")) {
            assertThrows(TypeError.class, () -> resolve(script), script);
        }
    }

    private static Resolution resolve(String source) throws IOException {
        List<Error> errors = new ArrayList<>();
        List<Error> warnings = new ArrayList<>();
        Lexer lexer = new Lexer(source);
        lexer.registerEventListener(errors::add);
        Parser parser = new Parser(lexer.scanTokens());
        parser.registerEventListener(errors::add);
        List<Stmt> statements = parser.parse();
        assertTrue(errors.isEmpty(), errors.toString());

        TypeResolver resolver = new TypeResolver(new ComponentResolver(ComponentResolver.loadCoreComponentLibraries()));
        resolver.registerEventListener(new ErrorEventListener() {
            @Override
            public void errorDetected(Error error) {
                errors.add(error);
            }

            @Override
            public void warningDetected(Error warning) {
                warnings.add(warning);
            }
        });
        for (Stmt statement : statements) {
            statement.accept(resolver);
        }
        assertTrue(errors.isEmpty(), errors.toString());
        return new Resolution(resolver, warnings);
    }

    private static void assertRepresentations(ResolvedTypeSet types, String... expected) {
        assertEquals(
                List.of(expected).stream().sorted().toList(),
                types.stream().map(ResolvedType::toString).sorted().toList());
    }

    private static void assertProperty(ResolvedTypeSet types, String property, double expected) {
        assertFalse(types.isEmpty());
        for (ResolvedType type : types) {
            assertNumber(expected, type.properties().get(property), type.toString() + "." + property);
        }
        assertNumber(expected, TypePropertyEngine.getPropertyOnAgreement(types, property), "All representations agree");
    }

    private static void assertNumber(double expected, Object actual, String message) {
        Number value = assertInstanceOf(Number.class, actual, message);
        assertEquals(expected, value.doubleValue(), message);
    }

    private record Resolution(TypeResolver resolver, List<Error> warnings) {
        ResolvedTypeSet types(String variable) {
            return resolver.resolveVariable(variable);
        }
    }
}
