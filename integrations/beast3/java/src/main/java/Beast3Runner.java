import beast.base.inference.MCMC;
import beastconfig.BEASTState;
import beastconfig.MCMCAssembler;
import org.phylospec.tiling.tiles.CandidateTile;
import org.xml.sax.SAXException;
import tiles.BeastCoreTileLibrary;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.List;

/// Runs a PhyloSpec model against the BEAST 3 engine.
///
/// Builds up a {@link BEASTState} by tiling the PhyloSpec AST, hands it to
/// {@link MCMCAssembler} to materialize a runnable {@link MCMC} object, and then runs it.
public class BEASTMCMCRunner extends PhyloSpecRunner<BEASTState, MCMC> {

    /**
     * Constructs a runner for the given PhyloSpec source code, using the default chain length.
     */
    public BEASTMCMCRunner(String source) {
        super(source);
    }

    /**
     * Constructs a runner for the given PhyloSpec source code, using the given default chain length.
     */
    public BEASTMCMCRunner(String source, long defaultChainLength) {
        super(source, defaultChainLength);
    }

    @Override
    protected BEASTState createState(String runName) {
        return new BEASTState(runName, this.defaultChainLength);
    }

    @Override
    protected List<CandidateTile<BEASTState>> getTileLibrary() {
        return new BeastCoreTileLibrary().getTiles();
    }

    @Override
    protected MCMC buildEngineObjects(BEASTState state) {
        return MCMCAssembler.assemble(state);
    }

    @Override
    protected void runEngineObjects(MCMC mcmc) {
        try {
            mcmc.run();
        } catch (IOException | SAXException | ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
    }
}
