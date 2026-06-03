package tiling.model;

import dr.evomodel.tree.TreeModel;
import dr.inference.model.AbstractModelLikelihood;

import java.util.function.Consumer;

public class BeastXTreeDistribution<L extends AbstractModelLikelihood> {

    public final L likelihood;
    public TreeModel treeModel;
    private final Consumer<TreeModel> bindTreeFunc;

    public BeastXTreeDistribution(
            L likelihood,
            TreeModel defaultTreeModel,
            Consumer<TreeModel> bindTreeFunc
    ) {
        this.likelihood = likelihood;
        this.treeModel = defaultTreeModel;
        this.bindTreeFunc = bindTreeFunc;
    }

    public void bind() {
        this.bindTreeFunc.accept(this.treeModel);
    }

    public void bind(TreeModel observedTreeModel) {
        this.bindTreeFunc.accept(observedTreeModel);
        this.treeModel = observedTreeModel;
    }
}