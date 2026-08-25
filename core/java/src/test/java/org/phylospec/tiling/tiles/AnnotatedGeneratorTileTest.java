package org.phylospec.tiling.tiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.phylospec.annotations.PhyloParam;
import org.phylospec.annotations.PhyloSpec;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.GeneratorTileMappingDescriptor;
import org.phylospec.typeresolver.Stochasticity;

public class AnnotatedGeneratorTileTest {

    @Test
    public void readsComponentAndInputMetadataFromAnnotations() {
        ExampleTile tile = new ExampleTile();

        assertEquals("Example", tile.getPhyloSpecGeneratorName());

        Map<String, GeneratorTile.GeneratorTileInput<?, Object>> inputs = tile.getGeneratorTileInputs().stream()
                .collect(Collectors.toMap(GeneratorTile.GeneratorTileInput::getPhylospecArgumentName, input -> input));

        assertEquals(2, inputs.size());
        assertEquals(true, inputs.get("requiredValue").isRequired());
        assertEquals(false, inputs.get("optionalValue").isRequired());
        assertEquals("1", inputs.get("optionalValue").getDefaultValue().orElseThrow());
    }

    @Test
    public void exposesAnnotationsAndRuntimeInputTypesThroughOneDescriptor() {
        GeneratorTileMappingDescriptor descriptor = new ExampleTile().getMappingDescriptor();

        assertEquals(ExampleTile.class, descriptor.implementationClass());
        assertEquals("Example", descriptor.componentName());
        assertEquals(PhyloSpec.Category.FUNCTION, descriptor.category().orElseThrow());
        assertEquals(PhyloSpec.Role.OTHER, descriptor.role().orElseThrow());
        assertEquals(2, descriptor.inputs().size());

        GeneratorTileMappingDescriptor.Input requiredInput = descriptor.inputs().get(0);
        assertEquals("requiredValue", requiredInput.name());
        assertEquals(true, requiredInput.required());
        assertEquals("String", requiredInput.type().toString());
        assertEquals(EnumSet.allOf(Stochasticity.class), requiredInput.acceptedStochasticities());

        GeneratorTileMappingDescriptor.Input optionalInput = descriptor.inputs().get(1);
        assertEquals("optionalValue", optionalInput.name());
        assertEquals(false, optionalInput.required());
        assertEquals("1", optionalInput.defaultValue().orElseThrow());
        assertEquals("Integer", optionalInput.type().toString());
    }

    @Test
    public void conventionalGeneratorTilesExposeTheSameDescriptorShape() {
        GeneratorTileMappingDescriptor descriptor = new ConventionalTile().getMappingDescriptor();

        assertEquals("Conventional", descriptor.componentName());
        assertEquals(true, descriptor.category().isEmpty());
        assertEquals(true, descriptor.role().isEmpty());
        assertEquals("value", descriptor.inputs().getFirst().name());
        assertEquals("String", descriptor.inputs().getFirst().type().toString());
    }

    @Test
    public void rejectsDuplicateInputNamesInOneMapping() {
        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> new DuplicateInputTile().getMappingDescriptor());

        assertEquals(
                "Generator tile mapping for 'DuplicateInput' declares the input 'value' more than once.",
                error.getMessage());
    }

    @Test
    public void rejectsTemplateWithoutComponentAnnotation() {
        MissingComponentAnnotationTile tile = new MissingComponentAnnotationTile();

        IllegalStateException error = assertThrows(IllegalStateException.class, tile::getPhyloSpecGeneratorName);

        assertEquals(
                "Annotated generator tile 'MissingComponentAnnotationTile' must declare @PhyloSpec.",
                error.getMessage());
    }

    @Test
    public void rejectsInputWithoutParameterAnnotation() {
        MissingParameterAnnotationTile tile = new MissingParameterAnnotationTile();

        IllegalStateException error = assertThrows(IllegalStateException.class, tile::getGeneratorTileInputs);

        assertEquals(
                "Input field 'valueInput' on annotated generator tile "
                        + "'MissingParameterAnnotationTile' must declare @PhyloParam.",
                error.getMessage());
    }

    @PhyloSpec("Example")
    private static final class ExampleTile extends AnnotatedGeneratorTile<String, Object> {

        @PhyloParam("requiredValue")
        GeneratorTileInput<String, Object> requiredValueInput = input();

        @PhyloParam(value = "optionalValue", required = false, defaultValue = "1")
        GeneratorTileInput<Integer, Object> optionalValueInput = input();

        @Override
        protected String applyTile(Object state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
            return requiredValueInput.apply(state, indexVariables);
        }
    }

    private static final class MissingComponentAnnotationTile extends AnnotatedGeneratorTile<String, Object> {

        @PhyloParam("value")
        GeneratorTileInput<String, Object> valueInput = input();

        @Override
        protected String applyTile(Object state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
            return valueInput.apply(state, indexVariables);
        }
    }

    private static final class ConventionalTile extends GeneratorTile<String, Object> {

        GeneratorTileInput<String, Object> valueInput = new GeneratorTileInput<>("value");

        @Override
        public String getPhyloSpecGeneratorName() {
            return "Conventional";
        }

        @Override
        protected String applyTile(Object state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
            return valueInput.apply(state, indexVariables);
        }
    }

    @PhyloSpec("DuplicateInput")
    private static final class DuplicateInputTile extends AnnotatedGeneratorTile<String, Object> {

        @PhyloParam("value")
        GeneratorTileInput<String, Object> firstInput = input();

        @PhyloParam("value")
        GeneratorTileInput<String, Object> secondInput = input();

        @Override
        protected String applyTile(Object state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
            return firstInput.apply(state, indexVariables);
        }
    }

    @PhyloSpec("MissingParameter")
    private static final class MissingParameterAnnotationTile extends AnnotatedGeneratorTile<String, Object> {

        GeneratorTileInput<String, Object> valueInput = input();

        @Override
        protected String applyTile(Object state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
            return valueInput.apply(state, indexVariables);
        }
    }
}
