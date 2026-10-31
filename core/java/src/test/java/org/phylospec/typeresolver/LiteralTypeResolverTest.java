package org.phylospec.typeresolver;

import static org.junit.jupiter.api.Assertions.*;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.phylospec.ast.Expr;
import org.phylospec.components.ComponentResolver;

class LiteralTypeResolverTest {
    private static Stream<Number> integerZeros() {
        return Stream.<Number>of(0, 0L);
    }

    private static Stream<Number> negativeIntegers() {
        return Stream.<Number>of(-1, -1L);
    }

    private static Stream<Number> floatingZeros() {
        return Stream.<Number>of(0.0f, -0.0f, 0.0d, -0.0d);
    }

    @ParameterizedTest
    @MethodSource("integerZeros")
    void integerZeroIsNonNegativeButNotPositive(Number value) throws Exception {
        ComponentResolver components = new ComponentResolver(ComponentResolver.loadCoreComponentLibraries());
        ResolvedTypeSet types = new TypeResolver(components).visitLiteral(new Expr.Literal(value));

        assertTrue(assignableTo(types, "NonNegativeInteger", components));
        assertTrue(assignableTo(types, "Integer", components));
        assertTrue(assignableTo(types, "NonNegativeReal", components));
        assertTrue(assignableTo(types, "Probability", components));
        assertFalse(assignableTo(types, "PositiveInteger", components));
        assertFalse(assignableTo(types, "PositiveReal", components));
    }

    @ParameterizedTest
    @MethodSource("negativeIntegers")
    void negativeIntegerIsNotNonNegative(Number value) throws Exception {
        ComponentResolver components = new ComponentResolver(ComponentResolver.loadCoreComponentLibraries());
        ResolvedTypeSet types = new TypeResolver(components).visitLiteral(new Expr.Literal(value));

        assertTrue(assignableTo(types, "Integer", components));
        assertFalse(assignableTo(types, "NonNegativeInteger", components));
        assertFalse(assignableTo(types, "PositiveInteger", components));
    }

    @ParameterizedTest
    @MethodSource("floatingZeros")
    void floatingZeroDoesNotBecomeAnInteger(Number value) throws Exception {
        ComponentResolver components = new ComponentResolver(ComponentResolver.loadCoreComponentLibraries());
        ResolvedTypeSet types = new TypeResolver(components).visitLiteral(new Expr.Literal(value));

        assertTrue(assignableTo(types, "NonNegativeReal", components));
        assertTrue(assignableTo(types, "Probability", components));
        assertFalse(assignableTo(types, "Integer", components));
        assertFalse(assignableTo(types, "NonNegativeInteger", components));
        assertFalse(assignableTo(types, "PositiveReal", components));
    }

    private static boolean assignableTo(ResolvedTypeSet types, String target, ComponentResolver components) {
        return TypeUtils.canBeAssignedTo(types, ResolvedType.fromString(target, components), components);
    }
}
