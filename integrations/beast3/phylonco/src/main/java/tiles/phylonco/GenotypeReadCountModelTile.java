package tiles.phylonco;

import beast.base.evolution.tree.Tree;
import beast.base.spec.type.IntScalar;
import beast.base.spec.type.RealScalar;
import beast.base.spec.type.RealVector;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.TemplateTile;
import phylonco.beast.evolution.likelihood.ReadCountTreeLikelihood;
import phylonco.beast.evolution.substitutionmodel.GT10;
import tiling.UnboundDistribution;

/**
 * {@code ReadCountModel(genotypes, ploidy, coverage, ...)}, the explicit form of LPhy's model. One
 * template matches the latent genotypes, ploidy and coverage draws together with the
 * {@code ReadCountModel}; they are integrated out, so this builds the same likelihood as the compact
 * {@code ReadCountModel(tree, qMatrix, ...)} in {@link ReadCountModelTile}. The template's
 * {@code ploidy} matches one draw only, so a coverage drawn with another ploidy is not matched.
 */
public class GenotypeReadCountModelTile
        extends TemplateTile<UnboundDistribution<ReadCountData, ReadCountTreeLikelihood>, BEASTState> {

    TemplateTileInput<Tree, BEASTState> treeInput = new TemplateTileInput<>("$tree");
    TemplateTileInput<IntScalar<?>, BEASTState> numSitesCTMCInput = new TemplateTileInput<>("$numSitesCTMC");
    TemplateTileInput<IntScalar<?>, BEASTState> numSitesPloidyInput = new TemplateTileInput<>("$numSitesPloidy");
    TemplateTileInput<IntScalar<?>, BEASTState> numCellsInput = new TemplateTileInput<>("$numCells");
    TemplateTileInput<GT10, BEASTState> qMatrixInput = new TemplateTileInput<>("$qMatrix");
    TemplateTileInput<RealScalar<?>, BEASTState> sequencingErrorInput = new TemplateTileInput<>("$sequencingError");
    TemplateTileInput<RealScalar<?>, BEASTState> allelicDropoutInput = new TemplateTileInput<>("$allelicDropout");
    TemplateTileInput<RealScalar<?>, BEASTState> meanAllelicCoverageInput =
            new TemplateTileInput<>("$meanAllelicCoverage");
    TemplateTileInput<RealScalar<?>, BEASTState> allelicCoverageVarianceInput =
            new TemplateTileInput<>("$allelicCoverageVariance");
    TemplateTileInput<RealVector<?>, BEASTState> cellSizeFactorsInput = new TemplateTileInput<>("$cellSizeFactors");
    TemplateTileInput<RealScalar<?>, BEASTState> homozygousOverdispersionInput =
            new TemplateTileInput<>("$homozygousOverdispersion");
    TemplateTileInput<RealScalar<?>, BEASTState> heterozygousOverdispersionInput =
            new TemplateTileInput<>("$heterozygousOverdispersion");

    @Override
    protected String getPhyloSpecTemplate() {
        return """
                Alignment genotypes ~ PhyloCTMC(tree=$tree, qMatrix=$qMatrix, numSites=$numSitesCTMC)
                Matrix<PositiveInteger> ploidy ~ Ploidy(numCells=$numCells, numSites=$numSitesPloidy, allelicDropout=$allelicDropout)
                Matrix<NonNegativeInteger> coverage ~ CoverageModel(
                    ploidy=ploidy,
                    meanAllelicCoverage=$meanAllelicCoverage,
                    allelicCoverageVariance=$allelicCoverageVariance,
                    cellSizeFactors=$cellSizeFactors
                )
                ReadCountModel(
                    genotypes=genotypes,
                    ploidy=ploidy,
                    coverage=coverage,
                    sequencingError=$sequencingError,
                    homozygousOverdispersion=$homozygousOverdispersion,
                    heterozygousOverdispersion=$heterozygousOverdispersion
                )
                """;
    }

    @Override
    public UnboundDistribution<ReadCountData, ReadCountTreeLikelihood> applyTile(
            BEASTState state, IdentityHashMap<Expr.Variable, Integer> indices) {
        int numSites = this.numSitesCTMCInput.apply(state, indices).get();
        int numSitesPloidy = this.numSitesPloidyInput.apply(state, indices).get();
        int numCells = this.numCellsInput.apply(state, indices).get();

        if (numSites != numSitesPloidy) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "The genotypes have " + numSites + " sites but the ploidy has " + numSitesPloidy + ".",
                    "Use the same numSites in PhyloCTMC and Ploidy.");
        }

        return ReadCountModelTile.treeLikelihood(
                state,
                this.getRootNode(),
                this.treeInput.apply(state, indices),
                this.qMatrixInput.apply(state, indices),
                this.sequencingErrorInput.apply(state, indices),
                this.allelicDropoutInput.apply(state, indices),
                this.meanAllelicCoverageInput.apply(state, indices),
                this.allelicCoverageVarianceInput.apply(state, indices),
                this.cellSizeFactorsInput.apply(state, indices),
                this.homozygousOverdispersionInput.apply(state, indices),
                this.heterozygousOverdispersionInput.apply(state, indices),
                data -> {
                    if (data.numSites() != numSites || data.numCells() != numCells) {
                        throw new TileApplicationError(
                                this.getRootNode(),
                                "The model has " + numCells + " cells and " + numSites
                                        + " sites, but the read counts have " + data.numCells() + " cells and "
                                        + data.numSites() + " sites.",
                                "Use numTaxa(readCounts) and numSites(readCounts) for numCells and numSites.");
                    }
                });
    }
}
