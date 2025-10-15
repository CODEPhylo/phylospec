package org.phylospec.components;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.awt.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class allows to register multiple component libraries and access
 * the generators and types defined in them.
 */
public class ComponentResolver {

    final Map<String, Generator> generators;
    final Map<String, Type> types;
    final List<ComponentLibrary> componentLibraries;

    public ComponentResolver() {
        generators = new HashMap<>();
        types = new HashMap<>();
        componentLibraries = new ArrayList<>();
    }

    /**
     * Registers a component library from a file path.
     */
    public void registerLibraryFromFile(String fileName) throws IOException {
        try (InputStream fileStream = new FileInputStream(fileName)) {
            ObjectMapper mapper = new ObjectMapper();
            ComponentLibrarySchema componentLibrary = mapper.readValue(fileStream, ComponentLibrarySchema.class);
            registerComponentLibrary(componentLibrary.getComponentLibrary());
        }
    }

    /**
     * Registers a component library from a file path.
     */
    public void registerComponentLibrary(ComponentLibrary library) {
        componentLibraries.add(library);
    }

    public boolean canResolveGenerator(String variableName) {
        return generators.containsKey(variableName);
    }

    public Generator resolveGenerator(String variableName) {
        return generators.get(variableName);
    }

    public boolean canResolveType(String typeName) {
        return types.containsKey(typeName);
    }

    public Type resolveType(String variableName) {
        return types.get(variableName);
    }

    public void importEntireNamespace(List<String> namespace) {
        String namespaceString = String.join(".", namespace);
        String namespaceStringWithDot = String.join(".", namespace) + ".";

        for (ComponentLibrary library : componentLibraries) {
            for (Generator generator : library.getGenerators()) {
                if (
                        generator.getNamespace().equals(namespaceString)
                                || generator.getNamespace().startsWith(namespaceStringWithDot)
                ) {
                    this.generators.put(generator.getName(), generator);
                }
            }
            for (Type type : library.getTypes()) {
                if (
                        type.getNamespace().equals(namespaceString)
                                || type.getNamespace().startsWith(namespaceStringWithDot)
                ) {
                    this.types.put(type.getName(), type);
                }
            }
        }
    }

    public void importNamespace(List<String> namespace) {
        String namespaceString = String.join(".", namespace);

        for (ComponentLibrary library : componentLibraries) {
            for (Generator generator : library.getGenerators()) {
                if (generator.getNamespace().equals(namespaceString)) {
                    this.generators.put(generator.getName(), generator);
                }
            }
            for (Type type : library.getTypes()) {
                if (type.getNamespace().equals(namespaceString)) {
                    this.types.put(type.getName(), type);
                }
            }
        }
    }
}
