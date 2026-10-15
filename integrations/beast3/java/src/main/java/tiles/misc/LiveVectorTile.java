package tiles.misc;

import beast.base.spec.domain.Real;
import beast.base.spec.type.RealScalar;
import beast.base.spec.type.RealVector;
import beastconfig.BEASTState;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.phylospec.Utils;
import org.phylospec.ast.AstNode;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.errors.FailedTilingAttempt;
import org.phylospec.tiling.errors.InconsistentTilingException;
import org.phylospec.tiling.tiles.AstNodeTile;
import org.phylospec.tiling.tiles.Tile;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.VariableResolver;

/** Preserves dependencies when an array contains estimated or computed real scalars. */
public final class LiveVectorTile extends AstNodeTile<RealVector<?>, Expr.Array, BEASTState> {
    private final Tile<?, BEASTState> elements;
    private final TypeToken<?> type;

    public LiveVectorTile() {
        this(null, TypeToken.of(RealVector.class));
    }

    private LiveVectorTile(Tile<?, BEASTState> elements, TypeToken<?> type) {
        this.elements = elements;
        this.type = type;
    }

    @Override
    public Set<Stochasticity> getCompatibleStochasticities() {
        return Set.of(Stochasticity.DETERMINISTIC, Stochasticity.STOCHASTIC);
    }

    @Override
    public Set<Tile<?, BEASTState>> tryToTile(
            AstNode node,
            Map<AstNode, Set<Tile<?, BEASTState>>> allInputTiles,
            VariableResolver variables,
            StochasticityResolver stochasticity)
            throws FailedTilingAttempt {
        if (!(node instanceof Expr.Array array)
                || !getCompatibleStochasticities().contains(stochasticity.getStochasticity(node))) {
            throw new FailedTilingAttempt.Irrelevant();
        }
        if (array.elements.isEmpty()) throw new FailedTilingAttempt.Rejected("BEAST 2.8 cannot handle empty arrays.");
        Set<Tile<?, BEASTState>> result = new HashSet<>();
        Set<TypeToken<?>> domains = new HashSet<>();
        for (Tile<?, BEASTState> first : allInputTiles.get(array.elements.getFirst())) {
            TypeToken<?> domain = TypeToken.firstConcreteTypeArg(first.getTypeToken());
            if (domain != null
                    && TypeToken.of(Real.class).isAssignableFrom(domain)
                    && TypeToken.parameterized(RealScalar.class, domain.getType())
                            .isAssignableFrom(first.getTypeToken())) {
                domains.add(domain);
            }
        }
        // Match RealScalar<D>, not its concrete implementation. The exact domain D is retained:
        // a signed scalar must not become positive merely because its current value is positive.
        for (TypeToken<?> domain : domains) {
            TypeToken<?> scalarType = TypeToken.parameterized(RealScalar.class, domain.getType());
            List<Set<Tile<?, BEASTState>>> compatibleInputs = new ArrayList<>();
            for (Expr element : array.elements) {
                Set<Tile<?, BEASTState>> candidates = new HashSet<>();
                for (Tile<?, BEASTState> candidate : allInputTiles.get(element)) {
                    if (scalarType.isAssignableFrom(candidate.getTypeToken())) candidates.add(candidate);
                }
                if (candidates.isEmpty()) break;
                compatibleInputs.add(candidates);
            }
            if (compatibleInputs.size() != array.elements.size()) continue;
            Utils.visitCombinations(compatibleInputs, inputs -> {
                // Reuse list application and shared-input checks, but not its concrete element type.
                ListVectorTile list = new ListVectorTile(inputs);
                list.setRootNode(node);
                list.setWeight(inputs.stream().mapToInt(Tile::getWeight).sum()
                        + list.getPriority().getWeight());
                LiveVectorTile tile =
                        new LiveVectorTile(list, TypeToken.parameterized(RealVector.class, domain.getType()));
                tile.setRootNode(node);
                tile.setWeight(list.getWeight() + getPriority().getWeight());
                result.add(tile);
            });
        }
        return result;
    }

    @Override
    @SuppressWarnings("unchecked") // The list/domain pair was checked when this candidate was built.
    public RealVector<?> applyTile(BEASTState state, IdentityHashMap<Expr.Variable, Integer> indices) {
        elements.setIndexVariables(getIndexVariables());
        List<RealScalar<Real>> values = (List<RealScalar<Real>>) elements.apply(state, indices);
        ScalarVector<Real> vector = new ScalarVector<>();
        state.setInput(vector, vector.valuesInput, values);
        state.initBEASTObject(vector);
        return vector;
    }

    @Override
    public Map<AstNode, Tile<?, ?>> getWiredUpInputs() throws InconsistentTilingException {
        // The list tile covers the same array node; expose its element dependencies.
        return elements == null ? Map.of() : elements.getWiredUpInputs();
    }

    @Override
    public TypeToken<?> getTypeToken() {
        return type;
    }
}
