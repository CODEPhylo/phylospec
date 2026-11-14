package utils.phylonco;

import beastconfig.BEASTState;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.phylospec.components.ComponentResolver;
import org.phylospec.components.EngineSpecificationSchema;
import org.phylospec.tiling.EngineSpecGenerator;
import org.phylospec.tiling.TileLibrary;
import tiles.phylonco.PhyloncoTileLibrary;

public final class PhyloncoEngineSpec {

    public static final String ENGINE_NAME = "phylonco";
    public static final List<String> DEPENDENCIES = List.of("beast2");
    public static final String INSTALLATION_INSTRUCTIONS =
            "Install the phylonco BEAST package (beast3 branch).";
    public static final String INSTALLATION_WEBSITE =
            "https://github.com/bioDS/beast-phylonco";

    private PhyloncoEngineSpec() {}

    public static EngineSpecificationSchema create(String version) throws IOException {
        PhyloncoTileLibrary library = new PhyloncoTileLibrary();
        ComponentResolver resolver = componentResolver(library);

        return EngineSpecGenerator.generateEngineSpecification(
                library,
                resolver,
                ENGINE_NAME,
                version,
                DEPENDENCIES,
                INSTALLATION_INSTRUCTIONS,
                INSTALLATION_WEBSITE);
    }

    public static void write(Path outputDirectory, String version) throws IOException {
        PhyloncoTileLibrary library = new PhyloncoTileLibrary();
        ComponentResolver resolver = componentResolver(library);

        EngineSpecGenerator.writeEngineSpecification(
                outputDirectory,
                library,
                resolver,
                ENGINE_NAME,
                version,
                DEPENDENCIES,
                INSTALLATION_INSTRUCTIONS,
                INSTALLATION_WEBSITE);
    }

    public static void writeResource(Path outputFile, String version) throws IOException {
        EngineSpecGenerator.writeEngineSpecification(outputFile, create(version));
    }

    public static void main(String[] arguments) throws IOException {
        if (arguments.length != 2) {
            throw new IllegalArgumentException(
                    "Usage: PhyloncoEngineSpec <output-file> <phylonco-version>");
        }

        writeResource(Path.of(arguments[0]), arguments[1]);
    }

    private static ComponentResolver componentResolver(
            TileLibrary<BEASTState> library) throws IOException {
        return new ComponentResolver(
                TileLibrary.loadComponentLibraries(List.of(library)));
    }
}
