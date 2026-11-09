package tiles.phylonco;

import beast.base.spec.type.RealScalar;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.Optional;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import phylonco.beast.evolution.likelihood.ReadCountTreeLikelihood;
import tiles.phylonco.LatentReadCountTiles.Coverage;
import tiles.phylonco.LatentReadCountTiles.Genotypes;
import tiles.phylonco.LatentReadCountTiles.Ploidy;
import tiling.UnboundDistribution;

/**
 * {@code ReadCountModel(genotypes, ploidy, coverage, ...)}, the explicit form of LPhy's model. The
 * genotypes, ploidy and coverage come from the latent draws in {@link LatentReadCountTiles} and
 * are integrated out: this builds the same likelihood as the compact
 * {@code ReadCountModel(tree, qMatrix, ...)} in {@link ReadCountModelTile}.
 */
public class GenotypeReadCountModelTile
        extends GeneratorTile<UnboundDistribution<ReadCountData, ReadCountTreeLikelihood>, BEASTState> {

    GeneratorTileInput<Genotypes, BEASTState> genotypesInput = new GeneratorTileInput<>("genotypes");
    GeneratorTileInput<Ploidy, BEASTState> ploidyInput = new GeneratorTileInput<>("ploidy");
    GeneratorTileInput<Coverage, BEASTState> coverageInput = new GeneratorTileInput<>("coverage");
    GeneratorTileInput<RealScalar<?>, BEASTState> sequencingErrorInput = new GeneratorTileInput<>("sequencingError");
    GeneratorTileInput<RealScalar<?>, BEASTState> homozygousOverdispersionInput =
            new GeneratorTileInput<>("homozygousOverdispersion");
    GeneratorTileInput<RealScalar<?>, BEASTState> heterozygousOverdispersionInput =
            new GeneratorTileInput<>("heterozygousOverdispersion");

    @Override
    public String getPhyloSpecGeneratorName() {
        return "ReadCountModel";
    }

    @Override
    public Optional<String> getNamespace() {
        return Optional.of("phylonco");
    }

    @Override
    public UnboundDistribution<ReadCountData, ReadCountTreeLikelihood> applyTile(
            BEASTState state, IdentityHashMap<Expr.Variable, Integer> indices) {
        Genotypes genotypes = this.genotypesInput.apply(state, indices);
        Ploidy ploidy = this.ploidyInput.apply(state, indices);
        Coverage coverage = this.coverageInput.apply(state, indices);

        // The likelihood integrates over one ploidy per cell and site, shared by coverage and reads.
        // Compare identity, not value: two Ploidy draws with equal arguments are independent
        // variables. Tile results are memoised, so the same draw always yields the same object.
        if (coverage.ploidy() != ploidy) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "ReadCountModel needs the same ploidy that the coverage was drawn with.",
                    "Pass the ploidy variable used in CoverageModel.");
        }
        if (genotypes.numSites() != ploidy.numSites()) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "The genotypes have " + genotypes.numSites() + " sites but the ploidy has "
                            + ploidy.numSites() + ".",
                    "Use the same numSites in PhyloCTMC and Ploidy.");
        }

        return ReadCountModelTile.treeLikelihood(
                state,
                this.getRootNode(),
                genotypes.tree(),
                genotypes.qMatrix(),
                this.sequencingErrorInput.apply(state, indices),
                ploidy.allelicDropout(),
                coverage.meanAllelicCoverage(),
                coverage.allelicCoverageVariance(),
                coverage.cellSizeFactors(),
                this.homozygousOverdispersionInput.apply(state, indices),
                this.heterozygousOverdispersionInput.apply(state, indices),
                data -> {
                    if (data.numSites() != genotypes.numSites() || data.numCells() != ploidy.numCells()) {
                        throw new TileApplicationError(
                                this.getRootNode(),
                                "The model has " + ploidy.numCells() + " cells and " + genotypes.numSites()
                                        + " sites, but the read counts have " + data.numCells() + " cells and "
                                        + data.numSites() + " sites.",
                                "Use numTaxa(readCounts) and numSites(readCounts) for numCells and numSites.");
                    }
                });
    }
}
