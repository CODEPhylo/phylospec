package org.phylospec.components;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import org.phylospec.Utils;
import org.phylospec.typeresolver.TypeError;

/**
 * This class allows to register multiple component libraries and access
 * the generators and types defined in them.
 */
public class ComponentResolver {

    /** The central repository the component libraries are distributed through. */
    public static final String CENTRAL_REPOSITORY_URI = "https://github.com/tochsner/phylospec-repository.git";

    final List<ComponentLibrary> componentLibraries;
    final Set<String> knownNamespaces;

    private final Map<String, List<Generator>> knownGenerators; // there might be multiple generators with the same name
    private final Map<String, Type> knownTypes;

    /**
     * Registers the newest version of every component library of the given repository.
     */
    public ComponentResolver(ComponentRepository repository) {
        this(repository.getLatestLibraries());
    }

    public ComponentResolver(List<ComponentLibrary> componentLibraries) {
        this.componentLibraries = new ArrayList<>();
        this.knownNamespaces = new HashSet<>();
        this.knownGenerators = new HashMap<>();
        this.knownTypes = new HashMap<>();

        for (ComponentLibrary library : componentLibraries) {
            this.registerComponentLibrary(library);
        }
        this.importEntireNamespace(List.of("phylospec"));
    }

    /**
     * Loads a component library from a file path.
     */
    public static ComponentLibrary loadLibraryFromFile(String fileName) throws IOException {
        try (InputStream fileStream = new FileInputStream(fileName)) {
            ObjectMapper mapper = new ObjectMapper();
            ComponentLibrarySchema componentLibrary = mapper.readValue(fileStream, ComponentLibrarySchema.class);
            return componentLibrary.getComponentLibrary();
        }
    }

    /**
     * Loads a component library from an input stream.
     */
    public static ComponentLibrary loadLibraryFromInputStream(InputStream fileStream) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        ComponentLibrarySchema componentLibrary = mapper.readValue(fileStream, ComponentLibrarySchema.class);
        return componentLibrary.getComponentLibrary();
    }

    /**
     * Loads the core component library.
     */
    public static List<ComponentLibrary> loadCoreComponentLibraries() throws IOException {
        List<ComponentLibrary> libraries = new ArrayList<>();
        libraries.add(loadLibraryFromInputStream(
                ComponentResolver.class.getResourceAsStream("/phylospec-core-component-library.json")));
        return libraries;
    }

    /**
     * Loads all component libraries of the given repository, including all their versions.
     * <p>
     * The repository is cached on disk, so it is only downloaded if the remote has changed. If
     * there is no internet connection, the cached version is used.
     */
    public static ComponentRepository loadRepository(String repoUri) throws IOException {
        return ComponentRepository.load(repoUri);
    }

    /**
     * Loads all component libraries of the central PhyloSpec repository, including all their versions.
     * <p>
     * If the repository can neither be reached nor found in the cache, this falls back to the
     * core component library bundled with this package.
     */
    public static ComponentRepository loadCentralRepository() throws IOException {
        try {
            return loadRepository(CENTRAL_REPOSITORY_URI);
        } catch (IOException e) {
            // we have neither an internet connection nor a cached version, so we fall back to
            // the bundled core component library
            return new ComponentRepository(CENTRAL_REPOSITORY_URI, loadCoreComponentLibraries());
        }
    }

    /**
     * Registers a component library.
     * Note that this does not make the types and generators resolvable ("import" them).
     * For this, the appropriate namespace has to be imported first using either
     * `importEntireNamespace` or `importNamespace`.
     */
    private void registerComponentLibrary(ComponentLibrary library) {
        componentLibraries.add(library);

        // register namespaces

        for (Generator generator : library.getGenerators()) {
            String namespace = generator.getNamespace();
            for (int i = 0; i < namespace.length(); i++) {
                if (namespace.charAt(i) == '.') knownNamespaces.add(namespace.substring(0, i));
            }
            knownNamespaces.add(namespace);
        }
        for (Type type : library.getTypes()) {
            String namespace = type.getNamespace();
            for (int i = 0; i < namespace.length(); i++) {
                if (namespace.charAt(i) == '.') knownNamespaces.add(namespace.substring(0, i));
            }
            knownNamespaces.add(namespace);
        }

        // make all components resolvable with their qualified name. this allows imported components
        // to reference
        // not-imported components (e.g. for their parameter types)

        for (Generator generator : library.getGenerators()) {
            String fullyQualifiedName = getFullyQualifiedName(generator);
            this.knownGenerators.computeIfAbsent(fullyQualifiedName, x -> new ArrayList<>());
            this.knownGenerators.get(fullyQualifiedName).add(generator);
        }
        for (Type type : library.getTypes()) {
            if (type.getName().contains(".")) {
                throw new IllegalArgumentException(
                        "The type name '"
                                + type.getName()
                                + "' contains periods. This is not allowed. If it contains the namespace as well, just remove it.");
            }

            String fullyQualifiedName = getFullyQualifiedName(type);

            if (this.knownTypes.containsKey(fullyQualifiedName)) {
                throw new IllegalArgumentException(
                        "The type '" + fullyQualifiedName + "' is registered multiple times. This is not allowed.");
            }

            this.knownTypes.put(fullyQualifiedName, type);
            type.setName(fullyQualifiedName);
        }

        // turn all internal references into fully-qualified references

        for (Generator generator : library.getGenerators()) {
            String namespace = generator.getNamespace();
            List<String> parameterTypes = generator.getTypeParameters();

            generator.setGeneratedType(getFullyQualifiedType(generator.getGeneratedType(), namespace, parameterTypes));

            for (Argument argument : generator.getArguments()) {
                argument.setType(getFullyQualifiedType(argument.getType(), namespace, parameterTypes));
            }
        }

        for (Type type : library.getTypes()) {
            String namespace = type.getNamespace();
            List<String> parameterTypes = type.getTypeParameters();

            type.setAlias(getFullyQualifiedType(type.getAlias(), namespace, parameterTypes));
            type.setExtends(getFullyQualifiedType(type.getExtends(), namespace, parameterTypes));
        }

        // validate imported components

        this.checkTypeParameters();
    }

    /**
     * For an unqualified type name, finds the fully qualified type name which is closest in the namespace.
     * Closest means that the two namespaces have the most levels in common before they diverge.
     */
    private String getFullyQualifiedType(String typeName, String namespace, List<String> typeParameters) {
        if (typeName == null) return null;
        if (typeParameters.contains(typeName)) return typeName;

        ParsedType parsedType = new ParsedType(typeName);

        // get the fully qualified name for the base type

        String unqualifiedBaseName = parsedType.getAtomicTypeName();
        String bestQualifiedBaseName = null;
        int bestScore = -Integer.MAX_VALUE;

        String[] splitNamespace = splitNamespace(namespace);

        for (Type candidateType : this.knownTypes.values()) {
            ParsedType parsedCandidateType = new ParsedType(candidateType.getName());

            String unqualifiedCandidateBaseName = parsedCandidateType.getAtomicTypeName();
            if (!Objects.equals(unqualifiedCandidateBaseName, unqualifiedBaseName)) continue;

            // these types have the same unqualified name. let's check how close their namespaces
            // are

            String[] splitCandidateNamespace = parsedCandidateType.getSplitNamespace();

            int sharedPrefixLength = 0;
            for (int i = 0; i < Math.min(splitNamespace.length, splitCandidateNamespace.length); i++) {
                if (splitNamespace[i].equals(splitCandidateNamespace[i])) {
                    sharedPrefixLength++;
                } else {
                    break;
                }
            }

            if (bestScore < sharedPrefixLength) {
                bestQualifiedBaseName = candidateType.getName();
                bestScore = sharedPrefixLength;
            }
        }

        if (bestQualifiedBaseName == null) {
            throw new IllegalArgumentException("Unknown type '"
                    + typeName
                    + "' is mentioned in '"
                    + namespace
                    + "'. This is not allowed. Are the component libraries registered in the correct order?");
        }

        // get the fully qualified names for the parameter types

        List<String> parameterTypesNames = new ArrayList<>();
        for (var parameter : parsedType.getTypeParameters()) {
            String fullyQualifiedType =
                    this.getFullyQualifiedType(parameter.getTypeString(), namespace, typeParameters);
            parameterTypesNames.add(fullyQualifiedType);
        }

        // add type parameters and properties

        if (!parameterTypesNames.isEmpty() || !parsedType.getTypeProperties().isEmpty()) {
            bestQualifiedBaseName += "<" + String.join(",", parameterTypesNames);

            if (!parsedType.getTypeProperties().isEmpty()) {
                bestQualifiedBaseName += ";";

                List<String> propertyStrings = parsedType.getTypeProperties().stream()
                        .map(Object::toString)
                        .toList();
                bestQualifiedBaseName += String.join(",", propertyStrings);
            }

            bestQualifiedBaseName += ">";
        }

        return bestQualifiedBaseName;
    }

    /**
     * Imports a namespace. This makes all registered components and types
     * in that namespace resolvable by their unqualified name. Throws a {@link TypeError}
     * if the namespace is not known.
     */
    public void importNamespace(List<String> namespace) {
        String namespaceString = String.join(".", namespace);

        if (!knownNamespaces.contains(namespaceString)) {
            String closestNamespace = findClosestNamespace(namespaceString);
            throw new TypeError(
                    "The import '" + namespaceString + "' does not exist.",
                    "Do you want to use '" + closestNamespace + "'?",
                    List.of("use " + closestNamespace));
        }

        for (ComponentLibrary library : componentLibraries) {
            for (Generator generator : library.getGenerators()) {
                if (generator.getNamespace().equals(namespaceString)) {
                    this.knownGenerators.computeIfAbsent(generator.getName(), x -> new ArrayList<>());

                    // we only allow multiple generators of the same namespace, otherwise this
                    // import
                    // shadows all others
                    this.knownGenerators.get(generator.getName()).removeIf(g -> !g.getNamespace()
                            .equals(generator.getNamespace()));

                    this.knownGenerators.get(generator.getName()).add(generator);
                }
            }
            for (Type type : library.getTypes()) {
                if (type.getNamespace().equals(namespaceString)) {
                    String atomicTypeName = new ParsedType(type.getName()).getAtomicTypeName();
                    this.knownTypes.put(atomicTypeName, type);
                }
            }
        }
    }

    /**
     * Imports a namespace and its sub-namespaces. This makes all registered components
     * and types in that namespace resolvable by their qualified name. Throws a {@link TypeError}
     * if the namespace is not known.
     */
    public void importEntireNamespace(List<String> namespace) {
        String namespaceString = String.join(".", namespace);
        String namespaceStringWithDot = String.join(".", namespace) + ".";

        if (!knownNamespaces.contains(namespaceString)) {
            String closestNamespace = findClosestNamespace(namespaceString);
            throw new TypeError(
                    "The import '" + namespaceString + "' does not exist.",
                    "Do you want to use '" + closestNamespace + "'?",
                    List.of("use " + closestNamespace));
        }

        for (ComponentLibrary library : componentLibraries) {
            for (Generator generator : library.getGenerators()) {
                if (generator.getNamespace().equals(namespaceString)
                        || generator.getNamespace().startsWith(namespaceStringWithDot)) {
                    this.knownGenerators
                            .computeIfAbsent(generator.getName(), k -> new ArrayList<>())
                            .add(generator);
                }
            }
            for (Type type : library.getTypes()) {
                if (type.getNamespace().equals(namespaceString)
                        || type.getNamespace().startsWith(namespaceStringWithDot)) {
                    String atomicTypeName = new ParsedType(type.getName()).getAtomicTypeName();
                    this.knownTypes.put(atomicTypeName, type);
                }
            }
        }
    }

    /**
     * Checks if generic type parameters are always specified if needed.
     * Type parameters have to be specified for all return values of generators.
     */
    private void checkTypeParameters() {
        for (List<Generator> generators : knownGenerators.values()) {
            for (Generator generator : generators) {
                String generatedTypeName = generator.getGeneratedType();
                ParsedType generatedType = new ParsedType(generatedTypeName);
                checkTypeParameters(generatedType, generator);
            }
        }
    }

    /**
     * Recursively checks if potential generic parameters types are specified for the given typeName.
     */
    private void checkTypeParameters(ParsedType type, Generator generator) {
        Type generatedType = resolveType(type.getTypeString());

        List<ParsedType> specifiedParameterTypes = type.getTypeParameters();

        if (specifiedParameterTypes.size() != generatedType.getTypeParameters().size()) {
            throw new IllegalArgumentException("Invalid number of type parameters of the generated type of '"
                    + generator.getName()
                    + "'. Type '"
                    + type.getAtomicTypeName()
                    + "' takes "
                    + generatedType.getTypeParameters().size()
                    + " type parameters.");
        }

        // recursively check parameter types

        for (ParsedType parameterType : specifiedParameterTypes) {
            if (generator.getTypeParameters().contains(parameterType.getTypeString())) continue;
            checkTypeParameters(parameterType, generator);
        }
    }

    /**
     * Returns the {@link Generator}s corresponding to the given name.
     * Returns an empty list if the generator has not been imported.
     */
    public List<Generator> resolveGenerator(String generatorName) {
        return knownGenerators.getOrDefault(generatorName, List.of());
    }

    /**
     * Returns the {@link Type} corresponding to the given name. Returns null if the type has not been imported.
     */
    public Type resolveType(String typeName) {
        String typeNameWithoutGenerics = new ParsedType(typeName).stripGenerics();
        return knownTypes.get(typeNameWithoutGenerics);
    }

    /**
     * Returns all imported generators.
     */
    public Map<String, List<Generator>> getKnownGenerators() {
        return knownGenerators;
    }

    /**
     * Returns all imported types.
     */
    public Map<String, Type> getKnownTypes() {
        return knownTypes;
    }

    /* helper functions for type name handling */

    /**
     * Splits the namespace into the different levels.
     */
    private static String[] splitNamespace(String namespace) {
        return namespace.split("\\.");
    }

    /**
     * Returns the fully qualified name (including namespace) of the generator.
     */
    private static String getFullyQualifiedName(Generator generator) {
        if (generator.getName().startsWith(generator.getNamespace() + ".")) {
            // the name is already fully qualified
            return generator.getName();
        }
        return generator.getNamespace() + "." + generator.getName();
    }

    /**
     * Returns the fully qualified name (including namespace) of the type.
     */
    private static String getFullyQualifiedName(Type type) {
        if (type.getName().startsWith(type.getNamespace() + ".")) {
            // the name is already fully qualified
            return type.getName();
        }
        return type.getNamespace() + "." + type.getName();
    }

    /* helper functions for better errors */

    /**
     * Returns the existing component name which is the closest to the given name.
     */
    public String findClosestComponent(String componentName) {
        String unqualifiedName = getUnqualifiedAtomicName(componentName);
        return getKnownGenerators().values().stream()
                .flatMap(Collection::stream)
                .map(x -> getUnqualifiedAtomicName(x.getName()))
                .min(Comparator.comparingInt(x -> Utils.editDistance(x, unqualifiedName)))
                .orElse("");
    }

    private static String getUnqualifiedAtomicName(String name) {
        String[] splitNamespace = splitNamespace(name);
        return splitNamespace[splitNamespace.length - 1];
    }

    /**
     * Returns the existing type name which is the closest to the given name.
     */
    public String findClosestType(String typeName) {
        String unqualifiedName = new ParsedType(typeName).getAtomicTypeName();
        return getKnownTypes().values().stream()
                .map(x -> new ParsedType(x.getName()).getAtomicTypeName())
                .min(Comparator.comparingInt(x -> Utils.editDistance(x, unqualifiedName)))
                .orElse("");
    }

    /**
     * Returns the existing namespace name which is the closest to the given name.
     */
    public String findClosestNamespace(String namespaceString) {
        return knownNamespaces.stream()
                .min(Comparator.comparingInt(x -> Utils.editDistance(x, namespaceString)))
                .orElse("");
    }
}
