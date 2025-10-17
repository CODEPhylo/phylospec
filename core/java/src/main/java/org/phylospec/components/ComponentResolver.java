package org.phylospec.components;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.phylospec.ast.TypeError;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * This class allows to register multiple component libraries and access
 * the generators and types defined in them.
 */
public class ComponentResolver {

    final List<ComponentLibrary> componentLibraries;
    final Set<String> knownNamespaces;

    final Map<String, List<Generator>> importedGenerators;  // there might be multiple generators with the same name
    final Map<String, Type> importedTypes;

    public ComponentResolver() {
        componentLibraries = new ArrayList<>();
        knownNamespaces = new HashSet<>();
        importedGenerators = new HashMap<>();
        importedTypes = new HashMap<>();
    }

    /**
     * Registers a component library from a file path.
     * Note that this does not make the types and generators resolvable. For
     * this, the appropriate namespace has to be imported first using either
     * `importEntireNamespace` or `importNamespace`.
     */
    public void registerLibraryFromFile(String fileName) throws IOException {
        try (InputStream fileStream = new FileInputStream(fileName)) {
            ObjectMapper mapper = new ObjectMapper();
            ComponentLibrarySchema componentLibrary = mapper.readValue(fileStream, ComponentLibrarySchema.class);
            registerComponentLibrary(componentLibrary.getComponentLibrary());
        }
    }

    /**
     * Registers a component library.
     * Note that this does not make the types and generators resolvable ("import" them).
     * For this, the appropriate namespace has to be imported first using either
     * `importEntireNamespace` or `importNamespace`.
     */
    public void registerComponentLibrary(ComponentLibrary library) {
        componentLibraries.add(library);

        for (Generator generator : library.getGenerators()) {
            String namespace = generator.getNamespace();
            for (int i = 0; i < namespace.length(); i++) {
                if (namespace.charAt(i) == '.')
                    knownNamespaces.add(namespace.substring(0, i));
            }
            knownNamespaces.add(namespace);
        }
        for (Type type : library.getTypes()) {
            String namespace = type.getNamespace();
            for (int i = 0; i < namespace.length(); i++) {
                if (namespace.charAt(i) == '.')
                    knownNamespaces.add(namespace.substring(0, i));
            }
            knownNamespaces.add(namespace);
        }
    }

    /**
     * Imports a namespace. This makes all registered components and types
     * in that namespace resolvable. Throws a {@link TypeError}
     * if the namespace is not known.
     */
    public void importNamespace(List<String> namespace) {
        String namespaceString = String.join(".", namespace);

        if (!knownNamespaces.contains(namespaceString)) {
            throw new TypeError("Import " + namespaceString + " is not known");
        }

        for (ComponentLibrary library : componentLibraries) {
            for (Generator generator : library.getGenerators()) {
                if (generator.getNamespace().equals(namespaceString)) {
                    this.importedGenerators.computeIfAbsent(generator.getName(), k -> new ArrayList<>()).add(generator);
                }
            }
            for (Type type : library.getTypes()) {
                if (type.getNamespace().equals(namespaceString)) {
                    this.importedTypes.put(type.getName(), type);
                }
            }
        }
    }

    /**
     * Imports a namespace and its sub-namespaces. This makes all registered components
     * and types in that namespace resolvable. Throws a {@link TypeError}
     * if the namespace is not known.
     */
    public void importEntireNamespace(List<String> namespace) {
        String namespaceString = String.join(".", namespace);
        String namespaceStringWithDot = String.join(".", namespace) + ".";

        if (!knownNamespaces.contains(namespaceString)) {
            throw new TypeError("Import " + namespaceString + " is not known");
        }

        for (ComponentLibrary library : componentLibraries) {
            for (Generator generator : library.getGenerators()) {
                if (
                        generator.getNamespace().equals(namespaceString)
                                || generator.getNamespace().startsWith(namespaceStringWithDot)
                ) {
                    this.importedGenerators.computeIfAbsent(generator.getName(), k -> new ArrayList<>()).add(generator);
                }
            }
            for (Type type : library.getTypes()) {
                if (
                        type.getNamespace().equals(namespaceString)
                                || type.getNamespace().startsWith(namespaceStringWithDot)
                ) {
                    this.importedTypes.put(type.getName(), type);
                }
            }
        }
    }

    /** Returns whether a given generatorName can be resolved. */
    public boolean canResolveGenerator(String generatorName) {
        return importedGenerators.containsKey(generatorName);
    }

    /** Returns the {@link Generator} corresponding to the given name. */
    public List<Generator> resolveGenerator(String generatorName) {
        return importedGenerators.get(generatorName);
    }

    /** Returns whether a given typeName can be resolved. */
    public boolean canResolveType(String typeName) {
        return importedTypes.containsKey(typeName);
    }

    /** Returns the {@link Type} corresponding to the given name. */
    public Type resolveType(String typeName) {
        return importedTypes.get(typeName);
    }
}
