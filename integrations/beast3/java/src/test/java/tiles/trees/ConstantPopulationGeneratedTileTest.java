package tiles.trees;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import beast.base.evolution.tree.coalescent.PopulationFunction;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.evolution.tree.coalescent.ConstantPopulation;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.RealScalar;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.tiles.Tile;

public class ConstantPopulationGeneratedTileTest {

    @Test
    public void exposesSameInputContractAsHandwrittenTile() {
        ConstantPopulationTile handwrittenTile = new ConstantPopulationTile();

        ConstantPopulationGeneratedTile generatedTile = new ConstantPopulationGeneratedTile();

        assertEquals(handwrittenTile.getPhyloSpecGeneratorName(), generatedTile.getPhyloSpecGeneratorName());

        assertEquals(
                handwrittenTile.getTypeToken().toString(),
                generatedTile.getTypeToken().toString());

        assertEquals(
                List.of("populationSize"),
                generatedTile.getGeneratorTileInputs().stream()
                        .map(input -> input.getPhylospecArgumentName())
                        .toList());

        assertEquals(
                List.of(true),
                generatedTile.getGeneratorTileInputs().stream()
                        .map(input -> input.isRequired())
                        .toList());

        assertEquals(
                handwrittenTile.getGeneratorTileInputs().getFirst().getTypeToken(),
                generatedTile.getGeneratorTileInputs().getFirst().getTypeToken());

        assertEquals(
                "RealScalar<PositiveReal>",
                generatedTile.getGeneratorTileInputs().getFirst().getTypeToken().toString());
    }

    @Test
    public void setsTheSameBeastInputAsHandwrittenTile() {
        RealScalarParam<PositiveReal> populationSize = new RealScalarParam<>(100.0, PositiveReal.INSTANCE);

        Tile<RealScalar<? extends PositiveReal>, BEASTState> valueTile =
                new Tile<RealScalar<? extends PositiveReal>, BEASTState>() {

                    @Override
                    protected RealScalar<? extends PositiveReal> applyTile(
                            BEASTState state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {

                        return populationSize;
                    }
                };

        valueTile.setIndexVariables(Set.of());

        ConstantPopulationTile handwrittenTile = new ConstantPopulationTile();

        ConstantPopulationGeneratedTile generatedTile = new ConstantPopulationGeneratedTile();

        handwrittenTile.populationSizeInput.setTile(valueTile);

        generatedTile.populationSizeInput.setTile(valueTile);

        PopulationFunction handwrittenResult =
                handwrittenTile.applyTile(new BEASTState("handwritten-constant-population"), new IdentityHashMap<>());

        PopulationFunction generatedResult =
                generatedTile.applyTile(new BEASTState("generated-constant-population"), new IdentityHashMap<>());

        assertInstanceOf(ConstantPopulation.class, handwrittenResult);

        assertInstanceOf(ConstantPopulation.class, generatedResult);

        ConstantPopulation generatedObject = (ConstantPopulation) generatedResult;

        assertSame(populationSize, generatedObject.popSizeParameter.get());
    }
}
