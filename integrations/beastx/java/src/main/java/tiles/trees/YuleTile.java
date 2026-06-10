package tiles.trees;

import dr.evolution.util.Taxa;
import dr.evolution.util.Units;
import dr.evomodel.speciation.BirthDeathGernhard08Model;
import dr.evomodel.speciation.SpeciationLikelihood;
import dr.evomodel.tree.DefaultTreeModel;
import dr.inference.model.Parameter;
import org.phylospec.ast.Expr;
import org.phylospec.domain.NonNegativeReal;
import org.phylospec.domain.PositiveReal;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.RealScalar;
import tiling.params.BeastXRealScalarParam;
import tiling.BeastXState;
import tiling.model.TreeDistribution;

import java.util.IdentityHashMap;

public class YuleTile extends GeneratorTile<
        TreeDistribution<SpeciationLikelihood>,
        BeastXState
        > {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "Yule";
    }

    GeneratorTileInput<RealScalar<? extends PositiveReal>, BeastXState> birthRateInput =
            new GeneratorTileInput<>("birthRate");

    GeneratorTileInput<RealScalar<? extends NonNegativeReal>, BeastXState> rootAgeInput =
            new GeneratorTileInput<>("rootAge", false);

    GeneratorTileInput<Taxa, BeastXState> taxaInput =
            new GeneratorTileInput<>("taxa");

    @Override
    public TreeDistribution<SpeciationLikelihood> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        RealScalar<? extends PositiveReal> birthRate =
                this.birthRateInput.apply(beastState, indexVariables);

        RealScalar<? extends NonNegativeReal> rootAge =
                this.rootAgeInput.apply(beastState, indexVariables);

        Taxa taxa =
                this.taxaInput.apply(beastState, indexVariables);

        DefaultTreeModel defaultTreeModel =
                new DefaultTreeModel(
                        "tree",
                        InitialTreeBuilder.balancedTree(taxa, "Yule", rootAge)
                );

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

        return new TreeDistribution<>(
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
}