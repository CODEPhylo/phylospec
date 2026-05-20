package tiling;

import dr.evolution.alignment.Alignment;
import dr.evolution.alignment.SitePatterns;
import dr.evomodel.branchmodel.BranchModel;
import dr.evomodel.branchratemodel.BranchRateModel;
import dr.evomodel.siteratemodel.SiteRateModel;
import dr.evomodel.tree.TreeModel;
import dr.evomodel.treelikelihood.BeagleTreeLikelihood;
import dr.evomodel.treelikelihood.PartialsRescalingScheme;
import dr.inference.model.AbstractModelLikelihood;
import dr.inference.model.Model;
import dr.inference.model.Variable;

public class BeastXPhyloCTMCLikelihoodSpec extends AbstractModelLikelihood {

    private final Alignment observedAlignment;
    private final TreeModel treeModel;
    private final BranchModel branchModel;
    private final SiteRateModel siteRateModel;
    private final BranchRateModel branchRateModel;

    public BeastXPhyloCTMCLikelihoodSpec(
            String id,
            Alignment observedAlignment,
            TreeModel treeModel,
            BranchModel branchModel,
            SiteRateModel siteRateModel,
            BranchRateModel branchRateModel
    ) {
        super(id);
        this.observedAlignment = observedAlignment;
        this.treeModel = treeModel;
        this.branchModel = branchModel;
        this.siteRateModel = siteRateModel;
        this.branchRateModel = branchRateModel;

        this.addModel(treeModel);
        this.addModel(branchModel);
        this.addModel(siteRateModel);
        this.addModel(branchRateModel);
    }

    public Alignment getObservedAlignment() {
        return observedAlignment;
    }

    public TreeModel getTreeModel() {
        return treeModel;
    }

    public BranchModel getBranchModel() {
        return branchModel;
    }

    public SiteRateModel getSiteRateModel() {
        return siteRateModel;
    }

    public BranchRateModel getBranchRateModel() {
        return branchRateModel;
    }

    public BeagleTreeLikelihood materializeBeagleTreeLikelihood() {
        SitePatterns patterns =
                new SitePatterns(this.observedAlignment);

        return new BeagleTreeLikelihood(
                patterns,
                this.treeModel,
                this.branchModel,
                this.siteRateModel,
                this.branchRateModel,
                null,
                false,
                PartialsRescalingScheme.DEFAULT,
                false
        );
    }

    @Override
    public Model getModel() {
        return this;
    }

    @Override
    public double getLogLikelihood() {
        throw new UnsupportedOperationException(
                "PhyloCTMC likelihood has not been materialized. Call materializeBeagleTreeLikelihood() before evaluation."
        );
    }

    @Override
    public void makeDirty() {
        this.fireModelChanged();
    }

    @Override
    protected void handleModelChangedEvent(Model model, Object object, int index) {
        this.fireModelChanged(object, index);
    }

    @Override
    protected void handleVariableChangedEvent(
            Variable variable,
            int index,
            Variable.ChangeType type
    ) {
        this.fireModelChanged(variable, index);
    }

    @Override
    protected void storeState() {
        // No local mutable state is stored in this lightweight specification.
    }

    @Override
    protected void restoreState() {
        // No local mutable state is stored in this lightweight specification.
    }

    @Override
    protected void acceptState() {
        // No local mutable state is stored in this lightweight specification.
    }
}
