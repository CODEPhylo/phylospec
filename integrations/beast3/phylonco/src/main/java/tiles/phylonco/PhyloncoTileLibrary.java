package tiles.phylonco;

import beast.base.inference.Distribution;
import beast.base.inference.Scalable;
import beast.base.inference.StateNode;
import beast.base.spec.evolution.operator.AdaptableVarianceMultivariateNormalOperator;
import beast.base.spec.evolution.operator.UpDownOperator;
import beast.base.spec.inference.operator.Transform;
import beast.base.spec.type.Tensor;
import beastconfig.BEASTState;
import java.util.ArrayList;
import java.util.List;
import org.phylospec.annotations.ComponentSource;
import org.phylospec.tiling.TileLibrary;
import org.phylospec.tiling.tiles.CandidateTile;
import org.phylospec.tiling.tiles.TilePriority;
import phylonco.beast.evolution.likelihood.ReadCountTreeLikelihood;
import phylonco.beast.evolution.readcountmodel.LikelihoodReadCountModel;

/** BEAST 3 tiles for beast-phylonco: mpileup read counts, GT10 and the read-count tree likelihood. */
@ComponentSource("/phylonco-components.json")
public final class PhyloncoTileLibrary extends TileLibrary<BEASTState> {

    @Override
    public String getId() {
        return "phylonco";
    }

    @Override
    public Class<BEASTState> getStateType() {
        return BEASTState.class;
    }

    @Override
    public List<CandidateTile<BEASTState>> getTiles() {
        return List.of(
                new FromMpileupTile(),
                new ReadCountAccessorTiles.Taxa(),
                new ReadCountAccessorTiles.NumTaxa(),
                new ReadCountAccessorTiles.NumSites(),
                new GT10Tile(),
                new ReadCountModelTile(),
                new GenotypeReadCountModelTile(),
                new ReadCountModelErrorTile(),
                new ObservedAsReadCountTile());
    }

    @Override
    public List<String> getEngineSpecificationResources() {
        return List.of("/META-INF/phylospec/engines/phylonco.json");
    }

    /**
     * The read-count likelihood depends on t, v and s only through t * s and v * s^2, so single
     * scale moves barely travel along this ridge. As in LPhyBEAST, t and v are scaled together,
     * once on their own and once against s, and an adaptive multivariate normal move learns how
     * t, s and v vary together.
     */
    @Override
    public void configureState(BEASTState state) {
        for (Distribution likelihood : state.likelihoodDistributions) {
            if (!(likelihood instanceof ReadCountTreeLikelihood treeLikelihood)) {
                continue;
            }
            LikelihoodReadCountModel model = treeLikelihood.readCountModelInput.get();
            List<StateNode> up = sampled(state, model.tInput.get(), model.vInput.get());
            List<StateNode> down = sampled(state, model.sInput.get());
            if (up.size() == 2) {
                addUpDown(state, up, List.of(), "readCountModel.tv");
            }
            if (!up.isEmpty() && !down.isEmpty()) {
                addUpDown(state, up, down, "readCountModel.stv");
            }
            List<StateNode> joint = sampled(state, model.tInput.get(), model.sInput.get(), model.vInput.get());
            if (joint.size() >= 2) {
                addAdaptiveMultivariateNormal(state, joint);
            }
        }
    }

    /** The given positive parameters that MCMC samples; a fixed value must not get a proposal. */
    private static List<StateNode> sampled(BEASTState state, Object... parameters) {
        List<StateNode> sampled = new ArrayList<>();
        for (Object parameter : parameters) {
            if (parameter instanceof StateNode node
                    && parameter instanceof Scalable
                    && parameter instanceof Tensor<?, ?>
                    && state.stateNodes.containsKey(node)) {
                sampled.add(node);
            }
        }
        return sampled;
    }

    private static void addUpDown(BEASTState state, List<StateNode> up, List<StateNode> down, String id) {
        UpDownOperator operator = new UpDownOperator();
        operator.setID(state.getAvailableID(id));
        up.forEach(parameter -> operator.setInputValue("up", parameter));
        down.forEach(parameter -> operator.setInputValue("down", parameter));
        operator.setInputValue("scaleFactor", 0.75);
        operator.setInputValue("weight", 15.0);
        state.initBEASTObject(operator);
        state.addOperator(operator, List.of());
    }

    /** Proposes all parameters at once on the log scale, with the settings LPhyBEAST uses. */
    private static void addAdaptiveMultivariateNormal(BEASTState state, List<StateNode> parameters) {
        AdaptableVarianceMultivariateNormalOperator operator = new AdaptableVarianceMultivariateNormalOperator();
        operator.setID(state.getAvailableID("readCountModel.AVMN"));
        parameters.forEach(parameter ->
                operator.setInputValue("transformations", new Transform.LogTransform((Tensor<?, ?>) parameter)));
        operator.setInputValue("beta", 0.05);
        operator.setInputValue("burnin", 100);
        operator.setInputValue("initial", 200);
        operator.setInputValue("weight", 25.0);
        state.initBEASTObject(operator);
        state.addOperator(operator, List.of());
    }
}
