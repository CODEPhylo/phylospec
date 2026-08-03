package tiles.trees;

import dr.evolution.util.Taxa;
import dr.evolution.util.Units;
import dr.evomodel.speciation.BirthDeathGernhard08Model;
import dr.evomodel.speciation.SpeciationLikelihood;
import dr.evomodel.tree.DefaultTreeModel;
import dr.evomodel.tree.TreeModel;
import dr.inference.model.Parameter;
import org.phylospec.ast.Expr;
import org.phylospec.domain.NonNegativeReal;
import org.phylospec.domain.PositiveReal;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.RealScalar;
import tiling.BeastXState;
import tiling.model.BoundDistribution;
import tiling.params.BeastXParameters;

import java.util.IdentityHashMap;

public class YuleTile extends GeneratorTile<
        BoundDistribution<TreeModel, SpeciationLikelihood>,
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
    public BoundDistribution<TreeModel, SpeciationLikelihood> applyTile(
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
                        BeastXParameters.toParameter(birthRate),
                        new Parameter.Default(0.0),
                        new Parameter.Default(1.0),
                        BirthDeathGernhard08Model.TreeType.UNSCALED,
                        Units.Type.YEARS
                );

        return new BoundDistribution<>(
                defaultTreeModel,
                treeModel -> {
                    return new SpeciationLikelihood(
                            treeModel,
                            yuleModel,
                            "yulePrior"
                    );
                }
        );
    }
}
