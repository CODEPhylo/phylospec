package tiles.functions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import beast.base.spec.domain.Int;
import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.domain.NonNegativeReal;
import beast.base.spec.domain.PositiveInt;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.Real;
import beast.base.spec.domain.UnitInterval;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.inference.parameter.IntVectorParam;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.inference.parameter.RealVectorParam;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.tiles.Tile;

class RepeatTileTest {

    @Test
    void preservesRealDomainsAndRepeatedValues() {
        assertRealRepeat(-2.5, Real.INSTANCE);
        assertRealRepeat(2.0, PositiveReal.INSTANCE);
        assertRealRepeat(0.0, NonNegativeReal.INSTANCE);
        assertRealRepeat(0.4, UnitInterval.INSTANCE);
    }

    @Test
    void preservesIntegerDomainsAndRepeatedValues() {
        assertIntRepeat(-2, Int.INSTANCE);
        assertIntRepeat(0, NonNegativeInt.INSTANCE);
        assertIntRepeat(2, PositiveInt.INSTANCE);
    }

    @Test
    void supportsIntegerDomainEndpoints() {
        assertIntRepeat(Integer.MIN_VALUE, Int.INSTANCE);
        assertIntRepeat(Integer.MAX_VALUE, Int.INSTANCE);
        assertIntRepeat(Integer.MAX_VALUE, NonNegativeInt.INSTANCE);
        assertIntRepeat(Integer.MAX_VALUE, PositiveInt.INSTANCE);
    }

    private static void assertRealRepeat(double value, Real domain) {
        RepeatRealTile tile = new RepeatRealTile();
        tile.valueInput.setTile(valueTile(
                new RealScalarParam<>(value, domain),
                TypeToken.parameterized(RealScalarParam.class, domain.getClass())));
        tile.numInput.setTile(countTile());

        RealVectorParam<Real> result = tile.applyTile(new BEASTState("repeat-real-test"), new IdentityHashMap<>());

        assertEquals(TypeToken.parameterized(RealVectorParam.class, domain.getClass()), tile.getTypeToken());
        assertSame(domain, result.getDomain());
        assertEquals(3, result.size());
        assertEquals(List.of(value, value, value), result.getElements());
    }

    private static void assertIntRepeat(int value, Int domain) {
        RepeatIntTile tile = new RepeatIntTile();
        tile.valueInput.setTile(valueTile(
                new IntScalarParam<>(value, domain), TypeToken.parameterized(IntScalarParam.class, domain.getClass())));
        tile.numInput.setTile(countTile());

        IntVectorParam<Int> result = tile.applyTile(new BEASTState("repeat-int-test"), new IdentityHashMap<>());

        assertEquals(TypeToken.parameterized(IntVectorParam.class, domain.getClass()), tile.getTypeToken());
        assertSame(domain, result.getDomain());
        assertEquals(3, result.size());
        assertEquals(List.of(value, value, value), result.getElements());
        assertEquals(domain.getLower(), result.getLower());
        assertEquals(domain.getUpper(), result.getUpper());
    }

    private static Tile<IntScalarParam<PositiveInt>, BEASTState> countTile() {
        return valueTile(
                new IntScalarParam<>(3, PositiveInt.INSTANCE),
                TypeToken.parameterized(IntScalarParam.class, PositiveInt.class));
    }

    private static <T> Tile<T, BEASTState> valueTile(T value, TypeToken<?> typeToken) {
        Tile<T, BEASTState> tile = new Tile<T, BEASTState>() {
            @Override
            public TypeToken<?> getTypeToken() {
                return typeToken;
            }

            @Override
            protected T applyTile(BEASTState state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
                return value;
            }
        };
        tile.setIndexVariables(Set.of());
        return tile;
    }
}
