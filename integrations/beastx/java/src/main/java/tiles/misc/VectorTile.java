package tiles.misc;

import org.phylospec.ast.AstNode;
import org.phylospec.ast.Expr;
import org.phylospec.domain.Real;
import org.phylospec.domain.UnitInterval;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.errors.FailedTilingAttempt;
import org.phylospec.tiling.tiles.AstNodeTile;
import org.phylospec.tiling.tiles.Tile;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.VariableResolver;
import org.phylospec.types.RealScalar;
import org.phylospec.types.RealVector;
import tiling.BeastXRealVectorParam;
import tiling.BeastXSimplexParam;
import tiling.BeastXState;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class VectorTile<T> extends AstNodeTile<T, Expr.Array, BeastXState> {

    private final TypeToken<T> typeToken;
    private final T value;

    public VectorTile() {
        this(new TypeToken<>() {}, null);
    }

    public VectorTile(TypeToken<T> typeToken, T value) {
        this.typeToken = typeToken;
        this.value = value;
    }

    @Override
    public Set<Stochasticity> getCompatibleStochasticities() {
        return Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC);
    }

    @Override
    public Set<Tile<?, BeastXState>> tryToTile(
            AstNode node,
            Map<AstNode, Set<Tile<?, BeastXState>>> allInputTiles,
            VariableResolver variableResolver,
            StochasticityResolver stochasticityResolver
    ) throws FailedTilingAttempt {
        if (!(node instanceof Expr.Array array)) {
            throw new FailedTilingAttempt.Irrelevant();
        }

        Stochasticity stochasticity = stochasticityResolver.getStochasticity(node);
        if (!this.getCompatibleStochasticities().contains(stochasticity)) {
            throw new FailedTilingAttempt.Rejected(
                    Stochasticity.getErrorMessage("BEAST X", stochasticity, this.getCompatibleStochasticities())
            );
        }

        if (array.elements.isEmpty()) {
            throw new FailedTilingAttempt.Rejected("BEAST X cannot handle empty arrays.");
        }

        List<Map<TypeToken<?>, Tile<?, BeastXState>>> elementMaps = new ArrayList<>();
        for (Expr element : array.elements) {
            Map<TypeToken<?>, Tile<?, BeastXState>> typeToTile = new LinkedHashMap<>();

            for (Tile<?, BeastXState> tile : allInputTiles.get(element)) {
                TypeToken<?> typeToken = tile.getTypeToken();
                if (isRealScalarType(typeToken)) {
                    typeToTile.put(typeToken, tile);
                }
            }

            elementMaps.add(typeToTile);
        }

        Set<TypeToken<?>> commonTypes = new HashSet<>(elementMaps.get(0).keySet());
        for (int i = 1; i < elementMaps.size(); i++) {
            commonTypes.retainAll(elementMaps.get(i).keySet());
        }

        if (commonTypes.isEmpty()) {
            throw new FailedTilingAttempt.Rejected("No common real scalar type across all array elements.");
        }

        Set<Tile<?, BeastXState>> vectorTiles = new HashSet<>();
        for (TypeToken<?> elementType : commonTypes) {
            List<? extends Tile<?, BeastXState>> elementTiles = elementMaps.stream()
                    .map(map -> map.get(elementType))
                    .toList();

            vectorTiles.addAll(buildVectorTiles(array, elementType, elementTiles));
        }

        if (vectorTiles.isEmpty()) {
            throw new FailedTilingAttempt.Rejected("BEAST X cannot build a vector for this array.");
        }

        return vectorTiles;
    }

    private static boolean isRealScalarType(TypeToken<?> typeToken) {
        if (!(typeToken.getType() instanceof ParameterizedType parameterizedType)) {
            return false;
        }

        return parameterizedType.getRawType() == RealScalar.class;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<Tile<?, BeastXState>> buildVectorTiles(
            Expr.Array array,
            TypeToken<?> elementType,
            List<? extends Tile<?, BeastXState>> elementTiles
    ) {
        ParameterizedType parameterizedType = (ParameterizedType) elementType.getType();
        Type domainType = parameterizedType.getActualTypeArguments()[0];

        double[] values = new double[elementTiles.size()];
        Real domain = null;

        for (int i = 0; i < elementTiles.size(); i++) {
            RealScalar<?> scalar =
                    (RealScalar<?>) elementTiles.get(i).apply(null, new IdentityHashMap<>());

            values[i] = scalar.get();

            if (domain == null) {
                domain = scalar.domainType();
            }
        }

        int weight = elementTiles.stream()
                .mapToInt(Tile::getWeight)
                .sum();

        List<Tile<?, BeastXState>> tiles = new ArrayList<>();

        VectorTile vectorTile = new VectorTile(
                TypeToken.parameterized(RealVector.class, domainType),
                new BeastXRealVectorParam<>(values, domain)
        );
        vectorTile.setWeight(weight);
        vectorTile.setRootNode(array);
        tiles.add(vectorTile);

        if (domainType == UnitInterval.class) {
            double sum = 0.0;
            for (double value : values) {
                sum += value;
            }

            if (Math.abs(sum - 1.0) <= 1e-6) {
                VectorTile simplexTile = new VectorTile(
                        TypeToken.of(BeastXSimplexParam.class),
                        new BeastXSimplexParam(values)
                );
                simplexTile.setWeight(weight);
                simplexTile.setRootNode(array);
                tiles.add(simplexTile);
            }
        }

        return tiles;
    }

    @Override
    public T applyTile(BeastXState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        return this.value;
    }

    @Override
    public TypeToken<?> getTypeToken() {
        return this.typeToken;
    }

    @Override
    public Tile<?, BeastXState> createInstance() {
        return new VectorTile<>(new TypeToken<>() {}, null);
    }
}
