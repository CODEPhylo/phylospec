package org.phylospec.tiling;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Focused tests for {@link TypeToken#toString()}. */
public class TypeTokenRenderingTest {

    /** Container whose fields supply wildcard and type-variable types via reflection. */
    private static class Wildcards<T> {
        List<?> unbounded;
        List<? extends Number> upperBounded;
        List<? super Integer> lowerBounded;
        Map<String, ? extends List<? extends Number>> nestedWildcard;
        List<T> variable;
        Map<T, List<T>> nestedVariable;
        List<String>[] genericArray;
    }

    private static TypeToken<?> fieldType(String fieldName) {
        try {
            return TypeToken.of(Wildcards.class.getDeclaredField(fieldName).getGenericType());
        } catch (NoSuchFieldException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void testSimpleClassesUseSimpleNames() {
        assertEquals("String", new TypeToken<String>() {}.toString());
        assertEquals("Integer", new TypeToken<Integer>() {}.toString());
        assertEquals("int", TypeToken.of(int.class).toString());
        assertEquals("Object", new TypeToken<Object>() {}.toString());
    }

    @Test
    public void testRawParameterizedClassHasNoTypeArguments() {
        assertEquals("List", TypeToken.of(List.class).toString());
        assertEquals("Map", TypeToken.of(Map.class).toString());
    }

    @Test
    public void testParameterizedTypes() {
        assertEquals("List<String>", new TypeToken<List<String>>() {}.toString());
        assertEquals("Map<String, Integer>", new TypeToken<Map<String, Integer>>() {}.toString());
    }

    @Test
    public void testNestedParameterizedTypes() {
        assertEquals("Map<String, List<Integer>>", new TypeToken<Map<String, List<Integer>>>() {}.toString());
        assertEquals(
                "List<Map<String, Set<List<Integer>>>>",
                new TypeToken<List<Map<String, Set<List<Integer>>>>>() {}.toString());
    }

    @Test
    public void testArrays() {
        assertEquals("String[]", new TypeToken<String[]>() {}.toString());
        assertEquals("String[][]", new TypeToken<String[][]>() {}.toString());
        assertEquals("int[]", TypeToken.of(int[].class).toString());
        assertEquals("List[]", TypeToken.of(List[].class).toString());
    }

    @Test
    public void testGenericArray() {
        assertEquals("List<String>[]", fieldType("genericArray").toString());
    }

    @Test
    public void testUnboundedWildcardRendersAsQuestionMark() {
        assertEquals("List<?>", fieldType("unbounded").toString());
    }

    @Test
    public void testBoundedWildcardsCollapseToTheirBound() {
        assertEquals("List<Number>", fieldType("upperBounded").toString());
        assertEquals("List<Integer>", fieldType("lowerBounded").toString());
    }

    @Test
    public void testNestedWildcardsCollapseAtEveryLevel() {
        assertEquals("Map<String, List<Number>>", fieldType("nestedWildcard").toString());
    }

    @Test
    public void testTypeVariablesRenderAsTheirName() {
        assertEquals("List<T>", fieldType("variable").toString());
        assertEquals("Map<T, List<T>>", fieldType("nestedVariable").toString());
    }

    @Test
    public void testRuntimeBuiltParameterizedTypes() {
        assertEquals(
                "List<String>",
                TypeToken.parameterized(List.class, String.class).toString());
        assertEquals(
                "Map<String, Integer>",
                TypeToken.parameterized(Map.class, String.class, Integer.class).toString());
        assertEquals(
                "List<List<String>>",
                TypeToken.parameterized(
                                List.class,
                                TypeToken.parameterized(List.class, String.class)
                                        .getType())
                        .toString());
    }

    @Test
    public void testParameterizedWithNoTypeArgumentsOmitsBrackets() {
        assertEquals("List", TypeToken.parameterized(List.class).toString());
    }

    @Test
    public void testListOf() {
        assertEquals(
                "List<String>", TypeToken.listOf(new TypeToken<String>() {}).toString());
        assertEquals(
                "List<List<Integer>>",
                TypeToken.listOf(new TypeToken<List<Integer>>() {}).toString());
    }

    @Test
    public void testFirstConcreteTypeArgKeepsRendering() {
        TypeToken<?> arg = TypeToken.firstConcreteTypeArg(new TypeToken<Map<List<String>, Integer>>() {});
        assertEquals("List<String>", arg.toString());
    }
}
