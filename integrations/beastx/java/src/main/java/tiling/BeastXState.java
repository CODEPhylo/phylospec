package tiling;

import dr.evomodel.tree.TreeModel;
import dr.inference.distribution.AbstractDistributionLikelihood;
import dr.inference.model.AbstractModelLikelihood;
import dr.inference.model.Parameter;
import org.phylospec.tiling.TypeToken;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BeastXState {

    public final String runName;

    public final Map<Parameter, TypeToken<?>> stateNodes;
    public final Map<Parameter, AbstractDistributionLikelihood> priorDistributions;
    public final Map<TreeModel, AbstractModelLikelihood> treePriorDistributions;
    public final List<AbstractModelLikelihood> likelihoodDistributions;

    private final Set<String> ids;

    public BeastXState(String runName) {
        this.runName = runName;
        this.stateNodes = new HashMap<>();
        this.priorDistributions = new HashMap<>();
        this.treePriorDistributions = new HashMap<>();
        this.likelihoodDistributions = new ArrayList<>();
        this.ids = new HashSet<>();
    }

    public String getAvailableID(String proposal) {
        if (!this.ids.contains(proposal)) {
            this.ids.add(proposal);
            return proposal;
        }

        int prefix = 2;
        while (this.ids.contains(proposal + "_" + prefix)) {
            prefix++;
        }

        proposal = proposal + "_" + prefix;
        this.ids.add(proposal);
        return proposal;
    }

    public void addStateNode(BeastXParam stateNode, TypeToken<?> typeToken, String id) {
        Parameter parameter = stateNode.getParameter();
        parameter.setId(this.getAvailableID(id));
        this.stateNodes.put(parameter, typeToken);
    }

    public void addPriorDistribution(
            BeastXParam stateNode,
            AbstractDistributionLikelihood distribution,
            String id
    ) {
        Parameter parameter = stateNode.getParameter();
        distribution.setId(this.getAvailableID(id));
        this.priorDistributions.put(parameter, distribution);
    }

    public void addTreePriorDistribution(
            TreeModel treeModel,
            AbstractModelLikelihood likelihood,
            String id
    ) {
        treeModel.setId(this.getAvailableID(id));
        likelihood.setId(this.getAvailableID(id + "_prior"));
        this.treePriorDistributions.put(treeModel, likelihood);
    }

    public void addLikelihoodDistribution(
            AbstractModelLikelihood likelihood,
            String id
    ) {
        likelihood.setId(this.getAvailableID(id));
        this.likelihoodDistributions.add(likelihood);
    }
}