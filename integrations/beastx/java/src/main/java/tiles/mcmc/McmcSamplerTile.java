package tiles.mcmc;

import org.phylospec.ast.Expr;
import org.phylospec.tiling.mcmc.McmcSamplerSpec;
import org.phylospec.tiling.tiles.TemplateTile;
import org.phylospec.typeresolver.Stochasticity;
import tiling.BeastXState;

import java.util.IdentityHashMap;
import java.util.Set;

/// Matches a `mcmc(...)` declaration in the `mcmc` block and registers a
/// [org.phylospec.tiling.mcmc.McmcSamplerSpec] on the state.
public class McmcSamplerTile extends TemplateTile<Void, BeastXState> {

    @Override
    protected String getPhyloSpecTemplate() {
        return """
                mcmc {
                    Sampler sampler = mcmc(
                        chainLength=$$chainLength
                    )
                }""";
    }

    public TemplateTileInput<Integer, BeastXState> chainLengthInput =
            new TemplateTileInput<>("$$chainLength", false, Set.of(Stochasticity.CONSTANT));

    @Override
    protected Void applyTile(BeastXState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        Integer chainLength = this.chainLengthInput.apply(beastState, indexVariables);

        beastState.setSamplerSpec(chainLength != null ? new McmcSamplerSpec(chainLength) : new McmcSamplerSpec());

        return null;
    }
}
