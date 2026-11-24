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
import org.phylospec.typeresolver.Stochasticity;
import tiling.BeastXState;
import tiling.model.BoundDistribution;
import tiling.params.BeastXParameters;

import java.util.IdentityHashMap;
import java.util.Set;

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

    // The root is fixed at rootAge during MCMC, so a sampled rootAge is not supported.
    GeneratorTileInput<RealScalar<? extends NonNegativeReal>, BeastXState> rootAgeInput =
            new GeneratorTileInput<>("rootAge", false, Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));

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

        Double conditioningAge =
                RootAge.conditioningAge(this.getRootNode(), beastState, rootAge, taxa, null, "Yule");

        DefaultTreeModel defaultTreeModel =
                new DefaultTreeModel(
                        "tree",
                        InitialTreeBuilder.balancedTree(taxa, "Yule", rootAge)
                );

        BirthDeathGernhard08Model yuleModel =
                conditioningAge == null
                        ? new BirthDeathGernhard08Model(
                                BeastXParameters.toParameter(birthRate),
                                new Parameter.Default(0.0),
                                new Parameter.Default(1.0),
                                BirthDeathGernhard08Model.TreeType.UNSCALED,
                                Units.Type.YEARS
                        )
                        : RootAge.conditionedModel(
                                "yule",
                                BeastXParameters.toParameter(birthRate),
                                new Parameter.Default(0.0),
                                BirthDeathGernhard08Model.TreeType.UNSCALED
                        );

        if (conditioningAge != null) {
            RootAge.fixRoot(beastState, defaultTreeModel);
        }

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
