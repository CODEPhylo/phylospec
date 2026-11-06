package tiles.distributions;

import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.Real;
import beast.base.spec.inference.distribution.Dirichlet;
import beast.base.spec.inference.parameter.SimplexParam;
import beast.base.spec.type.RealVector;
import beastconfig.BEASTState;
import beastconfig.OperatorSelector;
import java.util.Arrays;
import java.util.IdentityHashMap;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import tiling.BoundDistribution;
import tiling.LiveDirichletDistribution;

public class DirichletTile extends GeneratorTile<BoundDistribution<SimplexParam, Dirichlet>, BEASTState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "Dirichlet";
    }

    GeneratorTileInput<RealVector<? extends Real>, BEASTState> concentrationInput =
            new GeneratorTileInput<>("concentration");

    @Override
    public BoundDistribution<SimplexParam, Dirichlet> applyTile(
            BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        RealVector<? extends Real> concentration = this.concentrationInput.apply(beastState, indexVariables);
        int dim = concentration.size();
        if (dim < 1) {
            throw new TileApplicationError(
                    getRootNode(),
                    "Dirichlet requires a nonempty concentration vector.",
                    "Provide a fixed-length vector of finite positive concentrations.");
        }
        for (int i = 0; i < dim; i++) {
            double value = concentration.get(i);
            if (!Double.isFinite(value) || value <= 0.0) {
                throw new TileApplicationError(
                        getRootNode(),
                        "Invalid Dirichlet concentration at position " + (i + 1) + ".",
                        "Every initial concentration must be finite and greater than zero.");
            }
        }

        Dirichlet distribution = new LiveDirichletDistribution();
        // The native input only reads the vector. Keep the original object and
        // domain; the distribution checks the concentrations on every evaluation.
        @SuppressWarnings("unchecked")
        RealVector<PositiveReal> nativeConcentration = (RealVector<PositiveReal>) concentration;
        beastState.setInput(distribution, distribution.alphaInput, nativeConcentration);

        // default state: uniform simplex of the same dimension as the concentration vector
        double[] defaultValues = new double[dim];
        Arrays.fill(defaultValues, 1.0 / dim);
        SimplexParam defaultState = new SimplexParam(defaultValues);

        return new BoundDistribution<>(
                distribution,
                defaultState,
                stateNode -> {
                    if (stateNode.size() != dim) {
                        throw new TileApplicationError(
                                getRootNode(),
                                "The simplex length does not match the concentration vector.",
                                "Provide exactly " + dim + " simplex elements.");
                    }
                    beastState.setInput(distribution, distribution.paramInput, stateNode);
                },
                OperatorSelector::getDefaultOperators);
    }
}
