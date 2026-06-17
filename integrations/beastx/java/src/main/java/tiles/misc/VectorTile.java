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
import tiling.BeastXState;
import tiling.params.BeastXParam;
import tiling.params.BeastXRealVectorParam;
import tiling.params.BeastXSimplexParam;
import tiling.params.BeastXRealScalarParam;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.OptionalLong;
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
    private final Tile<?, BeastXState> singleElementTile;

    public VectorTile() {
        this(new TypeToken<>() {}, null, null);
    }

    public VectorTile(TypeToken<T> typeToken, T value) {
        this(typeToken, value, null);
    }

    public VectorTile(
            TypeToken<T> typeToken,
            T value,
            Tile<?, BeastXState> singleElementTile
    ) {
        this.typeToken = typeToken;
        this.value = value;
        this.singleElementTile = singleElementTile;
    }

    @Override
    public Set<Stochasticity> getCompatibleStochasticities() {
        return Set.of(
                Stochasticity.CONSTANT,
                Stochasticity.DETERMINISTIC,
                Stochasticity.STOCHASTIC
        );
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

        Stochasticity stochasticity =
                stochasticityResolver.getStochasticity(node);

        if (!this.getCompatibleStochasticities().contains(stochasticity)) {
            throw new FailedTilingAttempt.Rejected(
                    Stochasticity.getErrorMessage(
                            "BEAST X",
                            stochasticity,
                            this.getCompatibleStochasticities()
                    )
            );
        }

        if (array.elements.isEmpty()) {
            throw new FailedTilingAttempt.Rejected("BEAST X cannot handle empty arrays.");
        }

        if (stochasticity == Stochasticity.STOCHASTIC) {
            return buildSingleElementStochasticVectorTiles(array, allInputTiles);
        }

        return buildConstantOrDeterministicVectorTiles(array, allInputTiles);
    }

    private static Set<Tile<?, BeastXState>> buildConstantOrDeterministicVectorTiles(
            Expr.Array array,
            Map<AstNode, Set<Tile<?, BeastXState>>> allInputTiles
    ) throws FailedTilingAttempt {
        List<Map<TypeToken<?>, Tile<?, BeastXState>>> elementMaps =
                new ArrayList<>();

        for (Expr element : array.elements) {
            Map<TypeToken<?>, Tile<?, BeastXState>> typeToTile =
                    new LinkedHashMap<>();

            for (Tile<?, BeastXState> tile : allInputTiles.get(element)) {
                TypeToken<?> typeToken =
                        tile.getTypeToken();

                if (isRealScalarType(typeToken)) {
                    typeToTile.put(typeToken, tile);
                }
            }

            elementMaps.add(typeToTile);
        }

        Set<TypeToken<?>> commonTypes =
                new HashSet<>(elementMaps.getFirst().keySet());

        for (int i = 1; i < elementMaps.size(); i++) {
            commonTypes.retainAll(elementMaps.get(i).keySet());
        }

        if (commonTypes.isEmpty()) {
            throw new FailedTilingAttempt.Rejected(
                    "No common real scalar type across all array elements."
            );
        }

        Set<Tile<?, BeastXState>> vectorTiles =
                new HashSet<>();

        for (TypeToken<?> elementType : commonTypes) {
            List<? extends Tile<?, BeastXState>> elementTiles =
                    elementMaps.stream()
                            .map(map -> map.get(elementType))
                            .toList();

            vectorTiles.addAll(
                    buildLiteralVectorTiles(
                            array,
                            elementType,
                            elementTiles
                    )
            );
        }

        if (vectorTiles.isEmpty()) {
            throw new FailedTilingAttempt.Rejected(
                    "BEAST X cannot build a vector for this array."
            );
        }

        return vectorTiles;
    }

    private static Set<Tile<?, BeastXState>> buildSingleElementStochasticVectorTiles(
            Expr.Array array,
            Map<AstNode, Set<Tile<?, BeastXState>>> allInputTiles
    ) throws FailedTilingAttempt {
        if (array.elements.size() != 1) {
            throw new FailedTilingAttempt.Rejected(
                    "BEAST X currently supports stochastic vector literals only when they contain exactly one scalar parameter."
            );
        }

        Expr element =
                array.elements.getFirst();

        Set<Tile<?, BeastXState>> inputTiles =
                allInputTiles.get(element);

        if (inputTiles == null || inputTiles.isEmpty()) {
            throw new FailedTilingAttempt.Rejected(
                    "BEAST X cannot tile the stochastic vector element."
            );
        }

        Set<Tile<?, BeastXState>> vectorTiles =
                new HashSet<>();

        for (Tile<?, BeastXState> inputTile : inputTiles) {
            TypeToken<?> elementType =
                    inputTile.getTypeToken();

            if (!isRealScalarType(elementType)) {
                continue;
            }

            ParameterizedType parameterizedType =
                    (ParameterizedType) elementType.getType();

            Type domainType =
                    parameterizedType.getActualTypeArguments()[0];

            VectorTile<?> vectorTile =
                    new VectorTile<>(
                            TypeToken.parameterized(RealVector.class, domainType),
                            null,
                            inputTile
                    );

            vectorTile.setRootNode(array);
            vectorTile.setWeight(inputTile.getWeight());
            vectorTiles.add(vectorTile);
        }

        if (vectorTiles.isEmpty()) {
            throw new FailedTilingAttempt.Rejected(
                    "BEAST X stochastic vector literals must contain a real scalar parameter."
            );
        }

        return vectorTiles;
    }

    private static boolean isRealScalarType(TypeToken<?> typeToken) {
        if (!(typeToken.getType() instanceof ParameterizedType parameterizedType)) {
            return false;
        }

        if (!(parameterizedType.getRawType() instanceof Class<?> rawType)) {
            return false;
        }

        return RealScalar.class.isAssignableFrom(rawType);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<Tile<?, BeastXState>> buildLiteralVectorTiles(
            Expr.Array array,
            TypeToken<?> elementType,
            List<? extends Tile<?, BeastXState>> elementTiles
    ) {
        ParameterizedType parameterizedType =
                (ParameterizedType) elementType.getType();

        Type domainType =
                parameterizedType.getActualTypeArguments()[0];

        double[] values =
                new double[elementTiles.size()];

        Real domain =
                null;

        for (int i = 0; i < elementTiles.size(); i++) {
            RealScalar<?> scalar =
                    (RealScalar<?>) elementTiles.get(i).apply(null, new IdentityHashMap<>());

            values[i] =
                    scalar.get();

            if (domain == null) {
                domain =
                        scalar.domainType();
            }
        }

        int weight =
                elementTiles.stream()
                        .mapToInt(Tile::getWeight)
                        .sum();

        List<Tile<?, BeastXState>> tiles =
                new ArrayList<>();

        VectorTile vectorTile =
                new VectorTile(
                        TypeToken.parameterized(RealVector.class, domainType),
                        new BeastXRealVectorParam<>(values, domain)
                );

        vectorTile.setWeight(weight);
        vectorTile.setRootNode(array);
        tiles.add(vectorTile);

        if (domainType == UnitInterval.class) {
            double sum =
                    0.0;

            for (double value : values) {
                sum += value;
            }

            if (Math.abs(sum - 1.0) <= 1e-6) {
                VectorTile simplexTile =
                        new VectorTile(
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
    @SuppressWarnings("unchecked")
    public T applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        if (this.singleElementTile == null) {
            return this.value;
        }

        Object scalarObject =
                this.singleElementTile.apply(beastState, indexVariables);

        if (!(scalarObject instanceof RealScalar<?> scalar)) {
            throw new IllegalStateException(
                    "BEAST X stochastic vector literal expected a real scalar element."
            );
        }

        if (!(scalar instanceof BeastXParam beastXParam)) {
            throw new IllegalStateException(
                    "BEAST X stochastic vector literal expected a BEAST X parameter-backed scalar."
            );
        }

        return (T) new BeastXRealVectorParam<>(
                beastXParam.getParameter(),
                scalar.domainType()
        );
    }

    @Override
    public TypeToken<?> getTypeToken() {
        return this.typeToken;
    }

    @Override
    public Tile<?, BeastXState> createInstance() {
        return new VectorTile<>(new TypeToken<>() {}, null, null);
    }

    @Override
    public OptionalLong getFixedOutputSize() {
        if (this.value instanceof RealVector<?> vector) {
            return OptionalLong.of(vector.size());
        }

        if (this.singleElementTile != null) {
            return OptionalLong.of(1);
        }

        return OptionalLong.empty();
    }
}