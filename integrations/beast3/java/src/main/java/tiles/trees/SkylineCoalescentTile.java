package tiles.trees;

import beast.base.evolution.tree.Tree;
import beast.base.spec.domain.Real;
import beast.base.spec.type.RealScalar;
import beast.base.spec.type.RealVector;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import org.phylospec.ast.AstNode;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import tiles.input.TaxaData;
import tiles.input.TaxonValue;
import tiling.BoundDistribution;
import tiling.PiecewiseConstantPopulation;
import tiling.RealVectorFromScalars;
import tiling.SkylineCoalescentModel;

/** Time-based skyline with live, fixed-length population sizes and change times. */
public class SkylineCoalescentTile extends GeneratorTile<BoundDistribution<Tree, SkylineCoalescentModel>, BEASTState> {

    GeneratorTileInput<RealVector<? extends Real>, BEASTState> populationSizesInput =
            new GeneratorTileInput<>("populationSizes");
    GeneratorTileInput<RealVector<? extends Real>, BEASTState> changeTimesInput =
            new GeneratorTileInput<>("changeTimes");
    GeneratorTileInput<List<TaxonValue>, BEASTState> taxaInput = new GeneratorTileInput<>("taxa");

    // groupSizes is not declared: supplying it must fail rather than silently change the model.
    @Override
    public String getPhyloSpecGeneratorName() {
        return "SkylineCoalescent";
    }

    @Override
    public Optional<String> getNamespace() {
        return Optional.of("phylospec.distributions.tree");
    }

    @Override
    public BoundDistribution<Tree, SkylineCoalescentModel> applyTile(
            BEASTState state, IdentityHashMap<Expr.Variable, Integer> indices) {
        return bind(
                state,
                populationSizesInput.apply(state, indices),
                changeTimesInput.apply(state, indices),
                taxaInput.apply(state, indices),
                getRootNode());
    }

    private static BoundDistribution<Tree, SkylineCoalescentModel> bind(
            BEASTState state,
            RealVector<? extends Real> populationSizes,
            RealVector<? extends Real> times,
            List<TaxonValue> taxa,
            AstNode root) {
        PiecewiseConstantPopulation population = new PiecewiseConstantPopulation();
        state.setInput(population, population.populationSizesInput, populationSizes);
        state.setInput(population, population.changeTimesInput, times);
        try {
            population.initAndValidate();
        } catch (IllegalArgumentException exception) {
            throw new TileApplicationError(root, "Cannot initialize SkylineCoalescent.", exception.getMessage());
        }
        return CoalescentTile.bind(state, TaxaData.fromList(state, taxa), population, new SkylineCoalescentModel());
    }

    private static RealVectorFromScalars fromScalars(List<? extends RealScalar<? extends Real>> values) {
        RealVectorFromScalars vector = new RealVectorFromScalars();
        for (RealScalar<? extends Real> value : values) vector.valuesInput.setValue(value, vector);
        return vector;
    }

    /** Retains dependency edges when population sizes are assembled from sampled scalars. */
    public static class ListPopulations
            extends GeneratorTile<BoundDistribution<Tree, SkylineCoalescentModel>, BEASTState> {

        GeneratorTileInput<List<? extends RealScalar<? extends Real>>, BEASTState> populationSizesInput =
                new GeneratorTileInput<>("populationSizes");
        GeneratorTileInput<RealVector<? extends Real>, BEASTState> changeTimesInput =
                new GeneratorTileInput<>("changeTimes");
        GeneratorTileInput<List<TaxonValue>, BEASTState> taxaInput = new GeneratorTileInput<>("taxa");

        @Override
        public String getPhyloSpecGeneratorName() {
            return "SkylineCoalescent";
        }

        @Override
        public Optional<String> getNamespace() {
            return Optional.of("phylospec.distributions.tree");
        }

        @Override
        public BoundDistribution<Tree, SkylineCoalescentModel> applyTile(
                BEASTState state, IdentityHashMap<Expr.Variable, Integer> indices) {
            return bind(
                    state,
                    fromScalars(populationSizesInput.apply(state, indices)),
                    changeTimesInput.apply(state, indices),
                    taxaInput.apply(state, indices),
                    getRootNode());
        }
    }

    public static class ListTimes extends GeneratorTile<BoundDistribution<Tree, SkylineCoalescentModel>, BEASTState> {

        GeneratorTileInput<RealVector<? extends Real>, BEASTState> populationSizesInput =
                new GeneratorTileInput<>("populationSizes");
        GeneratorTileInput<List<? extends RealScalar<? extends Real>>, BEASTState> changeTimesInput =
                new GeneratorTileInput<>("changeTimes");
        GeneratorTileInput<List<TaxonValue>, BEASTState> taxaInput = new GeneratorTileInput<>("taxa");

        @Override
        public String getPhyloSpecGeneratorName() {
            return "SkylineCoalescent";
        }

        @Override
        public Optional<String> getNamespace() {
            return Optional.of("phylospec.distributions.tree");
        }

        @Override
        public BoundDistribution<Tree, SkylineCoalescentModel> applyTile(
                BEASTState state, IdentityHashMap<Expr.Variable, Integer> indices) {
            return bind(
                    state,
                    populationSizesInput.apply(state, indices),
                    fromScalars(changeTimesInput.apply(state, indices)),
                    taxaInput.apply(state, indices),
                    getRootNode());
        }
    }

    public static class Lists extends GeneratorTile<BoundDistribution<Tree, SkylineCoalescentModel>, BEASTState> {

        GeneratorTileInput<List<? extends RealScalar<? extends Real>>, BEASTState> populationSizesInput =
                new GeneratorTileInput<>("populationSizes");
        GeneratorTileInput<List<? extends RealScalar<? extends Real>>, BEASTState> changeTimesInput =
                new GeneratorTileInput<>("changeTimes");
        GeneratorTileInput<List<TaxonValue>, BEASTState> taxaInput = new GeneratorTileInput<>("taxa");

        @Override
        public String getPhyloSpecGeneratorName() {
            return "SkylineCoalescent";
        }

        @Override
        public Optional<String> getNamespace() {
            return Optional.of("phylospec.distributions.tree");
        }

        @Override
        public BoundDistribution<Tree, SkylineCoalescentModel> applyTile(
                BEASTState state, IdentityHashMap<Expr.Variable, Integer> indices) {
            return bind(
                    state,
                    fromScalars(populationSizesInput.apply(state, indices)),
                    fromScalars(changeTimesInput.apply(state, indices)),
                    taxaInput.apply(state, indices),
                    getRootNode());
        }
    }
}
