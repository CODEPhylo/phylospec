import dr.app.beast.BeastVersion;
import org.phylospec.tiling.EngineSpecGenerator;
import tiles.BeastXCoreTileLibrary;

import java.io.IOException;
import java.util.List;

/**
 * Generates the BEAST X engine specification and writes it to the generated folder.
 */
public class CreateEngineSpecification {

    static void main() throws IOException {
        BeastVersion beastVersion = new BeastVersion();

        EngineSpecGenerator.writeEngineSpecification(
                new BeastXCoreTileLibrary(),
                "beastX",
                beastVersion.getVersion(),
                List.of(),
                "Open the website and download BEAST X for your operating system.",
                "https://beast.community/"
        );
    }

}
