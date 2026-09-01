package tiles.substitutionmodels;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import beast.base.spec.evolution.substitutionmodel.JukesCantor;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.GeneratorTileMappingDescriptor;

public class JC69GeneratedTileTest {

    @Test
    public void exposesSameGeneratorContractAsHandwrittenTile() {
        JC69Tile handwrittenTile = new JC69Tile();
        JC69GeneratedTile generatedTile = new JC69GeneratedTile();

        assertEquals(handwrittenTile.getPhyloSpecGeneratorName(), generatedTile.getPhyloSpecGeneratorName());

        assertEquals(handwrittenTile.getCompatibleStochasticities(), generatedTile.getCompatibleStochasticities());

        assertEquals(handwrittenTile.getPriority(), generatedTile.getPriority());

        assertEquals(
                handwrittenTile.getGeneratorTileInputs().size(),
                generatedTile.getGeneratorTileInputs().size());

        assertTrue(generatedTile.getGeneratorTileInputs().isEmpty());
    }

    @Test
    public void exposesCanonicalPhyloSpecNamespace() {
        JC69GeneratedTile generatedTile = new JC69GeneratedTile();

        assertEquals(Optional.of("phylospec.functions.substitution"), generatedTile.getNamespace());

        GeneratorTileMappingDescriptor descriptor = generatedTile.getMappingDescriptor();

        assertEquals("jc69", descriptor.componentName());

        assertEquals(Optional.of("phylospec.functions.substitution"), descriptor.namespace());

        assertTrue(descriptor.inputs().isEmpty());
    }

    @Test
    public void constructsSameBeastImplementationAsHandwrittenTile() {
        JC69Tile handwrittenTile = new JC69Tile();
        JC69GeneratedTile generatedTile = new JC69GeneratedTile();

        JukesCantor handwrittenResult = handwrittenTile.applyTile(
                new BEASTState("handwritten-jc69"), new IdentityHashMap<Expr.Variable, Integer>());

        JukesCantor generatedResult = generatedTile.applyTile(
                new BEASTState("generated-jc69"), new IdentityHashMap<Expr.Variable, Integer>());

        assertEquals(handwrittenResult.getClass(), generatedResult.getClass());

        assertInstanceOf(JukesCantor.class, generatedResult);
    }
}
