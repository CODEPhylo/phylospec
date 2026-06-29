package tiles.functions;

import dr.evolution.tree.TreeUtils;
import dr.evolution.util.Taxa;
import dr.evolution.util.Taxon;
import dr.evomodel.tree.TMRCAStatistic;
import dr.evomodel.tree.TreeModel;
import org.phylospec.ast.Expr;
import org.phylospec.domain.NonNegativeReal;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.RealScalar;
import tiling.BeastXState;
import tiling.params.BeastXStatisticRealScalar;

import java.util.IdentityHashMap;
import java.util.List;

public class MRCATile extends GeneratorTile<RealScalar<NonNegativeReal>, BeastXState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "mrca";
    }

    GeneratorTileInput<List<String>, BeastXState> cladeInput =
            new GeneratorTileInput<>("clade");

    GeneratorTileInput<TreeModel, BeastXState> treeInput =
            new GeneratorTileInput<>("tree");

    @Override
    public RealScalar<NonNegativeReal> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        List<String> clade =
                this.cladeInput.apply(beastState, indexVariables);

        TreeModel tree =
                this.treeInput.apply(beastState, indexVariables);

        if (clade.isEmpty()) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "MRCA requires at least one taxon name.",
                    "Provide a non-empty clade.",
                    List.of("Age a = mrca(clade=[\"taxon1\", \"taxon2\"], tree=tree)")
            );
        }

        TMRCAStatistic statistic;

        try {
            statistic =
                    new TMRCAStatistic(
                            "mrcaAge",
                            tree,
                            toTaxa(clade),
                            false,
                            false
                    );
        } catch (TreeUtils.MissingTaxonException exception) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "Could not find one or more MRCA taxa in the tree.",
                    "Use taxon names or species names that exist in the tree.",
                    List.of("Age a = mrca(clade=[\"taxon1\", \"taxon2\"], tree=tree)")
            );
        }

        statistic.setId(
                beastState.getAvailableID(
                        this.getId("mrcaAge", indexVariables, "")
                )
        );

        if (statistic.getStatisticValue(0) < 0.0) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "MRCA age must be non-negative.",
                    "Use a tree whose node heights are non-negative.",
                    List.of("Age a = mrca(clade=[\"taxon1\", \"taxon2\"], tree=tree)")
            );
        }

        beastState.addCalculationNode(
                statistic,
                new TypeToken<TMRCAStatistic>() {},
                statistic.getId()
        );

        return new BeastXStatisticRealScalar<>(
                statistic,
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