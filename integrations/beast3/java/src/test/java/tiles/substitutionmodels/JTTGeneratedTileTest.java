package tiles.substitutionmodels;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import beast.base.spec.evolution.substitutionmodel.Frequencies;
import beast.base.spec.evolution.substitutionmodel.JTT;
import beast.base.spec.inference.parameter.SimplexParam;
import beast.base.spec.type.Simplex;
import beastconfig.BEASTState;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.tiles.Tile;

public class JTTGeneratedTileTest {

    @Test
    public void exposesSameInputContractAsHandwrittenTile() {
        JTTTile handwrittenTile = new JTTTile();

        JTTGeneratedTile generatedTile = new JTTGeneratedTile();

        assertEquals(handwrittenTile.getPhyloSpecGeneratorName(), generatedTile.getPhyloSpecGeneratorName());

        assertEquals(handwrittenTile.getTypeToken(), generatedTile.getTypeToken());

        assertEquals(
                List.of("baseFrequencies"),
                generatedTile.getGeneratorTileInputs().stream()
                        .map(input -> input.getPhylospecArgumentName())
                        .toList());

        assertEquals(
                List.of(false),
                generatedTile.getGeneratorTileInputs().stream()
                        .map(input -> input.isRequired())
                        .toList());

        assertEquals(
                handwrittenTile.getGeneratorTileInputs().getFirst().getTypeToken(),
                generatedTile.getGeneratorTileInputs().getFirst().getTypeToken());

        assertEquals(
                "Simplex",
                generatedTile.getGeneratorTileInputs().getFirst().getTypeToken().toString());
    }

    @Test
    public void adaptsProvidedBaseFrequencies() {
        double[] values = new double[20];

        Arrays.fill(values, 1.0 / values.length);

        SimplexParam baseFrequencies = new SimplexParam(values);

        Tile<Simplex, BEASTState> frequenciesTile = JTTGeneratedTileTest.<Simplex>valueTile(baseFrequencies);

        JTTTile handwrittenTile = new JTTTile();

        JTTGeneratedTile generatedTile = new JTTGeneratedTile();

        handwrittenTile.baseFrequenciesInput.setTile(frequenciesTile);

        generatedTile.baseFrequenciesInput.setTile(frequenciesTile);

        JTT handwrittenResult = handwrittenTile.applyTile(new BEASTState("handwritten-jtt"), new IdentityHashMap<>());

        JTT generatedResult = generatedTile.applyTile(new BEASTState("generated-jtt"), new IdentityHashMap<>());

        assertInstanceOf(JTT.class, generatedResult);

        Frequencies handwrittenFrequencies = handwrittenResult.frequenciesInput.get();

        Frequencies generatedFrequencies = generatedResult.frequenciesInput.get();

        assertSame(baseFrequencies, handwrittenFrequencies.frequenciesInput.get());

        assertSame(baseFrequencies, generatedFrequencies.frequenciesInput.get());

        assertArrayEquals(handwrittenFrequencies.getFreqs(), generatedFrequencies.getFreqs(), 1.0e-12);

        assertArrayEquals(values, generatedFrequencies.getFreqs(), 1.0e-12);
    }

    @Test
    public void skipsAdapterWhenBaseFrequenciesAreOmitted() {
        JTTTile handwrittenTile = new JTTTile();

        JTTGeneratedTile generatedTile = new JTTGeneratedTile();

        BEASTState handwrittenState = new BEASTState("handwritten-default-jtt");

        BEASTState generatedState = new BEASTState("generated-default-jtt");

        JTT handwrittenResult = handwrittenTile.applyTile(handwrittenState, new IdentityHashMap<>());

        JTT generatedResult = generatedTile.applyTile(generatedState, new IdentityHashMap<>());

        assertNull(handwrittenResult.frequenciesInput.get());

        assertNull(generatedResult.frequenciesInput.get());

        /*
         * The handwritten Tile initializes JTT immediately when no
         * frequencies are provided. Generated Tiles use the normal
         * BEASTState lifecycle, so initialize the returned object
         * before inspecting its empirical frequencies.
         */
        generatedState.initBEASTObject(generatedResult);

        assertEquals(handwrittenResult.getStateCount(), generatedResult.getStateCount());

        assertArrayEquals(handwrittenResult.getFrequencies(), generatedResult.getFrequencies(), 1.0e-12);
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
