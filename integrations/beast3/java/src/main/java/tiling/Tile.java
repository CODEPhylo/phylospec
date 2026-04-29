package tiling;

import beastconfig.BEASTState;
import org.phylospec.ast.AstNode;
import org.phylospec.ast.Expr;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

/**
 * A tile covers a subgraph of the PhyloSpec AST rooted at a root node and describes how to turn ("apply") this
 * subgraph into BEAST objects.
 */
public abstract class Tile<T> {

    /* methods to evaluate a tiling */

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

    /** methods to apply a tiling */

    private final Map<IdentityHashMap<Expr.Variable, Integer>, T> appliedWithIndexedVariables = new HashMap<>();

    /**
     * Applies the tile. Memoization is used to not apply the same tile twice.
     */
    public T apply(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        // we use memoization to make sure that no tile is applied more than once.
        // two apply calls are only considered the same when the index variables in scope are identical

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

        // we have never applied this tile with these index variables
        // we apply it now

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
