package tiles.phylonco;

import beast.base.evolution.tree.Tree;
import beast.base.spec.type.IntScalar;
import beast.base.spec.type.RealScalar;
import beast.base.spec.type.RealVector;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.Optional;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.tiling.tiles.TemplateTile;
import phylonco.beast.evolution.likelihood.ReadCountTreeLikelihood;
import phylonco.beast.evolution.substitutionmodel.GT10;
import tiling.UnboundDistribution;

/**
 * {@code ReadCountModel(genotypes, ploidy, coverage, ...)}, the explicit form of LPhy's model. The
 * genotypes, ploidy and coverage come from latent draws and
 * are integrated out: this builds the same likelihood as the compact
 * {@code ReadCountModel(tree, qMatrix, ...)} in {@link ReadCountModelTile}.
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
                    int numSitesCTMC = this.numSitesCTMCInput.apply(state, indices).get();
                    int numSitesPloidy = this.numSitesPloidyInput.apply(state, indices).get();

                    int numCellsData = data.numCells();
                    int numCellsPloidy = this.numCellsInput.apply(state, indices).get();

                    if (numSitesCTMC != numSitesPloidy || numCellsData != numCellsPloidy) {
                        throw new TileApplicationError(
                                this.getRootNode(),
                                "The model has " + numCellsData + " cells and " + numSitesCTMC
                                        + " sites, but the read counts have " + numCellsPloidy + " cells and "
                                        + numSitesPloidy + " sites.",
                                "Use numTaxa(readCounts) and numSites(readCounts) for numCells and numSites.");
                    }
                });
    }
}
