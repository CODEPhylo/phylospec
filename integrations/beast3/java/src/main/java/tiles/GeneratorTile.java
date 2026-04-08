package tiles;

import org.phylospec.Utils;
import org.phylospec.ast.AstNode;
import org.phylospec.ast.Expr;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.VariableResolver;
import tiling.BEASTState;
import tiling.Tile;
import tiling.TypeToken;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.*;

public abstract class GeneratorTile<T> extends Tile<T> {

    public abstract String getPhyloSpecGeneratorName();

    @Override
    public Set<Tile<?>> tryToTile(AstNode node, Map<AstNode, Set<Tile<?>>> inputTiles, VariableResolver variableResolver, StochasticityResolver stochasticityResolver) {
        if (!(node instanceof Expr.Call call)) return Set.of();
        if (!Objects.equals(call.functionName, this.getPhyloSpecGeneratorName())) return Set.of();

        // check the stochasticity

        Stochasticity stochasticity = stochasticityResolver.getStochasticity(node);
        if (!this.getPreferredStochasticities().contains(stochasticity)) {
            return Set.of();
        }

        // the generator has the right name and stochasticity

        // the expected inputs correspond to the class fields with type GeneratorTile.Input (similar to BEAST inputs)
        // we use reflection to get the expected inputs

        Map<String, TileInput<?>> expectedInputs = new HashMap<>();
        for (Field field : this.getClass().getDeclaredFields()) {
            if (field.getType().equals(TileInput.class)) {
                field.setAccessible(true);
                try {
                    TileInput<?> input = (TileInput<?>) field.get(this);
                    input.resolveTypeFromField(field);
                    expectedInputs.put(input.getPhylospecArgumentName(), input);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        List<Set<Tile<?>>> compatibleInputTiles = new ArrayList<>();
        Set<String> givenPhyloSpecArgumentNames = new HashSet<>();
        for (Expr.Argument argument : call.arguments) {
            String argumentName = this.getArgumentName(argument, call.arguments.length, expectedInputs);

            givenPhyloSpecArgumentNames.add(argumentName);
            TileInput<?> argumentInput = expectedInputs.get(argumentName);

            if (argumentInput == null) {
                // Generator has an argument for which no Input field is defined in the tile
                // we cannot tile
                return Set.of();
            }

            Set<Tile<?>> currentArgumentTiles = inputTiles.get(argument.expression);

            if (currentArgumentTiles == null) {
                throw new RuntimeException("Argument tiles not found. This should not happen.");
            }

            // for each argument tile, we check if its generated BEAST type is compatible with this input

            Set<Tile<?>> currentCompatibleInputTiles = new HashSet<>();
            for (Tile<?> argumentTile : currentArgumentTiles) {
                if (argumentInput.getTypeToken().isAssignableFrom(argumentTile.getTypeToken())) {
                    currentCompatibleInputTiles.add(argumentTile);
                }
            }

            compatibleInputTiles.add(currentCompatibleInputTiles);
        }

        // check that we have all required input arguments

        for (String inputName : expectedInputs.keySet()) {
            TileInput<?> input = expectedInputs.get(inputName);
            if (!input.isRequired()) continue;

            if (!givenPhyloSpecArgumentNames.contains(inputName)) {
                // a required argument is missing
                // we cannot tile this
                return Set.of();
            }
        }

        // we have all compatible input tiles
        // we now look at every possible input combination and create a new tile object correctly wired up

        Set<Tile<?>> wiredUpTiles = new HashSet<>();
        Utils.visitCombinations(
                compatibleInputTiles,
                inputs -> {
                    // for this combination of inputs, create a new Tile object with correctly wired up Input fields

                    Tile<?> wiredUpTile = this.createInstance();

                    // get Input fields from the new instance, keyed by argument name

                    Map<String, TileInput<?>> newTileInputs = new HashMap<>();
                    for (Field field : wiredUpTile.getClass().getDeclaredFields()) {
                        if (field.getType().equals(TileInput.class)) {
                            field.setAccessible(true);
                            try {
                                TileInput<?> input = (TileInput<?>) field.get(wiredUpTile);
                                newTileInputs.put(input.getPhylospecArgumentName(), input);
                            } catch (IllegalAccessException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }

                    // wire each input to the corresponding tile from this combination

                    int totalWeight = wiredUpTile.getPriority().getWeight();

                    for (int i = 0; i < call.arguments.length; i++) {
                        String argumentName = this.getArgumentName(call.arguments[i], call.arguments.length, expectedInputs);
                        Tile<?> inputTile = inputs.get(i);

                        newTileInputs.get(argumentName).setTile(inputs.get(i));
                        totalWeight += inputTile.getWeight();
                    }

                    wiredUpTile.setWeight(totalWeight);

                    wiredUpTiles.add(wiredUpTile);
                }
        );

        return wiredUpTiles;
    }

    private String getArgumentName(Expr.Argument argument, int numPassedArguments, Map<String, TileInput<?>> expectedInputs) {
        String argumentName = argument.name;

        if (argumentName != null) {
            return argumentName;
        }

        List<TileInput<?>> requiredInputs = expectedInputs.values().stream().filter(TileInput::isRequired).toList();
        if (requiredInputs.size() == 1 && numPassedArguments == 1) {
            return requiredInputs.getFirst().phylospecArgumentName;
        }

        if (argument.expression instanceof Expr.Variable var) {
            // we passed a variable, we use its name
            return var.variableName;
        }

        // this is the first argument (all other cases are invalid PhyloSpec and would have been caught by the type resolver)
        return requiredInputs.getFirst().getPhylospecArgumentName();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("(").append(getClass().getSimpleName());
        for (Field field : getClass().getDeclaredFields()) {
            if (field.getType().equals(TileInput.class)) {
                field.setAccessible(true);
                try {
                    TileInput<?> input = (TileInput<?>) field.get(this);
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

    public static class TileInput<T> {
        private final String phylospecArgumentName;
        private final boolean required;
        private TypeToken<T> typeToken;

        private Tile<T> tile;

        public TileInput(String phylospecArgumentName) {
            this(phylospecArgumentName, true);
        }

        public TileInput(String phylospecArgumentName, boolean required) {
            this.phylospecArgumentName = phylospecArgumentName;
            this.required = required;
        }

        // called during reflection-based field scanning in tryToTile to resolve
        // the type token from the field's generic signature rather than requiring
        // it to be passed explicitly at the call site
        void resolveTypeFromField(Field field) {
            if (this.typeToken != null) return;
            // Input<T> — T is the first type argument
            ParameterizedType fieldType = (ParameterizedType) field.getGenericType();
            this.typeToken = (TypeToken<T>) TypeToken.of(fieldType.getActualTypeArguments()[0]);
        }

        public String getPhylospecArgumentName() {
            return phylospecArgumentName;
        }

        public TypeToken<T> getTypeToken() {
            return typeToken;
        }

        public void setTile(Tile<?> tile) {
            // we assume that the generated type is compatible
            this.tile = (Tile<T>) tile;
        }

        public Tile<T> getTile() {
            return this.tile;
        }

        public boolean isRequired() {
            return this.required;
        }

        public T apply(BEASTState beastState) {
            return this.tile != null ? this.tile.apply(beastState) : null;
        }
    }

}
