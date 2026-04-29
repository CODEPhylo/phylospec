package tiles.misc;

import beast.base.spec.domain.*;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.inference.parameter.IntVectorParam;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.inference.parameter.RealVectorParam;
import beast.base.spec.inference.parameter.SimplexParam;
import org.phylospec.ast.AstNode;
import org.phylospec.ast.Expr;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.VariableResolver;
import tiles.AstNodeTile;
import beastconfig.BEASTState;
import tiling.FailedTilingAttempt;
import tiling.Tile;
import tiling.TypeToken;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

public class VectorTile<T> extends AstNodeTile<T, Expr.Array> {
    private final TypeToken<T> typeToken;
    private final T value;

    public VectorTile() {
        this(new TypeToken<>() {
        }, null);
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
    public Set<Tile<?>> tryToTile(AstNode node, Map<AstNode, Set<Tile<?>>> allInputTiles, VariableResolver variableResolver, StochasticityResolver stochasticityResolver) throws FailedTilingAttempt {
        if (!(node instanceof Expr.Array array)) throw new FailedTilingAttempt.Irrelevant();

        Stochasticity stochasticity = stochasticityResolver.getStochasticity(node);
        if (!this.getCompatibleStochasticities().contains(stochasticity)) {
            throw new FailedTilingAttempt.Rejected(
                    Stochasticity.getErrorMessage("BEAST 2.8", stochasticity, this.getCompatibleStochasticities())
            );
        }

        if (array.elements.isEmpty()) {
            throw new FailedTilingAttempt.Rejected("BEAST 2.8 cannot handle empty arrays.");
        }

        // for each element, build a map from scalar-param type token to tile

        List<Map<TypeToken<?>, Tile<?>>> elementMaps = new ArrayList<>();
        for (Expr element : array.elements) {
            Map<TypeToken<?>, Tile<?>> typeToTile = new LinkedHashMap<>();

            for (Tile<?> tile : allInputTiles.get(element)) {
                TypeToken<?> tt = tile.getTypeToken();
                if (isScalarParamType(tt)) typeToTile.put(tt, tile);
            }

            elementMaps.add(typeToTile);
        }

        // intersect to find type tokens present in every element

        Set<TypeToken<?>> commonTypes = new HashSet<>(elementMaps.get(0).keySet());
        for (int i = 1; i < elementMaps.size(); i++) {
            commonTypes.retainAll(elementMaps.get(i).keySet());
        }

        if (commonTypes.isEmpty()) {
            throw new FailedTilingAttempt.Rejected("No common scalar-param type across all array elements.");
        }

        // for each common type token, we create a VectorTile tile

        Set<Tile<?>> vectorTiles = new HashSet<>();
        for (TypeToken<?> elementType : commonTypes) {
            List<? extends Tile<?>> elementTiles = elementMaps.stream()
                    .map(m -> m.get(elementType))
                    .toList();
            vectorTiles.addAll(buildVectorTile(array, elementType, elementTiles));
        }

        if (vectorTiles.isEmpty()) {
            throw new FailedTilingAttempt.Rejected("BEAST 2.8 cannot build a vector for this array.");
        }

        return vectorTiles;
    }

    private static boolean isScalarParamType(TypeToken<?> tt) {
        if (!(tt.getType() instanceof ParameterizedType pt)) return false;
        Class<?> raw = (Class<?>) pt.getRawType();
        return raw == RealScalarParam.class || raw == IntScalarParam.class;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<Tile<?>> buildVectorTile(Expr.Array array, TypeToken<?> elementType, List<? extends Tile<?>> elementTiles) {
        ParameterizedType pt = (ParameterizedType) elementType.getType();
        Class<?> raw = (Class<?>) pt.getRawType();
        Type domainType = pt.getActualTypeArguments()[0];
        int weight = elementTiles.stream().mapToInt(Tile::getWeight).sum();

        if (raw == RealScalarParam.class) {
            double[] values = new double[elementTiles.size()];
            Real domain = null;

            for (int i = 0; i < elementTiles.size(); i++) {
                RealScalarParam<?> scalar = (RealScalarParam<?>) elementTiles.get(i).apply(null, new IdentityHashMap<>());
                values[i] = scalar.get();
                if (domain == null) domain = scalar.getDomain();
            }

            List<Tile<?>> tiles = new ArrayList<>();

            // always produce the plain real vector
            VectorTile vectorTile = new VectorTile(TypeToken.parameterized(RealVectorParam.class, domainType), new RealVectorParam<>(values, domain));
            vectorTile.setWeight(weight);
            vectorTile.setRootNode(array);
            tiles.add(vectorTile);

            // also produce a simplex when elements are unit-interval values summing to 1.0
            if (domainType == UnitInterval.class) {
                double sum = 0;
                for (double v : values) sum += v;
                if (Math.abs(sum - 1.0) <= 1e-6) {
                    VectorTile simplexTile = new VectorTile(TypeToken.of(SimplexParam.class), new SimplexParam(values));
                    simplexTile.setWeight(weight);
                    simplexTile.setRootNode(array);
                    tiles.add(simplexTile);
                }
            }

            return tiles;
        }

        if (raw == IntScalarParam.class) {
            int[] values = new int[elementTiles.size()];
            Int domain = null;

            for (int i = 0; i < elementTiles.size(); i++) {
                IntScalarParam<?> scalar = (IntScalarParam<?>) elementTiles.get(i).apply(null, new IdentityHashMap<>());
                values[i] = scalar.get();
                if (domain == null) domain = scalar.getDomain();
            }

            VectorTile tile = new VectorTile(TypeToken.parameterized(IntVectorParam.class, domainType), new IntVectorParam<>(values, domain));
            tile.setWeight(weight);
            tile.setRootNode(array);

            return List.of(tile);
        }

        return List.of();
    }

    @Override
    public T applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        return this.value;
    }

    @Override
    public TypeToken<?> getTypeToken() {
        return this.typeToken;
    }

    @Override
    public Tile<?> createInstance() {
        return new VectorTile<>(new TypeToken<>() {
        }, null);
    }
}
