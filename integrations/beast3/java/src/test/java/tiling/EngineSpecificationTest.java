package tiling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import beast.pkgmgmt.BEASTVersion;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.phylospec.components.Argument__1;
import org.phylospec.components.ComponentResolver;
import org.phylospec.components.EngineSpecificationSchema;
import org.phylospec.components.Generator__1;
import org.phylospec.tiling.EngineSpecGenerator;
import tiles.BeastCoreTileLibrary;

/**
 * Runs the same engine specification generation as {@code CreateEngineSpecification}, but without
 * writing the specification to disk. This makes sure that the tiles keep agreeing with the
 * component library: the generation validates every generator tile against it and throws if a tile
 * implements an unknown generator, declares an unknown argument, or declares the wrong argument in
 * first position.
 */
public class EngineSpecificationTest {

    @Test
    public void generatesEngineSpecificationForAllTiles() throws IOException {
        BEASTVersion beastVersion = new BEASTVersion();
        String versionString = beastVersion.getVersion()
                + (beastVersion.isPrerelease() ? ("-" + beastVersion.getPrereleaseDescription()) : "");

        // this throws if a tile does not agree with the component library

        EngineSpecificationSchema specification = EngineSpecGenerator.generateEngineSpecification(
                new BeastCoreTileLibrary(),
                new ComponentResolver(ComponentResolver.loadCoreComponentLibraries()),
                "beast2",
                versionString,
                List.of(),
                "Open the website and download BEAST 2 for your operating system.",
                "https://www.beast2.org/");

        assertEquals("beast2", specification.getName());
        assertEquals(versionString, specification.getEngineVersion());

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

        Generator__1 strictClock = generators.stream()
                .filter(generator -> "StrictClock".equals(generator.getName()))
                .findFirst()
                .orElseThrow();

        assertEquals(
                List.of("clockRate", "tree"),
                strictClock.getArguments().stream().map(Argument__1::getName).toList());
        assertTrue(strictClock.getArguments().stream().allMatch(Argument__1::getRequired));
    }
}
