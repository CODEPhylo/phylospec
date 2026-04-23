package tiles.functions;

import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.domain.Real;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.inference.parameter.SimplexParam;
import org.phylospec.ast.AstNode;
import org.phylospec.ast.Expr;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.VariableResolver;
import tiles.GeneratorTile;
import tiles.misc.AssignedArgumentTile;
import beastconfig.BEASTState;
import tiling.FailedTilingAttempt;
import tiling.Tile;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class RepeatSimplexTile extends GeneratorTile<SimplexParam> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "repeat";
    }

    GeneratorTileInput<RealScalarParam<? extends Real>> valueInput = new GeneratorTileInput<>(
            "value", Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
    );
    GeneratorTileInput<IntScalarParam<? extends NonNegativeInt>> numInput = new GeneratorTileInput<>(
            "num", Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
    );

    @Override
    public Set<Tile<?>> tryToTile(AstNode node, Map<AstNode, Set<Tile<?>>> inputTiles, VariableResolver variableResolver, StochasticityResolver stochasticityResolver) throws FailedTilingAttempt {
        Set<Tile<?>> tiles = super.tryToTile(node, inputTiles, variableResolver, stochasticityResolver);

        // we further have to check if both value and input are literals and if value*num = 1.0

        tiles = tiles.stream().filter(tile -> {
            if (!(tile instanceof RepeatSimplexTile simplexTile)) return false;

            if (!((Tile<?>) simplexTile.valueInput.getTile() instanceof AssignedArgumentTile valueArgTile)) return false;
            if (!((Tile<?>) simplexTile.numInput.getTile() instanceof AssignedArgumentTile numArgTile)) return false;

            if (!(valueArgTile.getRootNode().expression instanceof Expr.Literal valueLiteral)) return false;
            if (!(numArgTile.getRootNode().expression instanceof Expr.Literal numLiteral)) return false;

            if (!(valueLiteral.value instanceof Double value)) return false;
            if (!(numLiteral.value instanceof Integer num)) return false;

            return Math.abs(1.0 - value * num) < 1e-6;
        }).collect(Collectors.toSet());

        return tiles;
    }

    @Override
    public SimplexParam applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        double value = this.valueInput.apply(beastState, indexVariables).get();
        int num = this.numInput.apply(beastState, indexVariables).get();

        double[] values = new double[num];
        Arrays.fill(values, value);

        return new SimplexParam(values);
    }

}
