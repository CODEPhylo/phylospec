package tiles.distributions;

import dr.evolution.alignment.Alignment;
import dr.evomodel.branchratemodel.BranchRateModel;
import dr.evomodel.tree.TreeModel;
import dr.inference.model.Parameter;
import org.phylospec.ast.Expr;
import org.phylospec.domain.PositiveReal;
import org.phylospec.domain.Real;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.RealVector;
import tiling.BeastXState;
import tiling.model.BeastXBMTraitLikelihoodSpec;
import tiling.model.UnboundDistribution;
import tiling.params.BeastXParam;

import java.util.IdentityHashMap;
import java.util.List;

public class PhyloBMTile extends GeneratorTile<
        UnboundDistribution<Alignment>,
        BeastXState
        > {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "PhyloBM";
    }

    GeneratorTileInput<TreeModel, BeastXState> treeInput =
            new GeneratorTileInput<>("tree");

    GeneratorTileInput<BranchRateModel, BeastXState> branchRatesInput =
            new GeneratorTileInput<>("branchRates");

    GeneratorTileInput<RealVector<? extends PositiveReal>, BeastXState> siteRatesInput =
            new GeneratorTileInput<>("siteRates");

    GeneratorTileInput<RealVector<? extends Real>, BeastXState> rootValuesInput =
            new GeneratorTileInput<>("rootValues", false);

    @Override
    public UnboundDistribution<Alignment> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        TreeModel tree =
                this.treeInput.apply(beastState, indexVariables);

        BranchRateModel branchRates =
                this.branchRatesInput.apply(beastState, indexVariables);

        RealVector<? extends PositiveReal> siteRates =
                this.siteRatesInput.apply(beastState, indexVariables);

        RealVector<? extends Real> rootValues =
                this.rootValuesInput.apply(beastState, indexVariables);

        requireSingleTrait(siteRates, "siteRates");

        if (rootValues != null) {
            requireSingleTrait(rootValues, "rootValues");
        }

        Parameter siteRatesParameter =
                toParameter(siteRates);

        Parameter rootValuesParameter =
                rootValues == null
                        ? null
                        : toParameter(rootValues);

        return new UnboundDistribution<>((observedTraits, id) -> {
            BeastXBMTraitLikelihoodSpec likelihood =
                    new BeastXBMTraitLikelihoodSpec(
                            id,
                            observedTraits,
                            tree,
                            branchRates,
                            siteRatesParameter,
                            rootValuesParameter
                    );

            beastState.addLikelihoodDistribution(likelihood, id);
        });
    }

    private void requireSingleTrait(RealVector<?> vector, String argumentName) {
        if (vector.size() != 1) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "PhyloBM currently supports one continuous trait in the BEAST X backend.",
                    "Use a one-element vector for '" + argumentName + "'.",
                    List.of(argumentName + "=[1.0]")
            );
        }
    }

    private static Parameter toParameter(RealVector<? extends Real> vector) {
        if (vector instanceof BeastXParam beastXParam) {
            return beastXParam.getParameter();
        }

        return new Parameter.Default(vector.getDoubleArray());
    }
}