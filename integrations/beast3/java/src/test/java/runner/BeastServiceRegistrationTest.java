package runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import beast.base.core.BEASTInterface;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * BEAST's XMLParser only creates classes that are registered as BEASTInterface services.
 * The integration registers its own classes in {@code version.xml}, so an exported analysis can be
 * loaded again. These tests keep that list complete when classes are added.
 */
class BeastServiceRegistrationTest {

    /** Public BEAST classes that cannot be created from XML yet, so registering them would not help. */
    private static final Set<String> NOT_LOADABLE_FROM_XML = Set.of(
            // No no-arg constructor, and its age, trees and driver are not BEAST inputs.
            "tiling.RootAgeOperator");

    @Test
    void everyBeastClassOfTheIntegrationIsRegistered() throws Exception {
        Path classes = classesDirectory();
        Set<String> registered = registeredProviders(classes);

        List<String> missing = new ArrayList<>();
        try (Stream<Path> files = Files.walk(classes)) {
            for (Path file :
                    files.filter(path -> path.toString().endsWith(".class")).toList()) {
                String name = classes.relativize(file)
                        .toString()
                        .replace(file.getFileSystem().getSeparator(), ".")
                        .replaceAll("\\.class$", "");
                if (name.endsWith("module-info")) continue;

                Class<?> type = Class.forName(name, false, getClass().getClassLoader());
                if (canBeNamedInXml(type) && !registered.contains(name) && !NOT_LOADABLE_FROM_XML.contains(name)) {
                    missing.add(name);
                }
            }
        }

        assertEquals(List.of(), missing, "Register these classes in src/main/resources/version.xml");
    }

    @Test
    void everyRegisteredClassCanBeCreatedByTheXmlParser() throws Exception {
        for (String name : registeredProviders(classesDirectory())) {
            Class<?> type = Class.forName(name);

            assertTrue(BEASTInterface.class.isAssignableFrom(type), name + " is not a BEASTInterface");
            assertTrue(
                    Modifier.isPublic(type.getConstructor().getModifiers()),
                    name + " needs a public no-arg constructor");
        }
    }

    /** A concrete BEAST class that a spec="..." attribute can refer to. */
    private static boolean canBeNamedInXml(Class<?> type) {
        boolean namedPublicClass = Modifier.isPublic(type.getModifiers())
                && (type.getEnclosingClass() == null || Modifier.isStatic(type.getModifiers()));
        return BEASTInterface.class.isAssignableFrom(type)
                && !type.isInterface()
                && !Modifier.isAbstract(type.getModifiers())
                && namedPublicClass;
    }

    private static Path classesDirectory() throws Exception {
        return Path.of(PhyloSpecRunner.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
    }

    private static Set<String> registeredProviders(Path classes) throws Exception {
        var document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(classes.resolve("version.xml").toFile());
        NodeList providers = document.getElementsByTagName("provider");

        Set<String> names = new HashSet<>();
        for (int i = 0; i < providers.getLength(); i++) {
            names.add(((Element) providers.item(i)).getAttribute("classname"));
        }
        return names;
    }
}
