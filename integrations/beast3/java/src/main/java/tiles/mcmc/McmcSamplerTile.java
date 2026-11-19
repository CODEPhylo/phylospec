package tiles.mcmc;

import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.Set;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.mcmc.McmcSamplerSpec;
import org.phylospec.tiling.tiles.TemplateTile;
import org.phylospec.typeresolver.Stochasticity;

/// Matches a `mcmc(...)` declaration in the `mcmc` block and registers a
/// [org.phylospec.tiling.mcmc.McmcSamplerSpec] on the state.
public class McmcSamplerTile extends TemplateTile<Void, BEASTState> {

    @Override
    protected String getPhyloSpecTemplate() {
        return """
                mcmc {
                    Sampler sampler = mcmc(
                        chainLength=$$chainLength
                    )
                }""";
    }

    public TemplateTileInput<Integer, BEASTState> chainLengthInput =
            new TemplateTileInput<>("$$chainLength", false, Set.of(Stochasticity.CONSTANT));

    @Override
    protected Void applyTile(BEASTState state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        Integer chainLength = this.chainLengthInput.apply(state, indexVariables);

        state.setSamplerSpec(chainLength != null ? new McmcSamplerSpec(chainLength) : new McmcSamplerSpec());

        return null;
    }
}
