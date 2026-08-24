package org.phylospec.tiling.tiles;

import java.lang.reflect.Field;
import java.util.Set;
import org.phylospec.annotations.PhyloParam;
import org.phylospec.annotations.PhyloSpec;
import org.phylospec.typeresolver.Stochasticity;

/**
 * Opt-in generator-tile template that obtains component and argument metadata from
 * {@link PhyloSpec} and {@link PhyloParam} annotations.
 *
 * <p>The template only removes structural boilerplate. Subclasses still implement
 * {@link #applyTile} and therefore retain full control over engine-specific object
 * construction and state updates.</p>
 */
public abstract class AnnotatedGeneratorTile<T, S> extends GeneratorTile<T, S> {

    /** Creates a required-by-default input whose final metadata comes from {@link PhyloParam}. */
    protected final <I> GeneratorTileInput<I, S> input() {
        return new GeneratorTileInput<>();
    }

    /**
     * Creates an input with an engine-specific stochasticity restriction. Its name and required
     * status still come from {@link PhyloParam}.
     */
    protected final <I> GeneratorTileInput<I, S> input(Set<Stochasticity> acceptedStochasticities) {
        return new GeneratorTileInput<>(null, true, acceptedStochasticities);
    }

    @Override
    public final String getPhyloSpecGeneratorName() {
        return componentAnnotation().value();
    }

    @Override
    protected void configureTileInput(Field field, TileInput<?, S> input) {
        super.configureTileInput(field, input);

        if (!(input instanceof GeneratorTileInput<?, ?> generatorInput)) {
            throw new IllegalStateException(
                    "Annotated generator tile input field '" + field.getName() + "' must use GeneratorTileInput.");
        }

        PhyloParam parameter = field.getAnnotation(PhyloParam.class);
        if (parameter == null) {
            throw new IllegalStateException("Input field '"
                    + field.getName()
                    + "' on annotated generator tile '"
                    + getClass().getSimpleName()
                    + "' must declare @PhyloParam.");
        }

        generatorInput.bindMetadata(parameter.value(), parameter.required(), parameter.defaultValue());
    }

    private PhyloSpec componentAnnotation() {
        PhyloSpec annotation = getClass().getAnnotation(PhyloSpec.class);
        if (annotation == null) {
            throw new IllegalStateException(
                    "Annotated generator tile '" + getClass().getSimpleName() + "' must declare @PhyloSpec.");
        }
        if (annotation.value().isBlank()) {
            throw new IllegalStateException("Annotated generator tile '"
                    + getClass().getSimpleName()
                    + "' must declare a non-blank PhyloSpec component name.");
        }
        return annotation;
    }
}
