package tiles.distributions;

import beast.base.spec.domain.PositiveReal;
import beast.base.spec.inference.distribution.Dirichlet;
import beast.base.spec.inference.parameter.SimplexParam;
import beast.base.spec.type.RealVector;
import org.phylospec.ast.Expr;
import tiles.GeneratorTile;
import beastconfig.BEASTState;
import tiling.BoundDistribution;

import java.util.Arrays;
import java.util.IdentityHashMap;

public class DirichletTile extends GeneratorTile<BoundDistribution<SimplexParam, Dirichlet>> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "Dirichlet";
    }

    GeneratorTileInput<RealVector<PositiveReal>> concentrationInput = new GeneratorTileInput<>("concentration");

    @Override
    public BoundDistribution<SimplexParam, Dirichlet> applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        RealVector<PositiveReal> concentration = this.concentrationInput.apply(beastState, indexVariables);

        Dirichlet distribution = new Dirichlet();
        beastState.setInput(distribution, distribution.alphaInput, concentration);

        // default state: uniform simplex of the same dimension as the concentration vector
        int dim = concentration.size();
        double[] defaultValues = new double[dim];
        Arrays.fill(defaultValues, 1.0 / dim);
        SimplexParam defaultState = new SimplexParam(defaultValues);

        return new BoundDistribution<>(
                distribution,
                defaultState,
                param -> beastState.setInput(distribution, distribution.paramInput, param)
        );
    }

}
