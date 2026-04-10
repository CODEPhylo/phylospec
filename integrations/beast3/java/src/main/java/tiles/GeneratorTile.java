package tiles;

import org.phylospec.ast.AstNode;
import org.phylospec.ast.Expr;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.VariableResolver;
import tiling.Tile;
import tiling.TileInput;
import tiling.TilingAttempt;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This class represents tiles that cover a single generator call. Extend this class for custom tiles.
 * Use GeneratorTileInput fields to specify the tile inputs (similar to BEAST inputs).
 */
public abstract class GeneratorTile<T> extends Tile<T> {

    public abstract String getPhyloSpecGeneratorName();

    @Override
    public TilingAttempt tryToTile(AstNode node, Map<AstNode, Set<Tile<?>>> inputTiles, VariableResolver variableResolver, StochasticityResolver stochasticityResolver) {
        if (!(node instanceof Expr.Call call)) return new TilingAttempt.Irrelevant();
        if (!Objects.equals(call.functionName, this.getPhyloSpecGeneratorName())) return new TilingAttempt.Irrelevant();

        // check the stochasticity

        Stochasticity stochasticity = stochasticityResolver.getStochasticity(node);
        if (!this.getCompatibleStochasticities().contains(stochasticity)) {
            if (this.getCompatibleStochasticities().equals(Set.of(Stochasticity.STOCHASTIC))) {
                return new TilingAttempt.Rejected(
                        "BEAST expects a random variable here, but you provided a deterministic statement."
                );
            } else {
                return new TilingAttempt.Rejected(
                        "BEAST cannot handle a " + stochasticity.toString() + " here."
                );
            }
        }

        // the generator has the right name and stochasticity

        // the expected inputs correspond to the class fields with type GeneratorTile.Input (similar to BEAST inputs)
        // we use reflection to get the expected inputs

        List<TileInput<?>> expectedInputs = this.getTileInputs();
        Map<String, TileInput<?>> expectedInputsByArgument = expectedInputs.stream().collect(
                Collectors.toMap(TileInput::getKey, x -> x)
        );

        List<Set<Tile<?>>> compatibleInputTiles = new ArrayList<>();
        List<TileInput<?>> usedInputs = new ArrayList<>();
        Set<String> givenPhyloSpecArgumentNames = new HashSet<>();
        for (Expr.Argument argument : call.arguments) {
            String argumentName = this.getArgumentName(argument, call.arguments.length, expectedInputsByArgument);

            givenPhyloSpecArgumentNames.add(argumentName);
            TileInput<?> argumentInput = expectedInputsByArgument.get(argumentName);

            if (argumentInput == null) {
                // Generator has an argument for which no Input field is defined in the tile
                // we cannot tile
                return new TilingAttempt.Rejected(
                        "You need to provide a value for the '" + argumentName + "' argument to run this in BEAST."
                );
            }

            // for each argument tile, we check if its generated BEAST type is compatible with this input

            Set<Tile<?>> currentCompatibleInputTiles = argumentInput.getCompatibleInputTiles(argument, inputTiles);

            if (currentCompatibleInputTiles.isEmpty()) {
                return new TilingAttempt.Rejected(
                        "BEAST cannot deal with the value you provided for the '" + argumentName + "' argument for '" + this.getPhyloSpecGeneratorName() + "'."
                );
            }

            compatibleInputTiles.add(currentCompatibleInputTiles);
            usedInputs.add(argumentInput);
        }

        // check that we have all required input arguments

        for (String inputName : expectedInputsByArgument.keySet()) {
            TileInput<?> input = expectedInputsByArgument.get(inputName);
            if (!input.isRequired()) continue;

            if (!givenPhyloSpecArgumentNames.contains(inputName)) {
                // a required argument is missing
                // we cannot tile this
                return new TilingAttempt.Rejected(
                        "BEAST expects you to provide a value for the '" + input.getKey() + "' argument."
                );
            }
        }

        // we have all compatible input tiles
        // we now look at every possible input combination and create a new tile object correctly wired up

        return this.getWiredUpTiles(usedInputs, compatibleInputTiles);
    }

    private String getArgumentName(Expr.Argument argument, int numPassedArguments, Map<String, TileInput<?>> expectedInputs) {
        String argumentName = argument.name;

        if (argumentName != null) {
            return argumentName;
        }

        List<TileInput<?>> requiredInputs = expectedInputs.values().stream().filter(TileInput::isRequired).toList();
        if (requiredInputs.size() == 1 && numPassedArguments == 1) {
            return requiredInputs.getFirst().getKey();
        }

        if (argument.expression instanceof Expr.Variable var) {
            // we passed a variable, we use its name
            return var.variableName;
        }

        // this is the first argument (all other cases are invalid PhyloSpec and would have been caught by the type resolver)
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
                    GeneratorTileInput<?> input = (GeneratorTileInput<?>) field.get(this);
                    Tile<?> child = input.getTile();
                    if (child != null) {
                        sb.append(" (").append(input.getPhylospecArgumentName()).append(" ").append(child).append(")");
                    }
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        sb.append(")");
        return sb.toString();
    }

    public static class GeneratorTileInput<T> extends TileInput<T> {
        private final String phylospecArgumentName;

        public GeneratorTileInput(String phylospecArgumentName) {
            this(phylospecArgumentName, true);
        }

        public GeneratorTileInput(String phylospecArgumentName, boolean required) {
            super(required);
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
