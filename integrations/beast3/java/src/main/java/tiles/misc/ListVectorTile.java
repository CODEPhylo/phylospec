package tiles.misc;

import beast.base.spec.domain.Int;
import beast.base.spec.domain.Real;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.IntScalar;
import beast.base.spec.type.RealScalar;
import beastconfig.BEASTState;
import java.util.*;
import org.phylospec.Utils;
import org.phylospec.ast.AstNode;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.errors.FailedTilingAttempt;
import org.phylospec.tiling.errors.InconsistentTilingException;
import org.phylospec.tiling.tiles.AstNodeTile;
import org.phylospec.tiling.tiles.Tile;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.VariableResolver;

/**
 * Creates a list of the original element objects. Prefer matching concrete element types;
 * otherwise allow different scalar implementations within the same numeric family.
 */
public class ListVectorTile extends AstNodeTile<List<Object>, Expr.Array, BEASTState> {

    private final List<Tile<?, BEASTState>> inputTiles;
    private final TypeToken<?> elementType;

    public ListVectorTile() {
        this(List.of());
    }

    public ListVectorTile(List<Tile<?, BEASTState>> inputTiles) {
        this(inputTiles, null);
    }

    private ListVectorTile(List<Tile<?, BEASTState>> inputTiles, TypeToken<?> elementType) {
        this.inputTiles = inputTiles;
        this.elementType = elementType;
    }

    @Override
    public Set<Tile<?, BEASTState>> tryToTile(
            AstNode node,
            Map<AstNode, Set<Tile<?, BEASTState>>> allInputTiles,
            VariableResolver variableResolver,
            StochasticityResolver stochasticityResolver)
            throws FailedTilingAttempt {
        if (!(node instanceof Expr.Array array)) throw new FailedTilingAttempt.Irrelevant();

        // we gather at all possible input tiles for each element

        List<Set<Tile<?, BEASTState>>> allPossibleInputTiles = new ArrayList<>();
        for (Expr element : array.elements) {
            Set<Tile<?, BEASTState>> elementTiles = allInputTiles.get(element);
            allPossibleInputTiles.add(elementTiles);
        }

        // Keep the existing concrete-type matches. Group by TypeToken before visiting
        // combinations so that incompatible combinations are never constructed. This is
        // especially important for long numeric vectors, where each literal has several possible
        // Java representations and the unfiltered Cartesian product grows exponentially.

        Map<TypeToken<?>, List<Set<Tile<?, BEASTState>>>> compatibleTilesByType =
                commonElementTypes(allPossibleInputTiles);

        if (compatibleTilesByType.isEmpty() && !array.elements.isEmpty()) {
            addScalarFamily(
                    compatibleTilesByType,
                    array,
                    allPossibleInputTiles,
                    new TypeToken<RealScalar<? extends Real>>() {},
                    new TypeToken<RealScalarParam<Real>>() {});
            addScalarFamily(
                    compatibleTilesByType,
                    array,
                    allPossibleInputTiles,
                    new TypeToken<IntScalar<? extends Int>>() {},
                    new TypeToken<IntScalarParam<Int>>() {});
        }

        Set<Tile<?, BEASTState>> wiredUpTiles = new HashSet<>();
        for (var entry : compatibleTilesByType.entrySet()) {
            Utils.visitCombinations(entry.getValue(), inputTiles -> {
                Tile<?, BEASTState> tile = new ListVectorTile(inputTiles, entry.getKey());
                tile.setRootNode(node);

                int totalWeight = inputTiles.stream().mapToInt(Tile::getWeight).sum();
                tile.setWeight(totalWeight + this.getPriority().getWeight());

                wiredUpTiles.add(tile);
            });
        }

        return wiredUpTiles;
    }

    private static void addScalarFamily(
            Map<TypeToken<?>, List<Set<Tile<?, BEASTState>>>> families,
            Expr.Array array,
            List<Set<Tile<?, BEASTState>>> elementCandidates,
            TypeToken<?> family,
            TypeToken<?> literalType) {
        List<Set<Tile<?, BEASTState>>> compatible = new ArrayList<>();
        for (int i = 0; i < elementCandidates.size(); i++) {
            Set<Tile<?, BEASTState>> candidates = new HashSet<>();
            for (Tile<?, BEASTState> candidate : elementCandidates.get(i)) {
                if (family.isAssignableFrom(candidate.getTypeToken())) candidates.add(candidate);
            }
            if (candidates.isEmpty()) return;

            // A bare literal has several domain representations of the same fixed value.
            // This fallback exposes only the broad scalar family, so reuse its broad-domain
            // literal rather than multiplying these equivalent choices across a long list.
            // Named bindings and derived expressions retain all their candidate mappings.
            if (array.elements.get(i) instanceof Expr.Literal
                    && candidates.stream()
                            .anyMatch(candidate -> candidate instanceof LiteralTile<?>
                                    && literalType.equals(candidate.getTypeToken()))) {
                candidates.removeIf(candidate ->
                        candidate instanceof LiteralTile<?> && !literalType.equals(candidate.getTypeToken()));
            }
            compatible.add(candidates);
        }
        families.put(family, compatible);
    }

    private static Map<TypeToken<?>, List<Set<Tile<?, BEASTState>>>> commonElementTypes(
            List<Set<Tile<?, BEASTState>>> allPossibleInputTiles) {
        Map<TypeToken<?>, List<Set<Tile<?, BEASTState>>>> compatibleTilesByType = new HashMap<>();

        for (int index = 0; index < allPossibleInputTiles.size(); index++) {
            Map<TypeToken<?>, Set<Tile<?, BEASTState>>> tilesByType = new HashMap<>();
            for (Tile<?, BEASTState> tile : allPossibleInputTiles.get(index)) {
                tilesByType
                        .computeIfAbsent(tile.getTypeToken(), ignored -> new HashSet<>())
                        .add(tile);
            }

            if (index == 0) {
                for (Map.Entry<TypeToken<?>, Set<Tile<?, BEASTState>>> entry : tilesByType.entrySet()) {
                    compatibleTilesByType.put(entry.getKey(), new ArrayList<>(List.of(entry.getValue())));
                }
                continue;
            }

            compatibleTilesByType.keySet().retainAll(tilesByType.keySet());
            for (Map.Entry<TypeToken<?>, List<Set<Tile<?, BEASTState>>>> entry : compatibleTilesByType.entrySet()) {
                entry.getValue().add(tilesByType.get(entry.getKey()));
            }
        }

        return compatibleTilesByType;
    }

    @Override
    public Map<AstNode, Tile<?, ?>> getWiredUpInputs() throws InconsistentTilingException {
        // These children are stored in a list, not reflective TileInput fields. The global
        // consistency check must still see them to keep shared draws as one state variable.
        Map<AstNode, Tile<?, ?>> inputs = new IdentityHashMap<>();
        for (Tile<?, BEASTState> tile : inputTiles) {
            Tile<?, ?> previous = inputs.putIfAbsent(tile.getRootNode(), tile);
            if (previous != null && previous != tile) {
                throw new InconsistentTilingException(tile.getRootNode(), previous, tile);
            }
        }
        return inputs;
    }

    @Override
    public List<Object> applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        List<Object> list = new ArrayList<>();

        for (Tile<?, BEASTState> tile : inputTiles) {
            list.add(tile.apply(beastState, indexVariables));
        }

        return list;
    }

    @Override
    public TypeToken<?> getTypeToken() {
        TypeToken<?> valueType =
                elementType == null ? this.inputTiles.getFirst().getTypeToken() : elementType;
        if (valueType != null) return TypeToken.listOf(valueType);

        // we return the basic vector type
        return super.getTypeToken();
    }
}
