package tiling.popfunc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import beastconfig.BEASTState;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.phylospec.components.ComponentLibrary;
import org.phylospec.components.ComponentResolver;
import org.phylospec.components.Generator;
import org.phylospec.tiling.tiles.CandidateTile;
import org.phylospec.tiling.tiles.GeneratorTile;
import tiles.popfunc.PopFuncTileLibrary;
import tiles.popfunc.generated.GeneratedTileRegistry;

public class PopFuncCoverageTest {

    @Test
    public void implementsEveryPackageGeneratorExactlyOnce() throws IOException {
        PopFuncTileLibrary library = new PopFuncTileLibrary();
        Set<String> declaredGenerators = loadOwnedComponents(library).stream()
                .flatMap(componentLibrary -> componentLibrary.getGenerators().stream())
                .map(PopFuncCoverageTest::qualifiedName)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, Long> implementationCounts = library.getTiles().stream()
                .filter(GeneratorTile.class::isInstance)
                .map(GeneratorTile.class::cast)
                .map(tile -> tile.getMappingDescriptor().signature().qualifiedComponentName())
                .filter(declaredGenerators::contains)
                .collect(Collectors.groupingBy(
                        Function.identity(), TreeMap::new, Collectors.counting()));

        Map<String, Long> expectedCounts = declaredGenerators.stream()
                .collect(Collectors.toMap(
                        Function.identity(),
                        ignored -> 1L,
                        (left, right) -> left,
                        TreeMap::new));

        assertEquals(expectedCounts, implementationCounts);
    }

    @Test
    public void registersEveryGeneratedTileExactlyOnce() {
        List<CandidateTile<BEASTState>> registeredTiles =
                new PopFuncTileLibrary().getTiles();
        Set<Class<?>> generatedTileClasses = GeneratedTileRegistry.createTiles().stream()
                .map(Object::getClass)
                .collect(Collectors.toSet());

        Map<Class<?>, Long> registrationCounts = registeredTiles.stream()
                .map(Object::getClass)
                .filter(generatedTileClasses::contains)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        Map<Class<?>, Long> expectedCounts = generatedTileClasses.stream()
                .collect(Collectors.toMap(Function.identity(), ignored -> 1L));

        assertEquals(expectedCounts, registrationCounts);
    }

    private static List<ComponentLibrary> loadOwnedComponents(PopFuncTileLibrary library)
            throws IOException {
        return library.getComponentLibraryResources().stream()
                .map(resource -> loadComponent(library, resource))
                .toList();
    }

    private static ComponentLibrary loadComponent(
            PopFuncTileLibrary library, String resource) {
        try (InputStream input = library.getClass().getResourceAsStream(resource)) {
            assertNotNull(input, "Missing component library resource " + resource);
            return ComponentResolver.loadLibraryFromInputStream(input);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not load component library resource " + resource, exception);
        }
    }

    private static String qualifiedName(Generator generator) {
        return generator.getNamespace() + "." + generator.getName();
    }
}
