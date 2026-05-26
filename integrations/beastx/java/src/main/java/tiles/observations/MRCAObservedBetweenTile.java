package tiles.observations;

import dr.evolution.util.Taxa;
import dr.evolution.util.Taxon;
import dr.evomodel.tree.TMRCAStatistic;
import dr.evomodel.tree.TreeModel;
import dr.inference.distribution.DistributionLikelihood;
import dr.inference.distribution.UniformDistributionModel;
import dr.inference.model.Parameter;
import org.phylospec.ast.Expr;
import org.phylospec.domain.NonNegativeReal;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.TemplateTile;
import org.phylospec.types.RealScalar;
import tiling.BeastXRealScalarParam;
import tiling.BeastXState;

import java.util.IdentityHashMap;
import java.util.List;

public class MRCAObservedBetweenTile extends TemplateTile<RealScalar<NonNegativeReal>, BeastXState> {

    @Override
    protected String getPhyloSpecTemplate() {
        return "Any x = mrca(clade=$clade, tree=$tree) observed between [$from, $to]";
    }

    TemplateTileInput<List<String>, BeastXState> cladeInput =
            new TemplateTileInput<>("$clade");

    TemplateTileInput<TreeModel, BeastXState> treeInput =
            new TemplateTileInput<>("$tree");

    TemplateTileInput<RealScalar<?>, BeastXState> fromInput =
            new TemplateTileInput<>("$from");

    TemplateTileInput<RealScalar<?>, BeastXState> toInput =
            new TemplateTileInput<>("$to");

    @Override
    public RealScalar<NonNegativeReal> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        List<String> clade =
                this.cladeInput.apply(beastState, indexVariables);

        TreeModel tree =
                this.treeInput.apply(beastState, indexVariables);

        RealScalar<?> from =
                this.fromInput.apply(beastState, indexVariables);

        RealScalar<?> to =
                this.toInput.apply(beastState, indexVariables);

        if (clade.isEmpty()) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "MRCA calibration requires at least one taxon.",
                    "Provide a non-empty clade.",
                    List.of("Age h = mrca(clade=[\"Homo_sapiens\", \"Pan\"], tree=tree) observed between [1.0, 10.0]")
            );
        }

        if (from.get() < 0.0 || to.get() < 0.0) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "MRCA calibration bounds must be non-negative.",
                    "Use calibration bounds greater than or equal to zero.",
                    List.of("Age h = mrca(clade=[\"Homo_sapiens\", \"Pan\"], tree=tree) observed between [1.0, 10.0]")
            );
        }

        if (from.get() > to.get()) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "MRCA calibration lower bound must not be greater than upper bound.",
                    "Use lower <= upper.",
                    List.of("Age h = mrca(clade=[\"Homo_sapiens\", \"Pan\"], tree=tree) observed between [1.0, 10.0]")
            );
        }

        UniformDistributionModel distributionModel =
                new UniformDistributionModel(
                        new Parameter.Default(from.get()),
                        new Parameter.Default(to.get())
                );

        DistributionLikelihood calibrationPrior =
                new DistributionLikelihood(distributionModel);

        TMRCAStatistic mrcaStatistic;

        try {
            mrcaStatistic =
                    new TMRCAStatistic(
                            "mrcaAge",
                            tree,
                            toTaxa(clade),
                            false,
                            false
                    );
        } catch (Exception exception) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "Could not create an MRCA age statistic for the clade.",
                    "Use taxon names that exist in the tree.",
                    List.of("Age h = mrca(clade=[\"Homo_sapiens\", \"Pan\"], tree=tree) observed between [1.0, 10.0]")
            );
        }

        calibrationPrior.addData(mrcaStatistic);

        beastState.addCalibrationPriorDistribution(
                calibrationPrior,
                "mrcaCalibration"
        );

        return new BeastXRealScalarParam<>(
                mrcaStatistic.getStatisticValue(0),
                NonNegativeReal.INSTANCE
        );
    }

    private static Taxa toTaxa(List<String> taxonNames) {
        Taxa taxa =
                new Taxa();

        for (String taxonName : taxonNames) {
            taxa.addTaxon(new Taxon(taxonName));
        }

        return taxa;
    }
}