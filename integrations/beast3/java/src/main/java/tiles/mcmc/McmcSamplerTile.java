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
                        chainLength=$$chainLength,
                        stateFile=$$stateFile
                    )
                }""";
    }

    public TemplateTileInput<Integer, BEASTState> chainLengthInput =
            new TemplateTileInput<>("$$chainLength", false, Set.of(Stochasticity.CONSTANT));

    public TemplateTileInput<String, BEASTState> stateFileInput =
            new TemplateTileInput<>("$$stateFile", false, Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));

    @Override
    protected Void applyTile(BEASTState state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        Integer chainLength = this.chainLengthInput.apply(state, indexVariables);

        String stateFile = this.stateFileInput.apply(state, indexVariables);

        state.setSamplerSpec(new McmcSamplerSpec(
                chainLength != null ? chainLength : McmcSamplerSpec.DEFAULT_CHAIN_LENGTH,
                stateFile != null ? state.resolveOutputPath(stateFile) : null));

        return null;
    }
}
