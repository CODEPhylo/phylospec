package org.phylospec.tiling.tiles;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;
import org.phylospec.ast.AstNode;
import org.phylospec.ast.Expr;
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

        List<Set<Tile<?, S>>> compatibleInputTiles = new ArrayList<>();
        List<TileInput<?, S>> usedInputs = new ArrayList<>();
        Set<String> givenPhyloSpecArgumentNames = new HashSet<>();
        for (Expr.Argument argument : call.arguments) {
            String argumentName = this.getArgumentName(argument, call.arguments.length, expectedInputs);

            givenPhyloSpecArgumentNames.add(argumentName);
            TileInput<?, S> argumentInput = expectedInputsByArgument.get(argumentName);

            if (argumentInput == null) {
                // Generator has an argument for which no Input field is defined in the tile
                // we cannot tile
                throw new FailedTilingAttempt.Rejected(
                        "You cannot pass a value to the '" + argumentName + "' argument to run this.");
            }

            // for each argument tile, we check if its generated type is compatible with
            // this input

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

        // check that we have all required input arguments

        for (String inputName : expectedInputsByArgument.keySet()) {
            TileInput<?, S> input = expectedInputsByArgument.get(inputName);
            if (!input.isRequired()) continue;

            if (!givenPhyloSpecArgumentNames.contains(inputName)) {
                // a required argument is missing
                // we cannot tile this
                throw new FailedTilingAttempt.Rejected(
                        "Your engine expects you to provide a value for the '" + input.getKey() + "' argument.");
            }
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

    private String getArgumentName(
            Expr.Argument argument, int numPassedArguments, List<TileInput<?, S>> expectedInputs) {
        String argumentName = argument.name;

        if (argumentName != null) {
            return argumentName;
        }

        List<TileInput<?, S>> requiredInputs =
                expectedInputs.stream().filter(TileInput::isRequired).toList();
        if (requiredInputs.size() == 1 && numPassedArguments == 1) {
            return requiredInputs.getFirst().getKey();
        }

        if (argument.expression instanceof Expr.Variable var) {
            // we passed a variable, we use its name
            return var.variableName;
        }

        // this is the first argument (all other cases are invalid PhyloSpec and would have been
        // caught by the type resolver)
        return requiredInputs.getFirst().getKey();
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
        private final String phylospecArgumentName;

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

        @Override
        public String getKey() {
            return this.phylospecArgumentName;
        }
    }
}
