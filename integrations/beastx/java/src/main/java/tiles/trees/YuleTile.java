package tiles.trees;

import dr.evolution.tree.SimpleNode;
import dr.evolution.tree.SimpleTree;
import dr.evolution.util.Taxa;
import dr.evolution.util.Units;
import dr.evomodel.speciation.BirthDeathGernhard08Model;
import dr.evomodel.speciation.SpeciationLikelihood;
import dr.evomodel.tree.DefaultTreeModel;
import dr.inference.model.Parameter;
import org.phylospec.ast.Expr;
import org.phylospec.domain.PositiveReal;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.RealScalar;
import tiling.BeastXRealScalarParam;
import tiling.BeastXState;
import tiling.BeastXTreeDistribution;

import java.util.IdentityHashMap;

public class YuleTile extends GeneratorTile<
        BeastXTreeDistribution<SpeciationLikelihood>,
        BeastXState
        > {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "Yule";
    }

    GeneratorTileInput<RealScalar<? extends PositiveReal>, BeastXState> birthRateInput =
            new GeneratorTileInput<>("birthRate");

    GeneratorTileInput<Taxa, BeastXState> taxaInput =
            new GeneratorTileInput<>("taxa");

    @Override
    public BeastXTreeDistribution<SpeciationLikelihood> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        RealScalar<? extends PositiveReal> birthRate =
                this.birthRateInput.apply(beastState, indexVariables);

        Taxa taxa =
                this.taxaInput.apply(beastState, indexVariables);

        DefaultTreeModel defaultTreeModel =
                new DefaultTreeModel("tree", createInitialTree(taxa));

        BirthDeathGernhard08Model yuleModel =
                new BirthDeathGernhard08Model(
                        toParameter(birthRate),
                        new Parameter.Default(0.0),
                        new Parameter.Default(1.0),
                        BirthDeathGernhard08Model.TreeType.LABELED,
                        Units.Type.YEARS
                );

        SpeciationLikelihood likelihood =
                new SpeciationLikelihood(
                        defaultTreeModel,
                        yuleModel,
                        "yulePrior"
                );

        return new BeastXTreeDistribution<>(
                likelihood,
                defaultTreeModel,
                treeModel -> {
                    // SpeciationLikelihood receives the tree in its constructor.
                    // This hook exists so TreeDrawTile has the same bind pattern as scalar draws.
                }
        );
    }

    private static Parameter toParameter(RealScalar<?> scalar) {
        if (scalar instanceof BeastXRealScalarParam<?> beastXScalar) {
            return beastXScalar.getParameter();
        }

        return new Parameter.Default(scalar.get());
    }

    private static SimpleTree createInitialTree(Taxa taxa) {
        if (taxa.getTaxonCount() < 2) {
            throw new IllegalArgumentException("Yule requires at least two taxa.");
        }

        SimpleNode root = buildBalancedSubtree(taxa, 0, taxa.getTaxonCount(), 0.0);
        root.setHeight(Math.max(root.getHeight(), 1.0));

        return new SimpleTree(root);
    }

    private static SimpleNode buildBalancedSubtree(
            Taxa taxa,
            int from,
            int to,
            double height
    ) {
        if (to - from == 1) {
            SimpleNode leaf = new SimpleNode();
            leaf.setTaxon(taxa.getTaxon(from));
            leaf.setHeight(0.0);
            return leaf;
        }

        int mid = from + (to - from) / 2;

        SimpleNode left =
                buildBalancedSubtree(taxa, from, mid, height);

        SimpleNode right =
                buildBalancedSubtree(taxa, mid, to, height);

        SimpleNode parent = new SimpleNode();
        parent.addChild(left);
        parent.addChild(right);
        parent.setHeight(Math.max(left.getHeight(), right.getHeight()) + 1.0);

        return parent;
    }
}