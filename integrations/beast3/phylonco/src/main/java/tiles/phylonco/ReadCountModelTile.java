package tiles.phylonco;

import beast.base.evolution.tree.Tree;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.evolution.branchratemodel.StrictClockModel;
import beast.base.spec.evolution.sitemodel.SiteModel;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.RealScalar;
import beast.base.spec.type.RealVector;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.Optional;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import phylonco.beast.evolution.likelihood.ReadCountTreeLikelihood;
import phylonco.beast.evolution.readcountmodel.LikelihoodReadCountModel;
import phylonco.beast.evolution.substitutionmodel.GT10;
import tiling.UnboundDistribution;

/**
 * {@code ReadCountModel(tree, qMatrix, ...)} -> phylonco {@link ReadCountTreeLikelihood}, which
 * integrates genotypes out by pruning and uses {@link LikelihoodReadCountModel} for
 * P(reads | genotype) at the tips. The read-count model is only an input of the tree likelihood;
 * it is not registered as a distribution of its own (it would be counted twice).
 */
public class ReadCountModelTile
        extends GeneratorTile<UnboundDistribution<ReadCountData, ReadCountTreeLikelihood>, BEASTState> {

    GeneratorTileInput<Tree, BEASTState> treeInput = new GeneratorTileInput<>("tree");
    GeneratorTileInput<GT10, BEASTState> qMatrixInput = new GeneratorTileInput<>("qMatrix");
    GeneratorTileInput<RealScalar<?>, BEASTState> sequencingErrorInput = new GeneratorTileInput<>("sequencingError");
    GeneratorTileInput<RealScalar<?>, BEASTState> allelicDropoutInput = new GeneratorTileInput<>("allelicDropout");
    GeneratorTileInput<RealScalar<?>, BEASTState> meanAllelicCoverageInput =
            new GeneratorTileInput<>("meanAllelicCoverage");
    GeneratorTileInput<RealScalar<?>, BEASTState> allelicCoverageVarianceInput =
            new GeneratorTileInput<>("allelicCoverageVariance");
    GeneratorTileInput<RealVector<?>, BEASTState> cellSizeFactorsInput = new GeneratorTileInput<>("cellSizeFactors");
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
        Tree tree = this.treeInput.apply(state, indices);
        GT10 gt10 = this.qMatrixInput.apply(state, indices);
        RealVector<?> cellSizeFactors = this.cellSizeFactorsInput.apply(state, indices);

        LikelihoodReadCountModel readCountModel = new LikelihoodReadCountModel();
        state.setInput(readCountModel, readCountModel.epsilonInput, this.sequencingErrorInput.apply(state, indices));
        state.setInput(readCountModel, readCountModel.deltaInput, this.allelicDropoutInput.apply(state, indices));
        state.setInput(readCountModel, readCountModel.tInput, this.meanAllelicCoverageInput.apply(state, indices));
        state.setInput(readCountModel, readCountModel.vInput, this.allelicCoverageVarianceInput.apply(state, indices));
        state.setInput(readCountModel, readCountModel.sInput, cellSizeFactors);
        state.setInput(readCountModel, readCountModel.w1Input, this.homozygousOverdispersionInput.apply(state, indices));
        state.setInput(readCountModel, readCountModel.w2Input, this.heterozygousOverdispersionInput.apply(state, indices));

        SiteModel siteModel = new SiteModel();
        state.setInput(siteModel, siteModel.substModelInput, gt10);

        StrictClockModel clock = new StrictClockModel();
        state.setInput(clock, clock.meanRateInput, new RealScalarParam<>(1.0, PositiveReal.INSTANCE));

        ReadCountTreeLikelihood likelihood = new ReadCountTreeLikelihood();
        state.setInput(likelihood, likelihood.treeInput, tree);
        state.setInput(likelihood, likelihood.siteModelInput, siteModel);
        state.setInput(likelihood, likelihood.branchRateModelInput, clock);

        return new UnboundDistribution<>(likelihood, data -> {
            if (cellSizeFactors.size() != data.numCells()) {
                throw new TileApplicationError(
                        this.getRootNode(),
                        "cellSizeFactors has " + cellSizeFactors.size() + " values but the data has "
                                + data.numCells() + " cells.",
                        "Use one size factor per cell.");
            }
            // BEASTState.setInput initialises the value right away, so the read-count model is
            // attached only once its observed read counts are set
            state.setInput(readCountModel, readCountModel.readCountInput, data.readCount());
            state.setInput(likelihood, likelihood.readCountModelInput, readCountModel);
        });
    }
}
