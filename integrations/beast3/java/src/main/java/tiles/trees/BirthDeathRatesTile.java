package tiles.trees;

import beast.base.evolution.tree.Tree;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.Real;
import beast.base.spec.domain.UnitInterval;
import beast.base.spec.evolution.tree.coalescent.RandomTree;
import beast.base.spec.type.RealScalar;
import beastconfig.BEASTState;
import beastconfig.OperatorSelector;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import tiles.input.TaxaData;
import tiles.input.TaxonValue;
import tiling.BirthDeathRateValue;
import tiling.BoundDistribution;
import tiling.LiveBirthDeathModel;
import tiling.RootAgeSupport;

/** Direct birth/death rates in the native birth > death range, optionally conditioned on root age. */
public class BirthDeathRatesTile extends GeneratorTile<BoundDistribution<Tree, LiveBirthDeathModel>, BEASTState> {

    GeneratorTileInput<RealScalar<? extends PositiveReal>, BEASTState> birthRateInput =
            new GeneratorTileInput<>("birthRate");
    GeneratorTileInput<RealScalar<? extends PositiveReal>, BEASTState> deathRateInput =
            new GeneratorTileInput<>("deathRate");
    GeneratorTileInput<RealScalar<UnitInterval>, BEASTState> samplingProbabilityInput =
            new GeneratorTileInput<>("samplingProbability", false);
    GeneratorTileInput<List<TaxonValue>, BEASTState> taxaInput = new GeneratorTileInput<>("taxa");
    GeneratorTileInput<RealScalar<? extends Real>, BEASTState> rootAgeInput =
            new GeneratorTileInput<>("rootAge", false);

    @Override
    public String getPhyloSpecGeneratorName() {
        return "BirthDeath";
    }

    @Override
    public Optional<String> getNamespace() {
        return Optional.of("phylospec.distributions.tree");
    }

    @Override
    public BoundDistribution<Tree, LiveBirthDeathModel> applyTile(
            BEASTState state, IdentityHashMap<Expr.Variable, Integer> indices) {
        RealScalar<? extends PositiveReal> birth = birthRateInput.apply(state, indices);
        RealScalar<? extends PositiveReal> death = deathRateInput.apply(state, indices);
        RealScalar<UnitInterval> samplingProbability = samplingProbabilityInput.apply(state, indices);
        RealScalar<? extends Real> rootAge = rootAgeInput.apply(state, indices);
        TaxaData taxa = TaxaData.fromList(state, taxaInput.apply(state, indices));

        BirthDeathRateValue diversification = rateValue(state, birth, death, false);
        BirthDeathRateValue turnover = rateValue(state, birth, death, true);
        try {
            diversification.initAndValidate();
            turnover.initAndValidate();
        } catch (IllegalArgumentException exception) {
            throw new TileApplicationError(
                    getRootNode(), "Cannot initialize BirthDeath from birth/death rates.", exception.getMessage());
        }

        LiveBirthDeathModel distribution = new LiveBirthDeathModel();
        state.setInput(distribution, distribution.birthDiffRateParameterInput, diversification);
        state.setInput(distribution, distribution.relativeDeathRateParameterInput, turnover);
        if (samplingProbability != null) {
            state.setInput(distribution, distribution.sampleProbabilityInput, samplingProbability);
        }
        RandomTree initialTree = BirthDeathTile.createInitialTree(state, taxa);
        RootAgeSupport.prepare(
                state, distribution, distribution.rootAgeParameterInput, initialTree, rootAge, getRootNode());
        return new BoundDistribution<>(
                distribution,
                initialTree,
                tree -> RootAgeSupport.bindTree(state, distribution, tree, taxa, rootAge),
                OperatorSelector::getDefaultOperators);
    }

    private static BirthDeathRateValue rateValue(
            BEASTState state,
            RealScalar<? extends PositiveReal> birth,
            RealScalar<? extends PositiveReal> death,
            boolean turnover) {
        BirthDeathRateValue value = new BirthDeathRateValue();
        state.setInput(value, value.birthRateInput, birth);
        state.setInput(value, value.deathRateInput, death);
        state.setInput(value, value.turnoverInput, turnover);
        return value;
    }
}
