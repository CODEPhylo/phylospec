package utils;

import beast.pkgmgmt.BEASTVersion;
import org.phylospec.components.ComponentResolver;
import org.phylospec.tiling.EngineSpecGenerator;
import tiles.BeastCoreTileLibrary;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Generates the BEAST 2 engine specification and writes it to the generated folder.
 */
public class CreateEngineSpecification {

    static void main() throws IOException {
        BEASTVersion beastVersion = new BEASTVersion();
        String versionString = beastVersion.getVersion() + (beastVersion.isPrerelease() ? ("-" + beastVersion.getPrereleaseDescription()) : "");

        EngineSpecGenerator.writeEngineSpecification(
                Path.of("generated"),
                new BeastCoreTileLibrary(),
                new ComponentResolver(ComponentResolver.loadCoreComponentLibraries()),
                "beast2",
                versionString,
                List.of(),
                "Open the website and download BEAST 2 for your operating system.",
                "https://www.beast2.org/"
        );
    }

}
