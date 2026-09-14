package utils.popfunc;

import beastconfig.BEASTState;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.phylospec.components.ComponentResolver;
import org.phylospec.components.EngineSpecificationSchema;
import org.phylospec.tiling.EngineSpecGenerator;
import org.phylospec.tiling.TileLibrary;
import tiles.popfunc.PopFuncTileLibrary;

public final class PopFuncEngineSpec {

    public static final String ENGINE_NAME = "popfunc";
    public static final List<String> DEPENDENCIES = List.of("beast2");
    public static final String INSTALLATION_INSTRUCTIONS =
            "Install the PopFunc BEAST package.";
    public static final String INSTALLATION_WEBSITE =
            "https://github.com/LinguaPhylo/PopFunc";

    private PopFuncEngineSpec() {}

    public static EngineSpecificationSchema create(String version) throws IOException {
        PopFuncTileLibrary library = new PopFuncTileLibrary();
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
        PopFuncTileLibrary library = new PopFuncTileLibrary();
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
                    "Usage: PopFuncEngineSpec <output-file> <popfunc-version>");
        }

        writeResource(Path.of(arguments[0]), arguments[1]);
    }

    private static ComponentResolver componentResolver(
            TileLibrary<BEASTState> library) throws IOException {
        return new ComponentResolver(
                TileLibrary.loadComponentLibraries(List.of(library)));
    }
}
