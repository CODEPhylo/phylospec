package org.phylospec.tiling.tiles;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;
import org.phylospec.annotations.PhyloSpec;
import org.phylospec.ast.ArgumentResolutionError;
import org.phylospec.ast.AstNode;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.GeneratorTileMappingDescriptor;
import org.phylospec.tiling.errors.FailedTilingAttempt;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.VariableResolver;

/**
 * This class represents tiles that cover a single generator call. Extend this class for custom tiles.
 * Use GeneratorTileInput fields to specify the tile inputs (similar to BEAST 2.8 inputs).
 */
public abstract class GeneratorTile<T, S> extends Tile<T, S> implements CandidateTile<S> {

    public abstract String getPhyloSpecGeneratorName();

    /**
     * Returns the PhyloSpec namespace this generator lives in, if the tile knows it. Tiles that
     * don't override this leave the namespace unspecified.
     */
    public Optional<String> getNamespace() {
        return Optional.empty();
    }

    /**
     * Returns an immutable, engine-independent description of this tile's generator mapping.
     * Conventional and annotation-driven tiles therefore expose the same metadata shape.
     */
    public final GeneratorTileMappingDescriptor getMappingDescriptor() {
        List<GeneratorTileMappingDescriptor.Input> inputs = this.getGeneratorTileInputs().stream()
                .map(input -> new GeneratorTileMappingDescriptor.Input(
                        input.getPhylospecArgumentName(),
                        input.isRequired(),
                        input.getDefaultValue(),
                        input.getTypeToken(),
                        input.getAcceptedStochasticities()))
                .toList();

        return new GeneratorTileMappingDescriptor(
                this.getClass(),
                this.getPhyloSpecGeneratorName(),
                this.getNamespace(),
                this.getMappingCategory(),
                this.getMappingRole(),
                inputs);
    }

    /** Annotation-driven tiles override this to expose their component category. */
    protected Optional<PhyloSpec.Category> getMappingCategory() {
        return Optional.empty();
    }

    /** Annotation-driven tiles override this to expose their semantic role. */
    protected Optional<PhyloSpec.Role> getMappingRole() {
        return Optional.empty();
    }

    @Override
    public Set<Tile<?, S>> tryToTile(
            AstNode node,
            Map<AstNode, Set<Tile<?, S>>> inputTiles,
            VariableResolver variableResolver,
            StochasticityResolver stochasticityResolver)
            throws FailedTilingAttempt {
        if (!(node instanceof Expr.Call call)) throw new FailedTilingAttempt.Irrelevant();
        if (!Objects.equals(call.functionName, this.getPhyloSpecGeneratorName()))
            throw new FailedTilingAttempt.Irrelevant();

        // check the stochasticity

        Stochasticity stochasticity = stochasticityResolver.getStochasticity(node);
        if (!this.getCompatibleStochasticities().contains(stochasticity)) {
            throw new FailedTilingAttempt.Rejected(
                    Stochasticity.getErrorMessage("Your engine", stochasticity, this.getCompatibleStochasticities()));
        }

        // the generator has the right name and stochasticity

        // the expected inputs correspond to the class fields with type GeneratorTile.Input (similar
        // to BEAST 2.8 inputs)
        // we use reflection to get the expected inputs

        List<TileInput<?, S>> expectedInputs = this.getTileInputs();
        Map<String, TileInput<?, S>> expectedInputsByArgument =
                expectedInputs.stream().collect(Collectors.toMap(TileInput::getKey, x -> x));

        List<Expr.Call.Parameter> expectedInputParameters = expectedInputs.stream()
                .map(x -> new Expr.Call.Parameter(x.getKey(), x.isRequired()))
                .toList();

        // we resolve the given arguments by name using the expected inputs

        Map<String, Expr.Argument> argumentsByName;
        try {
            argumentsByName = call.resolveArgumentNames(expectedInputParameters);
        } catch (ArgumentResolutionError.UnknownName e) {
            throw new FailedTilingAttempt.Rejected(
                    "You cannot pass a value to the '" + e.name + "' argument to run this.");
        } catch (ArgumentResolutionError.MissingRequired e) {
            throw new FailedTilingAttempt.Rejected(
                    "Your engine expects you to provide a value for the '" + e.name + "' argument.");
        } catch (ArgumentResolutionError.MissingName e) {
            throw new FailedTilingAttempt.Rejected("You are passing an illegal unnamed argument in the application of '"
                    + this.getPhyloSpecGeneratorName() + "'.");
        } catch (ArgumentResolutionError.DuplicateName e) {
            throw new FailedTilingAttempt.Rejected(
                    "You have a duplicate argument in the application of '" + this.getPhyloSpecGeneratorName() + "'.");
        }

        // the argument names provided match with the expected inputs
        // we now check if we have the appropriate input tiles

        List<Set<Tile<?, S>>> compatibleInputTiles = new ArrayList<>();
        List<TileInput<?, S>> usedInputs = new ArrayList<>();
        for (String argumentName : argumentsByName.keySet()) {
            Expr.Argument argument = argumentsByName.get(argumentName);
            TileInput<?, S> argumentInput = expectedInputsByArgument.get(argumentName);

            // for each argument tile, we check if its generated type is compatible with
            // the expected input

            Set<Tile<?, S>> currentCompatibleInputTiles =
                    argumentInput.getCompatibleInputTiles(argument, inputTiles, stochasticityResolver);

            if (currentCompatibleInputTiles.isEmpty()) {
                throw new FailedTilingAttempt.RejectedBoundary(
                        "Your engine cannot deal with the value you provided for the '"
                                + argumentName
                                + "' argument for '"
                                + this.getPhyloSpecGeneratorName()
                                + "' (expected type "
                                + argumentInput.getTypeToken().toString()
                                + ").");
            }

            compatibleInputTiles.add(currentCompatibleInputTiles);
            usedInputs.add(argumentInput);
        }

        // we have all compatible input tiles
        // we now look at every possible input combination and create a new tile object correctly
        // wired up

        return this.getWiredUpTiles(usedInputs, compatibleInputTiles, node);
    }

    /**
     * Returns the GeneratorTileInput fields declared on this tile. Widens the access of the
     * inherited (package-private-to-subclasses) getTileInputs() so callers outside this package,
     * like the engine-specification generator, can inspect the tile's inputs.
     */
    public List<GeneratorTileInput<?, S>> getGeneratorTileInputs() {
        List<GeneratorTileInput<?, S>> inputs = new ArrayList<>();
        for (TileInput<?, S> input : this.getTileInputs()) {
            inputs.add((GeneratorTileInput<?, S>) input);
        }
        return inputs;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("(").append(getClass().getSimpleName());
        for (Field field : getClass().getDeclaredFields()) {
            if (field.getType().equals(GeneratorTileInput.class)) {
                field.setAccessible(true);
                try {
                    GeneratorTileInput<?, S> input = (GeneratorTileInput<?, S>) field.get(this);
                    Tile<?, S> child = input.getTile();
                    if (child != null) {
                        sb.append(" (")
                                .append(input.getPhylospecArgumentName())
                                .append(" ")
                                .append(child)
                                .append(")");
                    }
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        sb.append(")");
        return sb.toString();
    }

    public static class GeneratorTileInput<T, S> extends TileInput<T, S> {
        private String phylospecArgumentName;
        private String defaultValue = "";

        /**
         * Creates an input whose PhyloSpec metadata will be supplied by a tile template.
         */
        public GeneratorTileInput() {
            this(null, true);
        }

        public GeneratorTileInput(String phylospecArgumentName) {
            this(phylospecArgumentName, true);
        }

        public GeneratorTileInput(String phylospecArgumentName, boolean required) {
            this(phylospecArgumentName, required, EnumSet.allOf(Stochasticity.class));
        }

        public GeneratorTileInput(String phylospecArgumentName, Set<Stochasticity> acceptedStochasticities) {
            this(phylospecArgumentName, true, acceptedStochasticities);
        }

        public GeneratorTileInput(
                String phylospecArgumentName, boolean required, Set<Stochasticity> acceptedStochasticities) {
            super(required, acceptedStochasticities);
            this.phylospecArgumentName = phylospecArgumentName;
        }

        public String getPhylospecArgumentName() {
            return phylospecArgumentName;
        }

        /** Returns the declared textual default, if one was provided as metadata. */
        public Optional<String> getDefaultValue() {
            return defaultValue.isBlank() ? Optional.empty() : Optional.of(defaultValue);
        }

        void bindMetadata(String argumentName, boolean required, String defaultValue) {
            if (argumentName == null || argumentName.isBlank()) {
                throw new IllegalArgumentException("A generator tile input must have a non-blank argument name.");
            }
            if (this.phylospecArgumentName != null && !this.phylospecArgumentName.equals(argumentName)) {
                throw new IllegalStateException("Generator tile input declares argument '"
                        + this.phylospecArgumentName
                        + "' but its annotation declares '"
                        + argumentName
                        + "'.");
            }
            this.phylospecArgumentName = argumentName;
            this.setRequired(required);
            this.defaultValue = defaultValue == null ? "" : defaultValue;
        }

        @Override
        public String getKey() {
            if (this.phylospecArgumentName == null || this.phylospecArgumentName.isBlank()) {
                throw new IllegalStateException("Generator tile input metadata has not been configured.");
            }
            return this.phylospecArgumentName;
        }
    }
}
