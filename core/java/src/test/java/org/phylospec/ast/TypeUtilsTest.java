package org.phylospec.ast;

import org.junit.jupiter.api.Test;
import org.phylospec.components.ComponentResolver;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class TypeUtilsTest {
    @Test
    public void testTypeIntersectionOfAtomicTypes() throws IOException {
        ComponentResolver componentResolver = buildComponentResolver();

        Set<ResolvedType> actual = TypeUtils.findIntersection(
                Set.of(
                        ResolvedType.fromString("Real", componentResolver),
                        ResolvedType.fromString("Integer", componentResolver)
                ),
                Set.of(
                        ResolvedType.fromString("PositiveReal", componentResolver),
                        ResolvedType.fromString("Integer", componentResolver)
                ),
                componentResolver
        );

        Set<ResolvedType> expected = Set.of(
                ResolvedType.fromString("Integer", componentResolver),
                ResolvedType.fromString("PositiveReal", componentResolver)
        );

        assertEquals(expected, actual);
    }

    @Test
    public void testEmptyTypeIntersectionOfAtomicTypes() throws IOException {
        ComponentResolver componentResolver = buildComponentResolver();

        Set<ResolvedType> actual = TypeUtils.findIntersection(
                Set.of(
                        ResolvedType.fromString("Real", componentResolver),
                        ResolvedType.fromString("Integer", componentResolver)
                ),
                Set.of(
                        ResolvedType.fromString("PositiveReal", componentResolver)
                ),
                componentResolver
        );

        Set<ResolvedType> expected = Set.of();

        assertEquals(expected, actual);
    }


    @Test
    public void testTypeIntersectionOfGenericTypes() throws IOException {
        ComponentResolver componentResolver = buildComponentResolver();

        Set<ResolvedType> actual = TypeUtils.findIntersection(
                Set.of(
                        ResolvedType.fromString("Vector<Real>", componentResolver)
                ),
                Set.of(
                        ResolvedType.fromString("Vector<Real>", componentResolver),
                        ResolvedType.fromString("Vector<PositiveReal>", componentResolver)
                ),
                componentResolver
        );

        Set<ResolvedType> expected = Set.of(
                ResolvedType.fromString("Vector<PositiveReal>", componentResolver)
        );

        assertEquals(expected, actual);
    }

    @Test
    public void testTypeIntersectionOfGenericTypes2() throws IOException {
        ComponentResolver componentResolver = buildComponentResolver();

        Set<ResolvedType> actual = TypeUtils.findIntersection(
                Set.of(
                        ResolvedType.fromString("Vector<Vector<Real>>", componentResolver)
                ),
                Set.of(
                        ResolvedType.fromString("Vector<Simplex>", componentResolver),
                        ResolvedType.fromString("Vector<Vector<Real>>", componentResolver)
                ),
                componentResolver
        );

        Set<ResolvedType> expected = Set.of(
                ResolvedType.fromString("Vector<Simplex>", componentResolver)
        );

        assertEquals(expected, actual);
    }

    @Test
    public void testTypeUnionOfAtomicTypes() throws IOException {
        ComponentResolver componentResolver = buildComponentResolver();

        Set<ResolvedType> actual = TypeUtils.findUnion(
                Set.of(
                        ResolvedType.fromString("Real", componentResolver),
                        ResolvedType.fromString("Integer", componentResolver)
                ),
                Set.of(
                        ResolvedType.fromString("PositiveReal", componentResolver)
                ),
                componentResolver
        );

        Set<ResolvedType> expected = Set.of(
                        ResolvedType.fromString("Real", componentResolver),
                        ResolvedType.fromString("Integer", componentResolver)
        );

        assertEquals(expected, actual);
    }

    @Test
    public void testTypeUnionOfDistinctAtomicTypes() throws IOException {
        ComponentResolver componentResolver = buildComponentResolver();

        Set<ResolvedType> actual = TypeUtils.findUnion(
                Set.of(
                        ResolvedType.fromString("PositiveReal", componentResolver),
                        ResolvedType.fromString("Integer", componentResolver)
                ),
                Set.of(
                        ResolvedType.fromString("NonNegativeReal", componentResolver)
                ),
                componentResolver
        );

        Set<ResolvedType> expected = Set.of(
                ResolvedType.fromString("PositiveReal", componentResolver),
                ResolvedType.fromString("NonNegativeReal", componentResolver),
                ResolvedType.fromString("Integer", componentResolver)
        );

        assertEquals(expected, actual);
    }


    @Test
    public void testTypeUnionOfAtomicTypesWithEmptyType() throws IOException {
        ComponentResolver componentResolver = buildComponentResolver();

        Set<ResolvedType> actual = TypeUtils.findUnion(
                Set.of(
                        ResolvedType.fromString("Real", componentResolver),
                        ResolvedType.fromString("Integer", componentResolver)
                ),
                Set.of(),
                componentResolver
        );

        Set<ResolvedType> expected = Set.of(
                ResolvedType.fromString("Real", componentResolver),
                ResolvedType.fromString("Integer", componentResolver)
        );

        assertEquals(expected, actual);
    }

    @Test
    public void testTypeUnionOfAtomicTypesWithGenerics1() throws IOException {
        ComponentResolver componentResolver = buildComponentResolver();

        Set<ResolvedType> actual = TypeUtils.findUnion(
                Set.of(
                        ResolvedType.fromString("Vector<Real>", componentResolver)
                ),
                Set.of(
                        ResolvedType.fromString("Vector<NonNegativeReal>", componentResolver),
                        ResolvedType.fromString("Vector<PositiveReal>", componentResolver)
                ),
                componentResolver
        );

        Set<ResolvedType> expected = Set.of(
                ResolvedType.fromString("Vector<Real>", componentResolver)
        );

        assertEquals(expected, actual);
    }

    @Test
    public void testTypeUnionOfAtomicTypesWithGenerics2() throws IOException {
        ComponentResolver componentResolver = buildComponentResolver();

        Set<ResolvedType> actual = TypeUtils.findUnion(
                Set.of(
                        ResolvedType.fromString("Vector<Integer>", componentResolver),
                        ResolvedType.fromString("Simplex", componentResolver)
                ),
                Set.of(
                        ResolvedType.fromString("Vector<Real>", componentResolver)
                ),
                componentResolver
        );

        Set<ResolvedType> expected = Set.of(
                ResolvedType.fromString("Vector<Real>", componentResolver),
                ResolvedType.fromString("Vector<Integer>", componentResolver)
        );

        assertEquals(expected, actual);
    }

    @Test
    public void testCoversTypeWithAtomicTypes() throws IOException {
        ComponentResolver componentResolver = buildComponentResolver();

        assertTrue(TypeUtils.coversType(
                ResolvedType.fromString("Real", componentResolver),
                ResolvedType.fromString("Real", componentResolver),
                componentResolver
        ));

        assertTrue(TypeUtils.coversType(
                ResolvedType.fromString("Real", componentResolver),
                ResolvedType.fromString("PositiveReal", componentResolver),
                componentResolver
        ));

        assertFalse(TypeUtils.coversType(
                ResolvedType.fromString("PositiveReal", componentResolver),
                ResolvedType.fromString("Real", componentResolver),
                componentResolver
        ));

        assertFalse(TypeUtils.coversType(
                ResolvedType.fromString("Integer", componentResolver),
                ResolvedType.fromString("Real", componentResolver),
                componentResolver
        ));
    }

    @Test
    public void testCoversTypeWithGenericTypes() throws IOException {
        ComponentResolver componentResolver = buildComponentResolver();

        assertTrue(TypeUtils.coversType(
                ResolvedType.fromString("Vector<Real>", componentResolver),
                ResolvedType.fromString("Vector<PositiveReal>", componentResolver),
                componentResolver
        ));

        assertTrue(TypeUtils.coversType(
                ResolvedType.fromString("Vector<Real>", componentResolver),
                ResolvedType.fromString("Simplex", componentResolver),
                componentResolver
        ));

        assertFalse(TypeUtils.coversType(
                ResolvedType.fromString("Vector<Integer>", componentResolver),
                ResolvedType.fromString("Vector<Real>", componentResolver),
                componentResolver
        ));

        assertTrue(TypeUtils.coversType(
                ResolvedType.fromString("Vector<Vector<Real>>", componentResolver),
                ResolvedType.fromString("Vector<Vector<PositiveReal>>", componentResolver),
                componentResolver
        ));

        assertTrue(TypeUtils.coversType(
                ResolvedType.fromString("Vector<Distribution<Real>>", componentResolver),
                ResolvedType.fromString("Vector<Distribution<PositiveReal>>", componentResolver),
                componentResolver
        ));
    }

    @Test
    public void testPartiallyCoversTypeWithAtomicTypes() throws IOException {
        ComponentResolver componentResolver = buildComponentResolver();

        assertTrue(TypeUtils.partiallyCoversType(
                ResolvedType.fromString("Real", componentResolver),
                ResolvedType.fromString("Real", componentResolver),
                componentResolver
        ));

        assertTrue(TypeUtils.partiallyCoversType(
                ResolvedType.fromString("Real", componentResolver),
                ResolvedType.fromString("PositiveReal", componentResolver),
                componentResolver
        ));

        assertFalse(TypeUtils.partiallyCoversType(
                ResolvedType.fromString("PositiveReal", componentResolver),
                ResolvedType.fromString("Real", componentResolver),
                componentResolver
        ));

        assertFalse(TypeUtils.partiallyCoversType(
                ResolvedType.fromString("Integer", componentResolver),
                ResolvedType.fromString("Real", componentResolver),
                componentResolver
        ));
    }

    @Test
    public void testPartiallyCoversTypeWithGenericTypes() throws IOException {
        ComponentResolver componentResolver = buildComponentResolver();

        assertTrue(TypeUtils.partiallyCoversType(
                ResolvedType.fromString("Vector<Real>", componentResolver),
                ResolvedType.fromString("Vector<PositiveReal>", componentResolver),
                componentResolver
        ));

        assertTrue(TypeUtils.partiallyCoversType(
                ResolvedType.fromString("Vector<Real>", componentResolver),
                ResolvedType.fromString("Simplex", componentResolver),
                componentResolver
        ));

        assertFalse(TypeUtils.partiallyCoversType(
                ResolvedType.fromString("Vector<Integer>", componentResolver),
                ResolvedType.fromString("Vector<Real>", componentResolver),
                componentResolver
        ));

        assertTrue(TypeUtils.partiallyCoversType(
                ResolvedType.fromString("Vector<Vector<Real>>", componentResolver),
                ResolvedType.fromString("Vector<Vector<PositiveReal>>", componentResolver),
                componentResolver
        ));

        assertTrue(TypeUtils.partiallyCoversType(
                ResolvedType.fromString("Vector<Distribution<Real>>", componentResolver),
                ResolvedType.fromString("Vector<Distribution<PositiveReal>>", componentResolver),
                componentResolver
        ));
    }

    private static ComponentResolver buildComponentResolver() throws IOException {
        ComponentResolver componentResolver = new ComponentResolver();
        componentResolver.registerLibraryFromFile("../../schema/phylospec-core-component-library.json");
        componentResolver.importEntireNamespace(List.of("phylospec"));
        return componentResolver;
    }
}
