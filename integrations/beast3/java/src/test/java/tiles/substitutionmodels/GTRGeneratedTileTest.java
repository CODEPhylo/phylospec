package tiles.substitutionmodels;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import beast.base.spec.domain.PositiveReal;
import beast.base.spec.evolution.substitutionmodel.Frequencies;
import beast.base.spec.evolution.substitutionmodel.GTR;
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

public class GTRGeneratedTileTest {

    @Test
    public void exposesSameInputContractAsHandwrittenTile() {
        GTRTile handwrittenTile = new GTRTile();

        GTRGeneratedTile generatedTile = new GTRGeneratedTile();

        assertEquals(handwrittenTile.getPhyloSpecGeneratorName(), generatedTile.getPhyloSpecGeneratorName());

        assertEquals(handwrittenTile.getTypeToken(), generatedTile.getTypeToken());

        assertEquals(
                List.of("rateAC", "rateAG", "rateAT", "rateCG", "rateCT", "rateGT", "baseFrequencies"),
                generatedTile.getGeneratorTileInputs().stream()
                        .map(input -> input.getPhylospecArgumentName())
                        .toList());

        assertEquals(
                List.of(true, true, true, true, true, true, true),
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
                List.of(
                        "RealScalar<PositiveReal>",
                        "RealScalar<PositiveReal>",
                        "RealScalar<PositiveReal>",
                        "RealScalar<PositiveReal>",
                        "RealScalar<PositiveReal>",
                        "RealScalar<PositiveReal>",
                        "Simplex"),
                generatedTile.getGeneratorTileInputs().stream()
                        .map(input -> input.getTypeToken().toString())
                        .toList());
    }

    @Test
    public void mapsRatesAndAdaptsFrequenciesLikeHandwrittenTile() {
        RealScalarParam<PositiveReal> rateAC = rate(1.0);

        RealScalarParam<PositiveReal> rateAG = rate(2.0);

        RealScalarParam<PositiveReal> rateAT = rate(3.0);

        RealScalarParam<PositiveReal> rateCG = rate(4.0);

        RealScalarParam<PositiveReal> rateCT = rate(5.0);

        RealScalarParam<PositiveReal> rateGT = rate(6.0);

        double[] frequencyValues = {0.1, 0.2, 0.3, 0.4};

        SimplexParam baseFrequencies = new SimplexParam(frequencyValues);

        Tile<RealScalar<PositiveReal>, BEASTState> rateACTile = valueTile(rateAC);

        Tile<RealScalar<PositiveReal>, BEASTState> rateAGTile = valueTile(rateAG);

        Tile<RealScalar<PositiveReal>, BEASTState> rateATTile = valueTile(rateAT);

        Tile<RealScalar<PositiveReal>, BEASTState> rateCGTile = valueTile(rateCG);

        Tile<RealScalar<PositiveReal>, BEASTState> rateCTTile = valueTile(rateCT);

        Tile<RealScalar<PositiveReal>, BEASTState> rateGTTile = valueTile(rateGT);

        Tile<Simplex, BEASTState> frequenciesTile = valueTile(baseFrequencies);

        GTRTile handwrittenTile = new GTRTile();

        GTRGeneratedTile generatedTile = new GTRGeneratedTile();

        handwrittenTile.rateACInput.setTile(rateACTile);
        handwrittenTile.rateAGInput.setTile(rateAGTile);
        handwrittenTile.rateATInput.setTile(rateATTile);
        handwrittenTile.rateCGInput.setTile(rateCGTile);
        handwrittenTile.rateCTInput.setTile(rateCTTile);
        handwrittenTile.rateGTInput.setTile(rateGTTile);
        handwrittenTile.baseFrequenciesInput.setTile(frequenciesTile);

        generatedTile.rateACInput.setTile(rateACTile);
        generatedTile.rateAGInput.setTile(rateAGTile);
        generatedTile.rateATInput.setTile(rateATTile);
        generatedTile.rateCGInput.setTile(rateCGTile);
        generatedTile.rateCTInput.setTile(rateCTTile);
        generatedTile.rateGTInput.setTile(rateGTTile);
        generatedTile.baseFrequenciesInput.setTile(frequenciesTile);

        GTR handwrittenResult = handwrittenTile.applyTile(new BEASTState("handwritten-gtr"), new IdentityHashMap<>());

        GTR generatedResult = generatedTile.applyTile(new BEASTState("generated-gtr"), new IdentityHashMap<>());

        assertInstanceOf(GTR.class, generatedResult);

        assertSame(rateAC, handwrittenResult.rateACInput.get());
        assertSame(rateAG, handwrittenResult.rateAGInput.get());
        assertSame(rateAT, handwrittenResult.rateATInput.get());
        assertSame(rateCG, handwrittenResult.rateCGInput.get());
        assertSame(rateCT, handwrittenResult.rateCTInput.get());
        assertSame(rateGT, handwrittenResult.rateGTInput.get());

        assertSame(rateAC, generatedResult.rateACInput.get());
        assertSame(rateAG, generatedResult.rateAGInput.get());
        assertSame(rateAT, generatedResult.rateATInput.get());
        assertSame(rateCG, generatedResult.rateCGInput.get());
        assertSame(rateCT, generatedResult.rateCTInput.get());
        assertSame(rateGT, generatedResult.rateGTInput.get());

        Frequencies handwrittenFrequencies = handwrittenResult.frequenciesInput.get();

        Frequencies generatedFrequencies = generatedResult.frequenciesInput.get();

        assertSame(baseFrequencies, handwrittenFrequencies.frequenciesInput.get());

        assertSame(baseFrequencies, generatedFrequencies.frequenciesInput.get());

        assertArrayEquals(handwrittenFrequencies.getFreqs(), generatedFrequencies.getFreqs(), 1.0e-12);

        assertArrayEquals(frequencyValues, generatedFrequencies.getFreqs(), 1.0e-12);
    }

    private static RealScalarParam<PositiveReal> rate(double value) {

        return new RealScalarParam<>(value, PositiveReal.INSTANCE);
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
