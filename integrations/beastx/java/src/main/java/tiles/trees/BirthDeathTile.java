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
import org.phylospec.domain.UnitInterval;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.RealScalar;
import tiling.BeastXRealScalarParam;
import tiling.BeastXState;
import tiling.BeastXTreeDistribution;

import java.util.IdentityHashMap;

public class BirthDeathTile extends GeneratorTile<
        BeastXTreeDistribution<SpeciationLikelihood>,
        BeastXState
        > {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "BirthDeath";
    }

    GeneratorTileInput<RealScalar<? extends PositiveReal>, BeastXState> diversificationRateInput =
            new GeneratorTileInput<>("diversificationRate");

    GeneratorTileInput<RealScalar<? extends PositiveReal>, BeastXState> turnoverInput =
            new GeneratorTileInput<>("turnover");

    GeneratorTileInput<RealScalar<UnitInterval>, BeastXState> samplingProbabilityInput =
            new GeneratorTileInput<>("samplingProbability", false);

    GeneratorTileInput<Taxa, BeastXState> taxaInput =
            new GeneratorTileInput<>("taxa");

    @Override
    public BeastXTreeDistribution<SpeciationLikelihood> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        RealScalar<? extends PositiveReal> diversificationRate =
                this.diversificationRateInput.apply(beastState, indexVariables);

        RealScalar<? extends PositiveReal> turnover =
                this.turnoverInput.apply(beastState, indexVariables);

        RealScalar<UnitInterval> samplingProbability =
                this.samplingProbabilityInput.apply(beastState, indexVariables);

        Taxa taxa =
                this.taxaInput.apply(beastState, indexVariables);

        DefaultTreeModel defaultTreeModel =
                new DefaultTreeModel("tree", createInitialTree(taxa));

        Parameter samplingProbabilityParameter =
                samplingProbability == null
                        ? new Parameter.Default(1.0)
                        : toParameter(samplingProbability);

        BirthDeathGernhard08Model birthDeathModel =
                new BirthDeathGernhard08Model(
                        toParameter(diversificationRate),
                        toParameter(turnover),
                        samplingProbabilityParameter,
                        BirthDeathGernhard08Model.TreeType.LABELED,
                        Units.Type.YEARS
                );

        SpeciationLikelihood likelihood =
                new SpeciationLikelihood(
                        defaultTreeModel,
                        birthDeathModel,
                        "birthDeathPrior"
                );

        return new BeastXTreeDistribution<>(
                likelihood,
                defaultTreeModel,
                treeModel -> {
                    // SpeciationLikelihood receives the tree in its constructor.
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
            throw new IllegalArgumentException("BirthDeath requires at least two taxa.");
        }

        SimpleNode root =
                buildBalancedSubtree(taxa, 0, taxa.getTaxonCount());

        root.setHeight(Math.max(root.getHeight(), 1.0));

        return new SimpleTree(root);
    }

    private static SimpleNode buildBalancedSubtree(
            Taxa taxa,
            int from,
            int to
    ) {
        if (to - from == 1) {
            SimpleNode leaf =
                    new SimpleNode();

            leaf.setTaxon(taxa.getTaxon(from));
            leaf.setHeight(0.0);

            return leaf;
        }

        int mid =
                from + (to - from) / 2;

        SimpleNode left =
                buildBalancedSubtree(taxa, from, mid);

        SimpleNode right =
                buildBalancedSubtree(taxa, mid, to);

        SimpleNode parent =
                new SimpleNode();

        parent.addChild(left);
        parent.addChild(right);
        parent.setHeight(Math.max(left.getHeight(), right.getHeight()) + 1.0);

        return parent;
    }
}