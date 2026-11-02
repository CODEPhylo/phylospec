package tiles.functions;

import beast.base.spec.domain.NonNegativeReal;
import beast.base.spec.inference.parameter.RealScalarParam;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.Set;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;
import tiles.input.TaxonValue;

public class TaxonAgeTile extends GeneratorTile<RealScalarParam<NonNegativeReal>, BEASTState> {
    @Override
    public String getPhyloSpecGeneratorName() {
        return "age";
    }

    @Override
    public Optional<String> getNamespace() {
        return Optional.of("phylospec.functions.accessor");
    }

    GeneratorTileInput<TaxonValue, BEASTState> taxonInput =
            new GeneratorTileInput<>("taxon", Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));

    @Override
    public RealScalarParam<NonNegativeReal> applyTile(
            BEASTState state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        return new RealScalarParam<>(taxonInput.apply(state, indexVariables).age(), NonNegativeReal.INSTANCE);
    }
}
