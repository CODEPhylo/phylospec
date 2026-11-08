package tiles.misc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import beast.base.spec.domain.Int;
import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.Real;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.IntScalar;
import beast.base.spec.type.RealScalar;
import beastconfig.BEASTState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.phylospec.ast.AstNode;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.errors.FailedTilingAttempt;
import org.phylospec.tiling.errors.InconsistentTilingException;
import org.phylospec.tiling.tiles.Tile;
import tiling.ExpValue;

public class ListVectorTileTest {

    @Test
    public void exposesSharedElementToGlobalConsistencyCheck() throws InconsistentTilingException {
        Expr.Literal node = new Expr.Literal(1.0);
        Tile<?, BEASTState> first = new LiteralTile<>(new TypeToken<Double>() {}, 1.0, node);
        Tile<?, BEASTState> alternative = new LiteralTile<>(new TypeToken<Double>() {}, 1.0, node);
        ListVectorTile vector = new ListVectorTile(List.of(first, first));

        assertSame(first, vector.getWiredUpInputs().get(node));
        assertEquals(1, vector.getWiredUpInputs().size());
        assertFalse(vector.isInconsistent(new IdentityHashMap<>()));
        IdentityHashMap<AstNode, Tile<?, ?>> assignments = new IdentityHashMap<>();
        assignments.put(node, alternative);
        assertTrue(vector.isInconsistent(assignments));
    }

    @Test
    public void rejectsDifferentTilesForRepeatedElement() {
        Expr.Literal node = new Expr.Literal(1.0);
        ListVectorTile vector = new ListVectorTile(List.of(
                new LiteralTile<>(new TypeToken<Double>() {}, 1.0, node),
                new LiteralTile<>(new TypeToken<Double>() {}, 1.0, node)));

        assertThrows(InconsistentTilingException.class, vector::getWiredUpInputs);
        assertTrue(vector.isInconsistent(new IdentityHashMap<>()));
    }

    @Test
    public void checksSharedDependenciesAcrossNestedVectors() {
        Expr.Literal shared = new Expr.Literal(1.0);
        Tile<?, BEASTState> first = new LiteralTile<>(new TypeToken<Double>() {}, 1.0, shared);
        Tile<?, BEASTState> alternative = new LiteralTile<>(new TypeToken<Double>() {}, 1.0, shared);
        ListVectorTile left = new ListVectorTile(List.of(first));
        left.setRootNode(new Expr.Array(List.of(shared)));
        ListVectorTile right = new ListVectorTile(List.of(alternative));
        right.setRootNode(new Expr.Array(List.of(shared)));

        assertTrue(new ListVectorTile(List.of(left, right)).isInconsistent(new IdentityHashMap<>()));
        assertFalse(new ListVectorTile(List.of(left, left)).isInconsistent(new IdentityHashMap<>()));
    }

    @Test
    public void filtersLongVectorsByElementTypeBeforeCombining() throws FailedTilingAttempt {
        Expr.Literal element = new Expr.Literal(0.0625);
        Expr.Array array = new Expr.Array(Collections.nCopies(16, element));
        Set<Tile<?, BEASTState>> elementTiles = new LiteralTile<>().tryToTile(element, Map.of(), null, null);
        Map<AstNode, Set<Tile<?, BEASTState>>> inputTiles = Map.of(element, elementTiles);

        Set<Tile<?, BEASTState>> vectorTiles = new ListVectorTile().tryToTile(array, inputTiles, null, null);

        assertEquals(elementTiles.size(), vectorTiles.size());
        Set<TypeToken<?>> elementTypes =
                elementTiles.stream().map(Tile::getTypeToken).collect(java.util.stream.Collectors.toSet());
        assertTrue(vectorTiles.stream().map(Tile::getTypeToken).allMatch(vectorType -> elementTypes.stream()
                .map(TypeToken::listOf)
                .anyMatch(vectorType::equals)));
    }

    @Test
    public void preservesMultipleCombinationsWithinTheSameElementType() throws FailedTilingAttempt {
        Expr.Literal first = new Expr.Literal(1.0);
        Expr.Literal second = new Expr.Literal(2.0);
        Expr.Array array = new Expr.Array(List.of(first, second));
        TypeToken<Double> doubleType = new TypeToken<>() {};

        Set<Tile<?, BEASTState>> firstTiles =
                Set.of(new LiteralTile<>(doubleType, 1.0, first), new LiteralTile<>(doubleType, 10.0, first));
        Set<Tile<?, BEASTState>> secondTiles =
                Set.of(new LiteralTile<>(doubleType, 2.0, second), new LiteralTile<>(doubleType, 20.0, second));

        Set<Tile<?, BEASTState>> vectorTiles =
                new ListVectorTile().tryToTile(array, Map.of(first, firstTiles, second, secondTiles), null, null);

        assertEquals(4, vectorTiles.size());
    }

    @Test
    public void mixedRealImplementationsKeepLiveObjectsWithoutEvaluatingDuringMatching()
            throws FailedTilingAttempt, InconsistentTilingException {
        Expr.Variable scalarNode = new Expr.Variable("x");
        Expr.Variable derivedNode = new Expr.Variable("exponential");
        RealScalarParam<Real> scalar = new RealScalarParam<>(2.0, Real.INSTANCE);
        // An unbound ExpValue would fail if matching tried to read its numeric value.
        ExpValue derived = new ExpValue();
        ObservedTile<RealScalarParam<Real>> scalarTile =
                new ObservedTile<>(new TypeToken<RealScalarParam<Real>>() {}, scalar, scalarNode);
        ObservedTile<ExpValue> derivedTile = new ObservedTile<>(new TypeToken<ExpValue>() {}, derived, derivedNode);
        Expr.Array array = new Expr.Array(List.of(scalarNode, derivedNode, scalarNode));

        Set<Tile<?, BEASTState>> candidates = new ListVectorTile()
                .tryToTile(array, Map.of(scalarNode, Set.of(scalarTile), derivedNode, Set.of(derivedTile)), null, null);

        assertEquals(1, candidates.size());
        Tile<?, BEASTState> candidate = candidates.iterator().next();
        assertTrue(
                new TypeToken<List<? extends RealScalar<? extends Real>>>() {}.isAssignableFrom(
                        candidate.getTypeToken()));
        assertEquals(0, scalarTile.applications);
        assertEquals(0, derivedTile.applications);
        assertSame(scalarTile, candidate.getWiredUpInputs().get(scalarNode));
        assertFalse(candidate.isInconsistent(new IdentityHashMap<>()));

        List<?> values = (List<?>) candidate.apply(null, new IdentityHashMap<>());
        assertSame(scalar, values.get(0));
        assertSame(derived, values.get(1));
        assertSame(scalar, values.get(2));
        assertEquals(1, scalarTile.applications);
        assertEquals(1, derivedTile.applications);
    }

    @Test
    public void mixedIntegerDomainsMatchScalarListConsumersAndKeepOriginalObjects() throws FailedTilingAttempt {
        Expr.Variable signedNode = new Expr.Variable("signed");
        Expr.Variable nonnegativeNode = new Expr.Variable("nonnegative");
        IntScalarParam<Int> signed = new IntScalarParam<>(-2, Int.INSTANCE);
        IntScalarParam<NonNegativeInt> nonnegative = new IntScalarParam<>(3, NonNegativeInt.INSTANCE);
        ObservedTile<IntScalarParam<Int>> signedTile =
                new ObservedTile<>(new TypeToken<IntScalarParam<Int>>() {}, signed, signedNode);
        ObservedTile<IntScalarParam<NonNegativeInt>> nonnegativeTile =
                new ObservedTile<>(new TypeToken<IntScalarParam<NonNegativeInt>>() {}, nonnegative, nonnegativeNode);
        Expr.Array array = new Expr.Array(List.of(signedNode, nonnegativeNode));

        Set<Tile<?, BEASTState>> candidates = new ListVectorTile()
                .tryToTile(
                        array,
                        Map.of(signedNode, Set.of(signedTile), nonnegativeNode, Set.of(nonnegativeTile)),
                        null,
                        null);

        assertEquals(1, candidates.size());
        Tile<?, BEASTState> candidate = candidates.iterator().next();
        assertTrue(
                new TypeToken<List<? extends IntScalar<? extends Int>>>() {}.isAssignableFrom(
                        candidate.getTypeToken()));
        assertFalse(
                new TypeToken<List<? extends RealScalar<? extends Real>>>() {}.isAssignableFrom(
                        candidate.getTypeToken()));
        assertEquals(0, signedTile.applications);
        assertEquals(0, nonnegativeTile.applications);
        List<?> values = (List<?>) candidate.apply(null, new IdentityHashMap<>());
        assertSame(signed, values.get(0));
        assertSame(nonnegative, values.get(1));
        assertSame(Int.INSTANCE, signed.getDomain());
        assertSame(NonNegativeInt.INSTANCE, nonnegative.getDomain());
    }

    @Test
    public void fallbackDoesNotMixNumericFamiliesOrCoerceNonnumericElements() throws FailedTilingAttempt {
        TypeToken<?> real = new TypeToken<RealScalarParam<Real>>() {};
        TypeToken<?> integer = new TypeToken<IntScalarParam<Int>>() {};
        TypeToken<?> text = new TypeToken<String>() {};
        TypeToken<?> bool = new TypeToken<Boolean>() {};
        List<List<TypeToken<?>>> incompatibleTypes = List.of(
                List.of(real, integer),
                List.of(real, text),
                List.of(integer, bool),
                List.of(text, bool),
                List.of(real, new TypeToken<Double>() {}));
        for (List<TypeToken<?>> pair : incompatibleTypes) {
            Expr.Variable first = new Expr.Variable("first");
            Expr.Variable second = new Expr.Variable("second");
            Expr.Array array = new Expr.Array(List.of(first, second));
            ObservedTile<Object> firstTile = new ObservedTile<>(pair.get(0), null, first);
            ObservedTile<Object> secondTile = new ObservedTile<>(pair.get(1), null, second);

            Set<Tile<?, BEASTState>> candidates = new ListVectorTile()
                    .tryToTile(array, Map.of(first, Set.of(firstTile), second, Set.of(secondTile)), null, null);

            assertTrue(candidates.isEmpty(), pair.toString());
            assertEquals(0, firstTile.applications);
            assertEquals(0, secondTile.applications);
        }
    }

    @Test
    public void mixedLongListCollapsesOnlyBareLiteralDomainAlternatives() throws FailedTilingAttempt {
        Expr.Variable derivedNode = new Expr.Variable("exponential");
        ObservedTile<ExpValue> derived = new ObservedTile<>(new TypeToken<ExpValue>() {}, new ExpValue(), derivedNode);
        List<Expr> elements = new ArrayList<>(List.of(derivedNode));
        Map<AstNode, Set<Tile<?, BEASTState>>> inputs = new IdentityHashMap<>();
        inputs.put(derivedNode, Set.of(derived));
        for (int i = 0; i < 16; i++) {
            Expr.Literal literal = new Expr.Literal(0.5);
            elements.add(literal);
            inputs.put(literal, new LiteralTile<>().tryToTile(literal, Map.of(), null, null));
        }

        Set<Tile<?, BEASTState>> candidates =
                new ListVectorTile().tryToTile(new Expr.Array(elements), inputs, null, null);

        assertEquals(1, candidates.size());
        assertEquals(0, derived.applications);
        assertTrue(
                new TypeToken<List<? extends RealScalar<? extends Real>>>() {}.isAssignableFrom(
                        candidates.iterator().next().getTypeToken()));
    }

    @Test
    public void mixedFallbackPreservesNamedImplementationAndDomainAlternatives()
            throws FailedTilingAttempt, InconsistentTilingException {
        Expr.Variable namedNode = new Expr.Variable("named");
        Expr.Variable derivedNode = new Expr.Variable("derived");
        TypeToken<RealScalarParam<Real>> realType = new TypeToken<>() {};
        ObservedTile<RealScalarParam<Real>> first =
                new ObservedTile<>(realType, new RealScalarParam<>(1.0, Real.INSTANCE), namedNode);
        ObservedTile<RealScalarParam<Real>> alternative =
                new ObservedTile<>(realType, new RealScalarParam<>(2.0, Real.INSTANCE), namedNode) {};
        ObservedTile<RealScalarParam<PositiveReal>> narrowed = new ObservedTile<>(
                new TypeToken<RealScalarParam<PositiveReal>>() {},
                new RealScalarParam<>(3.0, PositiveReal.INSTANCE),
                namedNode);
        ObservedTile<ExpValue> derived = new ObservedTile<>(new TypeToken<ExpValue>() {}, new ExpValue(), derivedNode);
        Expr.Array array = new Expr.Array(List.of(namedNode, derivedNode));

        Set<Tile<?, BEASTState>> candidates = new ListVectorTile()
                .tryToTile(
                        array,
                        Map.of(namedNode, Set.of(first, alternative, narrowed), derivedNode, Set.of(derived)),
                        null,
                        null);

        assertEquals(3, candidates.size());
        Set<Tile<?, ?>> retained = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Tile<?, BEASTState> candidate : candidates) {
            retained.add(candidate.getWiredUpInputs().get(namedNode));
        }
        assertEquals(Set.of(first, alternative, narrowed), retained);
        assertEquals(0, first.applications);
        assertEquals(0, alternative.applications);
        assertEquals(0, narrowed.applications);
    }

    private static class ObservedTile<T> extends Tile<T, BEASTState> {
        private final TypeToken<?> type;
        private final T value;
        private int applications;

        private ObservedTile(TypeToken<?> type, T value, Expr node) {
            this.type = type;
            this.value = value;
            setRootNode(node);
            setIndexVariables(Set.of());
        }

        @Override
        public TypeToken<?> getTypeToken() {
            return type;
        }

        @Override
        protected T applyTile(BEASTState state, IdentityHashMap<Expr.Variable, Integer> indices) {
            applications++;
            return value;
        }
    }
}
