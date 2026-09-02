package tiles.substitutionmodels;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import beast.base.spec.domain.PositiveReal;
import beast.base.spec.evolution.substitutionmodel.Frequencies;
import beast.base.spec.evolution.substitutionmodel.HKY;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.inference.parameter.SimplexParam;
import beast.base.spec.type.RealScalar;
import beast.base.spec.type.Simplex;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.tiles.Tile;

public class HKYGeneratedTileTest {

    @Test
    public void exposesSameInputContractAsHandwrittenTile() {
        HKYTile handwrittenTile = new HKYTile();

        HKYGeneratedTile generatedTile = new HKYGeneratedTile();

        assertEquals(handwrittenTile.getPhyloSpecGeneratorName(), generatedTile.getPhyloSpecGeneratorName());

        assertEquals(handwrittenTile.getTypeToken(), generatedTile.getTypeToken());

        assertEquals(
                List.of("kappa", "baseFrequencies"),
                generatedTile.getGeneratorTileInputs().stream()
                        .map(input -> input.getPhylospecArgumentName())
                        .toList());

        assertEquals(
                List.of(true, true),
                generatedTile.getGeneratorTileInputs().stream()
                        .map(input -> input.isRequired())
                        .toList());

        assertEquals(
                handwrittenTile.getGeneratorTileInputs().stream()
                        .map(input -> input.getTypeToken())
                        .toList(),
                generatedTile.getGeneratorTileInputs().stream()
                        .map(input -> input.getTypeToken())
                        .toList());

        assertEquals(
                List.of("RealScalar<PositiveReal>", "Simplex"),
                generatedTile.getGeneratorTileInputs().stream()
                        .map(input -> input.getTypeToken().toString())
                        .toList());
    }

    @Test
    public void adaptsFrequenciesLikeHandwrittenTile() {
        RealScalarParam<PositiveReal> kappa = new RealScalarParam<>(2.0, PositiveReal.INSTANCE);

        double[] values = {0.1, 0.2, 0.3, 0.4};

        SimplexParam baseFrequencies = new SimplexParam(values);

        Tile<RealScalar<PositiveReal>, BEASTState> kappaTile =
                HKYGeneratedTileTest.<RealScalar<PositiveReal>>valueTile(kappa);

        Tile<Simplex, BEASTState> frequenciesTile = HKYGeneratedTileTest.<Simplex>valueTile(baseFrequencies);

        HKYTile handwrittenTile = new HKYTile();

        HKYGeneratedTile generatedTile = new HKYGeneratedTile();

        handwrittenTile.kappaInput.setTile(kappaTile);
        handwrittenTile.baseFrequenciesInput.setTile(frequenciesTile);

        generatedTile.kappaInput.setTile(kappaTile);
        generatedTile.baseFrequenciesInput.setTile(frequenciesTile);

        HKY handwrittenResult = handwrittenTile.applyTile(new BEASTState("handwritten-hky"), new IdentityHashMap<>());

        HKY generatedResult = generatedTile.applyTile(new BEASTState("generated-hky"), new IdentityHashMap<>());

        assertInstanceOf(HKY.class, generatedResult);

        assertSame(kappa, handwrittenResult.kappaInput.get());

        assertSame(kappa, generatedResult.kappaInput.get());

        Frequencies handwrittenFrequencies = handwrittenResult.frequenciesInput.get();

        Frequencies generatedFrequencies = generatedResult.frequenciesInput.get();

        assertSame(baseFrequencies, handwrittenFrequencies.frequenciesInput.get());

        assertSame(baseFrequencies, generatedFrequencies.frequenciesInput.get());

        assertArrayEquals(handwrittenFrequencies.getFreqs(), generatedFrequencies.getFreqs(), 1.0e-12);

        assertArrayEquals(values, generatedFrequencies.getFreqs(), 1.0e-12);
    }

    private static <T> Tile<T, BEASTState> valueTile(T value) {

        Tile<T, BEASTState> tile = new Tile<T, BEASTState>() {

            @Override
            protected T applyTile(BEASTState state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {

                return value;
            }
        };

        tile.setIndexVariables(Set.of());

        return tile;
    }
}
