package tiles.phylonco;

import beast.base.evolution.tree.Tree;
import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.type.RealScalar;
import beast.base.spec.type.RealVector;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.Set;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.tiles.TemplateTile;
import org.phylospec.typeresolver.Stochasticity;
import phylonco.beast.evolution.substitutionmodel.GT10;

/**
 * The latent draws of the explicit read-count model, as in LPhy:
 *
 * <pre>
 * Alignment genotypes ~ PhyloCTMC(tree=..., qMatrix=gt10(...), numSites=...)
 * Matrix&lt;PositiveInteger&gt; ploidy ~ Ploidy(numCells=..., numSites=..., allelicDropout=...)
 * Matrix&lt;NonNegativeInteger&gt; coverage ~ CoverageModel(ploidy=ploidy, ...)
 * </pre>
 *
 * <p>phylonco's {@code ReadCountTreeLikelihood} integrates genotypes, ploidy and coverage out, so
 * these draws do not become BEAST state nodes. Each tile only collects its arguments, and
 * {@link GenotypeReadCountModelTile} passes them to the shared tree likelihood. Like
 * {@code DrawnSiteRatesTile}, the model states the draw and the engine decides how to integrate
 * it. A latent value used anywhere else has no tile and is reported as unsupported.
 */
public final class LatentReadCountTiles {

    private LatentReadCountTiles() {}

    public record Genotypes(Tree tree, GT10 qMatrix, int numSites) {}

    public record Ploidy(RealScalar<?> allelicDropout, int numCells, int numSites) {}

    public record Coverage(
            Ploidy ploidy,
            RealScalar<?> meanAllelicCoverage,
            RealScalar<?> allelicCoverageVariance,
            RealVector<?> cellSizeFactors) {}

    private static final Set<Stochasticity> FIXED = Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC);

    /** {@code genotypes ~ PhyloCTMC(tree, qMatrix, numSites)} with a GT10 genotype model. */
    public static class GenotypeAlignment extends TemplateTile<Genotypes, BEASTState> {
        TemplateTileInput<Tree, BEASTState> treeInput = new TemplateTileInput<>("$tree");
        TemplateTileInput<GT10, BEASTState> qMatrixInput = new TemplateTileInput<>("$qMatrix");
        TemplateTileInput<IntScalarParam<? extends NonNegativeInt>, BEASTState> numSitesInput =
                new TemplateTileInput<>("$numSites", FIXED);

        @Override
        protected String getPhyloSpecTemplate() {
            return "Any x ~ PhyloCTMC(tree=$tree, qMatrix=$qMatrix, numSites=$numSites)";
        }

        @Override
        protected Genotypes applyTile(BEASTState state, IdentityHashMap<Expr.Variable, Integer> indices) {
            return new Genotypes(
                    this.treeInput.apply(state, indices),
                    this.qMatrixInput.apply(state, indices),
                    this.numSitesInput.apply(state, indices).get());
        }
    }

    /** {@code ploidy ~ Ploidy(numCells, numSites, allelicDropout)}. */
    public static class PloidyDraw extends TemplateTile<Ploidy, BEASTState> {
        TemplateTileInput<IntScalarParam<? extends NonNegativeInt>, BEASTState> numCellsInput =
                new TemplateTileInput<>("$numCells", FIXED);
        TemplateTileInput<IntScalarParam<? extends NonNegativeInt>, BEASTState> numSitesInput =
                new TemplateTileInput<>("$numSites", FIXED);
        TemplateTileInput<RealScalar<?>, BEASTState> allelicDropoutInput = new TemplateTileInput<>("$allelicDropout");

        @Override
        protected String getPhyloSpecTemplate() {
            return "Any x ~ Ploidy(numCells=$numCells, numSites=$numSites, allelicDropout=$allelicDropout)";
        }

        @Override
        protected Ploidy applyTile(BEASTState state, IdentityHashMap<Expr.Variable, Integer> indices) {
            return new Ploidy(
                    this.allelicDropoutInput.apply(state, indices),
                    this.numCellsInput.apply(state, indices).get(),
                    this.numSitesInput.apply(state, indices).get());
        }
    }

    /** {@code coverage ~ CoverageModel(ploidy, meanAllelicCoverage, allelicCoverageVariance, cellSizeFactors)}. */
    public static class CoverageDraw extends TemplateTile<Coverage, BEASTState> {
        TemplateTileInput<Ploidy, BEASTState> ploidyInput = new TemplateTileInput<>("$ploidy");
        TemplateTileInput<RealScalar<?>, BEASTState> meanAllelicCoverageInput =
                new TemplateTileInput<>("$meanAllelicCoverage");
        TemplateTileInput<RealScalar<?>, BEASTState> allelicCoverageVarianceInput =
                new TemplateTileInput<>("$allelicCoverageVariance");
        TemplateTileInput<RealVector<?>, BEASTState> cellSizeFactorsInput =
                new TemplateTileInput<>("$cellSizeFactors");

        @Override
        protected String getPhyloSpecTemplate() {
            return "Any x ~ CoverageModel(ploidy=$ploidy, meanAllelicCoverage=$meanAllelicCoverage,"
                    + " allelicCoverageVariance=$allelicCoverageVariance, cellSizeFactors=$cellSizeFactors)";
        }

        @Override
        protected Coverage applyTile(BEASTState state, IdentityHashMap<Expr.Variable, Integer> indices) {
            return new Coverage(
                    this.ploidyInput.apply(state, indices),
                    this.meanAllelicCoverageInput.apply(state, indices),
                    this.allelicCoverageVarianceInput.apply(state, indices),
                    this.cellSizeFactorsInput.apply(state, indices));
        }
    }
}
