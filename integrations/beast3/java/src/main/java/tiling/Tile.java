package tiling;

import beastconfig.BEASTState;
import org.phylospec.Utils;
import org.phylospec.ast.AstNode;
import org.phylospec.ast.Expr;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.VariableResolver;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

public abstract class Tile<T> {
    /* methods to find a tiling */

    /**
     * Returns the default priority of these tiles. Can be overridden by custom tiles.
     */
    public TilePriority getPriority() {
        return TilePriority.DEFAULT;
    }

    /**
     * Returns the different stochasticity levels which the root of the AST subgraph covered by the tile can have.
     */
    protected Set<Stochasticity> getCompatibleStochasticities() {
        return EnumSet.allOf(Stochasticity.class);
    }

    /**
     * Returns the {@code TypeToken<?>} produced when by a successful application of this tile.
     * By default, this returns the type parameter {@code T} of {@code Tile<T>}. If this cannot be
     * determined at compile-time, a custom tile has to override this method.
     */
    public TypeToken<?> getTypeToken() {
        // parse the type parameter T
        Type superclass = this.getClass().getGenericSuperclass();
        if (superclass instanceof ParameterizedType pt) {
            return TypeToken.of(pt.getActualTypeArguments()[0]);
        } else {
            throw new IllegalArgumentException("Tile " + this.getClass() + " has no return type parameter. Either specify the type in the type signature of the inheriting class, or override the getTypeToken method.");
        }
    }

    /**
     * Tries to tile this tile to the AST subgraph starting with 'node'. Has to be overridden by custom tiles.
     */
    public abstract Set<Tile<?>> tryToTile(
            AstNode node, Map<AstNode,
            Set<Tile<?>>> inputTiles,
            VariableResolver variableResolver,
            StochasticityResolver stochasticityResolver
    ) throws FailedTilingAttempt;

    /**
     * Recursively walks this tile's wired sub-tiles and verifies that no AstNode is committed
     * to two different sub-tiles anywhere in the sub-graph.
     * Throws {@link InconsistentTilingException} if an inconsistency is detected at any depth.
     */
    public boolean isInconsistent(IdentityHashMap<AstNode, Tile<?>> assignments) {
        Map<AstNode, Tile<?>> usedInputs;

        try {
            usedInputs = this.getWiredUpInputs();
        } catch (InconsistentTilingException exception) {
            return true;
        }

        for (Map.Entry<AstNode, Tile<?>> entry : usedInputs.entrySet()) {
            AstNode inputNode = entry.getKey();
            Tile<?> inputTile = entry.getValue();

            Tile<?> existingInputTile = assignments.putIfAbsent(inputNode, inputTile);

            if (existingInputTile == null) {
                // new commitment - recurse into it
                if (inputTile.isInconsistent(assignments)) {
                    return true;
                }
            } else if (existingInputTile != inputTile) {
                return true;
            }
            // if existingInputTile == inputTile, this sub-tile has already been walked
        }

        return false;
    }

    /**
     * Returns the immediate wired sub-tiles of this tile, keyed by the AstNode each sub-tile
     * covers. Throws {@link InconsistentTilingException} if two TileInputs wire to different
     * sub-tiles for the same AstNode, which indicates an intra-candidate inconsistency (e.g.
     * {@code f(x, x)} where the two x-slots picked different sub-tiles for the same declaration).
     */
    public Map<AstNode, Tile<?>> getWiredUpInputs() throws InconsistentTilingException {
        Map<AstNode, Tile<?>> usedInputs = new IdentityHashMap<>();

        for (TileInput<?> input : this.getTileInputs()) {
            Tile<?> inputTile = input.getTile();
            if (inputTile == null) continue;

            AstNode inputNode = inputTile.getRootNode();
            Tile<?> existingInputTile = usedInputs.putIfAbsent(inputNode, inputTile);

            if (existingInputTile != null && existingInputTile != inputTile) {
                // we already have a different input mapped to this AST node and there we used a different input tile
                throw new InconsistentTilingException(inputNode, existingInputTile, inputTile);
            }
        }

        return usedInputs;
    }

    /**
     * Returns the {@code TileInput<?>} fields of this tile using reflection.
     */
    protected List<TileInput<?>> getTileInputs() {
        List<TileInput<?>> inputs = new ArrayList<>();
        for (Field field : this.getClass().getDeclaredFields()) {
            if (TileInput.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                try {
                    TileInput<?> input = (TileInput<?>) field.get(this);
                    input.resolveTypeFromField(field);
                    inputs.add(input);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return inputs;
    }

    /**
     * Creates wired up fresh tiles for the given inputs and their compatible input tiles.
     */
    protected Set<Tile<?>> getWiredUpTiles(
            List<TileInput<?>> tileInputs,
            List<Set<Tile<?>>> compatibleInputTiles,
            AstNode rootNode
    ) {
        Set<Tile<?>> wiredUpTiles = new HashSet<>();

        Utils.visitCombinations(
                compatibleInputTiles,
                inputs -> {
                    Tile<?> wiredUpTile = this.createInstance();

                    // get TileInput fields from fresh instance

                    Map<String, TileInput<?>> freshInputsByKey = new HashMap<>();
                    for (TileInput<?> freshInput : wiredUpTile.getTileInputs()) {
                        freshInputsByKey.put(freshInput.getKey(), freshInput);
                    }

                    // wire each input tile and accumulate weight

                    int totalWeight = this.getPriority().getWeight();
                    for (int i = 0; i < tileInputs.size(); i++) {
                        Tile<?> inputTile = inputs.get(i);
                        String tileInputKey = tileInputs.get(i).getKey();

                        TileInput<?> freshInputTile = freshInputsByKey.get(tileInputKey);
                        freshInputTile.setTile(inputTile);

                        totalWeight += inputTile.getWeight();
                    }

                    wiredUpTile.setWeight(totalWeight);
                    wiredUpTile.setRootNode(rootNode);

                    if (!wiredUpTile.isInconsistent(new IdentityHashMap<>())) {
                        wiredUpTiles.add(wiredUpTile);
                    }
                }
        );

        return wiredUpTiles;
    }

    /** methods to apply a tiling */

    private final Map<IdentityHashMap<Expr.Variable, Integer>, T> appliedWithIndexedVariables = new HashMap<>();

    /**
     * Applies the tile. Memoization is used to not apply the same tile twice.
     */
    public T apply(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        // we filter the index Variables by the one in the current scope
        IdentityHashMap<Expr.Variable, Integer> indexVariablesInScope = new IdentityHashMap<>();
        for (Expr.Variable variable : indexVariables.keySet()) {
            if (this.getIndexVariables().contains(variable)) {
                indexVariablesInScope.put(variable, indexVariables.get(variable));
            }
        }

        // we check if we have already applied this tile with the given index variables
        for (IdentityHashMap<Expr.Variable, Integer> previousIndexVariables : this.appliedWithIndexedVariables.keySet()) {
            if (previousIndexVariables.size() != indexVariablesInScope.size()) continue;

            boolean allMatch = true;
            for (Expr.Variable index : indexVariablesInScope.keySet()) {
                if (!Objects.equals(indexVariablesInScope.get(index), previousIndexVariables.get(index))) allMatch = false;
            }

            if (allMatch) return this.appliedWithIndexedVariables.get(previousIndexVariables);
        }

        try {
            T result = this.applyTile(beastState, indexVariablesInScope);
            this.appliedWithIndexedVariables.put(new IdentityHashMap<>(indexVariablesInScope), result);
            return result;
        } catch (TileApplicationError tilingError) {
            // attach node if needed
            if (tilingError.getAstNode() == null) {
                tilingError.setAstNode(this.getRootNode());
            }
            // rethrow the error
            throw tilingError;
        } catch (Exception e) {
            // we wrap the exception into a tiling error
            throw new WrappedTileApplicationError(
                    this.getRootNode(),
                    "Creating the BEAST 2.8 objects did not work.",
                    e
            );
        }
    }

    /**
     * Applies the tile. This method should be overridden by custom tiles.
     */
    protected abstract T applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables);

    /* root node of an applied tile */

    protected AstNode rootNode;

    protected AstNode getRootNode() {
        return this.rootNode;
    }

    public void setRootNode(AstNode node) {
        this.rootNode = node;
    }

    /* index variables in scope */

    private Set<Expr.Variable> indexVariables;

    public Set<Expr.Variable> getIndexVariables() {
        return this.indexVariables;
    }

    public void setIndexVariables(Set<Expr.Variable> currentIndexVariables) {
        this.indexVariables = currentIndexVariables;
    }

    /* tiling weight */

    private int weight = 0;

    public int getWeight() {
        return this.weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    /* helper */

    /**
     * Creates a new instance of this tile.
     */
    protected Tile<?> createInstance() {
        try {
            return this.getClass().getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Tile " + getClass().getSimpleName() + " has no public no-arg constructor", e);
        }
    }

    /**
     * Constructs an ID consisting of the given prefix, postfix, and index variables.
     */
    protected String getId(String prefix, IdentityHashMap<Expr.Variable, Integer> indexVariables, String postfix) {
        StringBuilder builder = new StringBuilder(prefix);

        if (!indexVariables.isEmpty()) {
            Map<String, String> sortedIndexValues = new TreeMap<>();
            for (Expr.Variable indexVar : indexVariables.keySet()) {
                // this does not work with duplicate index names, but this never happens
                sortedIndexValues.put(indexVar.variableName, indexVariables.get(indexVar).toString());
            }

            for (String index : sortedIndexValues.keySet()) {
                builder.append("_").append(sortedIndexValues.get(index));
            }
        }

        if (!postfix.equals("")) {
            builder.append("_").append(postfix);
        }

        return builder.toString();
    }
}
