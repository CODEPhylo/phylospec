package org.phylospec.components;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
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

    public ComponentResolver() {
        generators = new HashMap<>();
        types = new HashMap<>();
    }

    /**
     * Registers a component library from a file path.
     */
    public void registerLibraryFromFile(String fileName) throws IOException {
        try(InputStream fileStream = new FileInputStream(fileName)) {
            ObjectMapper mapper = new ObjectMapper();
            ComponentLibrarySchema componentLibrary = mapper.readValue(fileStream, ComponentLibrarySchema.class);
            registerComponentLibrary(componentLibrary.getComponentLibrary());
        }
    }

    /**
     * Registers a component library from a file path.
     */
    public void registerComponentLibrary(ComponentLibrary library) {
        for (Generator generator : library.getGenerators()) {
            generators.put(generator.getName(), generator);
        }
        for (Type type : library.getTypes()) {
            types.put(type.getName(), type);
        }
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

    public void importNamespace(List<String> importPath) {
    }
}
