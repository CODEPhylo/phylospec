package tiles.distributions;

import dr.evolution.alignment.Alignment;
import dr.evomodel.tree.TreeModel;
import dr.inference.model.Parameter;
import org.phylospec.ast.Expr;
import org.phylospec.domain.PositiveReal;
import org.phylospec.domain.Real;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.RealScalar;
import org.phylospec.types.RealVector;
import tiling.BeastXState;
import tiling.model.BeastXOUTraitLikelihoodSpec;
import tiling.model.UnboundDistribution;
import tiling.params.BeastXParam;
import tiling.params.BeastXRealScalarParam;

import java.util.IdentityHashMap;
import java.util.List;

public class PhyloOUTile extends GeneratorTile<
        UnboundDistribution<Alignment>,
        BeastXState
        > {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "PhyloOU";
    }

    GeneratorTileInput<TreeModel, BeastXState> treeInput =
            new GeneratorTileInput<>("tree");

    GeneratorTileInput<RealVector<? extends PositiveReal>, BeastXState> siteVariancesInput =
            new GeneratorTileInput<>("siteVariances");

    GeneratorTileInput<RealScalar<? extends PositiveReal>, BeastXState> selectionStrengthInput =
            new GeneratorTileInput<>("selectionStrength");

    GeneratorTileInput<RealVector<? extends Real>, BeastXState> siteOptimaInput =
            new GeneratorTileInput<>("siteOptima");

    GeneratorTileInput<RealVector<? extends Real>, BeastXState> rootValuesInput =
            new GeneratorTileInput<>("rootValues", false);

    @Override
    public UnboundDistribution<Alignment> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        TreeModel tree =
                this.treeInput.apply(beastState, indexVariables);

        RealVector<? extends PositiveReal> siteVariances =
                this.siteVariancesInput.apply(beastState, indexVariables);

        RealScalar<? extends PositiveReal> selectionStrength =
                this.selectionStrengthInput.apply(beastState, indexVariables);

        RealVector<? extends Real> siteOptima =
                this.siteOptimaInput.apply(beastState, indexVariables);

        RealVector<? extends Real> rootValues =
                this.rootValuesInput.apply(beastState, indexVariables);

        requireSingleTrait(siteVariances, "siteVariances");
        requireSingleTrait(siteOptima, "siteOptima");

        if (rootValues != null) {
            requireSingleTrait(rootValues, "rootValues");
        }

        Parameter siteVariancesParameter =
                toParameter(siteVariances);

        Parameter selectionStrengthParameter =
                toParameter(selectionStrength);

        Parameter siteOptimaParameter =
                toParameter(siteOptima);

        Parameter rootValuesParameter =
                rootValues == null
                        ? null
                        : toParameter(rootValues);

        return new UnboundDistribution<>((observedTraits, id) -> {
            BeastXOUTraitLikelihoodSpec likelihood =
                    new BeastXOUTraitLikelihoodSpec(
                            id,
                            observedTraits,
                            tree,
                            siteVariancesParameter,
                            selectionStrengthParameter,
                            siteOptimaParameter,
                            rootValuesParameter
                    );

            beastState.addLikelihoodDistribution(likelihood, id);
        });
    }

    private void requireSingleTrait(RealVector<?> vector, String argumentName) {
        if (vector.size() != 1) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "PhyloOU currently supports one continuous trait in the BEAST X backend.",
                    "Use a one-element vector for '" + argumentName + "'.",
                    List.of(argumentName + "=[1.0]")
            );
        }
    }

    private static Parameter toParameter(RealScalar<? extends Real> scalar) {
        if (scalar instanceof BeastXRealScalarParam<?> beastXScalar) {
            return beastXScalar.getParameter();
        }

        return new Parameter.Default(scalar.get());
    }

    private static Parameter toParameter(RealVector<? extends Real> vector) {
        if (vector instanceof BeastXParam beastXParam) {
            return beastXParam.getParameter();
        }

        return new Parameter.Default(vector.getDoubleArray());
    }
}