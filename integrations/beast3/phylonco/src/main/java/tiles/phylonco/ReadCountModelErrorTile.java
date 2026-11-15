package tiles.phylonco;

import beast.base.evolution.tree.Tree;
import beast.base.spec.type.IntScalar;
import beast.base.spec.type.RealScalar;
import beast.base.spec.type.RealVector;
import beastconfig.BEASTState;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.TemplateTile;
import org.phylospec.tiling.tiles.TilePriority;
import phylonco.beast.evolution.likelihood.ReadCountTreeLikelihood;
import phylonco.beast.evolution.substitutionmodel.GT10;
import tiling.UnboundDistribution;

import java.util.IdentityHashMap;

/**
 * Reports an explicit {@code ReadCountModel(genotypes, ploidy, coverage, ...)} whose {@code ploidy}
 * is a different draw than the one its {@code coverage} was drawn with. The template matches two
 * separate {@code Ploidy} draws, one passed to {@code CoverageModel} and the other to
 * {@code ReadCountModel}. Such a model cannot be turned into a likelihood, because the latent ploidy
 * is integrated out as a single quantity. This tile never builds anything: applying it always
 * throws a {@link TileApplicationError} asking to pass the ploidy variable used in
 * {@code CoverageModel}.
 */
public class ReadCountModelErrorTile
        extends TemplateTile<UnboundDistribution<ReadCountData, ReadCountTreeLikelihood>, BEASTState> {

    @Override
    protected String getPhyloSpecTemplate() {
        return """
                Matrix<PositiveInteger> ploidy1 ~ Ploidy(numCells=$$numCells1, numSites=$$numSitesPloidy1, allelicDropout=$$allelicDropout1)
                Matrix<PositiveInteger> ploidy2 ~ Ploidy(numCells=$$numCells2, numSites=$$numSitesPloidy2, allelicDropout=$$allelicDropout2)
                Matrix<NonNegativeInteger> coverage ~ CoverageModel(
                    ploidy=ploidy1,
                    meanAllelicCoverage=$$meanAllelicCoverage,
                    allelicCoverageVariance=$$allelicCoverageVariance,
                    cellSizeFactors=$$cellSizeFactors
                )
                ReadCountModel(
                    genotypes=$$genotypes,
                    ploidy=ploidy2,
                    coverage=coverage,
                    sequencingError=$$sequencingError,
                    homozygousOverdispersion=$$homozygousOverdispersion,
                    heterozygousOverdispersion=$$heterozygousOverdispersion
                )
                """;
    }

    @Override
    public UnboundDistribution<ReadCountData, ReadCountTreeLikelihood> applyTile(
            BEASTState state, IdentityHashMap<Expr.Variable, Integer> indices) {
        throw new TileApplicationError(
                this.getRootNode(),
                "ReadCountModel needs the same ploidy that the coverage was drawn with.",
                "Pass the ploidy variable used in CoverageModel.");
    }

    @Override
    public TilePriority getPriority() {
        return TilePriority.ERROR;
    }
}
