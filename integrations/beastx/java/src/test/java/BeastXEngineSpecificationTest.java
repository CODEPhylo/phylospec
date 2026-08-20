import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import dr.app.beast.BeastVersion;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.phylospec.components.Argument__1;
import org.phylospec.components.ComponentResolver;
import org.phylospec.components.EngineSpecificationSchema;
import org.phylospec.components.Generator__1;
import org.phylospec.tiling.EngineSpecGenerator;
import tiles.BeastXCoreTileLibrary;

/**
 * Runs the same engine specification generation as {@code CreateEngineSpecification}, but without
 * writing the specification to disk. This makes sure that the tiles keep agreeing with the
 * component library: the generation validates every generator tile against it and throws if a tile
 * implements an unknown generator, declares an unknown argument, or declares the wrong argument in
 * first position.
 */
public class BeastXEngineSpecificationTest {

    @Test
    public void generatesEngineSpecificationForAllTiles() throws IOException {
        BeastVersion beastVersion = new BeastVersion();

        // this throws if a tile does not agree with the component library

        EngineSpecificationSchema specification = EngineSpecGenerator.generateEngineSpecification(
                new BeastXCoreTileLibrary(),
                new ComponentResolver(ComponentResolver.loadCoreComponentLibraries()),
                "beastX",
                beastVersion.getVersion(),
                List.of(),
                "Open the website and download BEAST X for your operating system.",
                "https://beast.community/");

        assertEquals("beastX", specification.getName());
        assertEquals(beastVersion.getVersion(), specification.getEngineVersion());

        // the tile library covers generators, so an empty specification means the generation
        // silently did not pick up any tile

        List<Generator__1> generators = specification.getGenerators();
        assertNotNull(generators);
        assertFalse(generators.isEmpty(), "The engine specification does not contain any generator.");

        for (Generator__1 generator : generators) {
            assertNotNull(generator.getName());
            assertFalse(generator.getName().isBlank());
            assertNotNull(generator.getArguments(), "The generator '" + generator.getName() + "' has no arguments.");

            for (Argument__1 argument : generator.getArguments()) {
                assertNotNull(argument.getName());
                assertFalse(argument.getName().isBlank());
                assertNotNull(argument.getRequired());
                assertNotNull(argument.getCanBeStochastic());
            }
        }
    }
}
