package org.phylospec.components;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Represents a parsed type name, including its namespace, generic parameters, and property assignments.
 */
public class ParsedType {

    private static final Pattern INPUT_PROPERTY_REFERENCE =
            Pattern.compile("^\\$?([A-Za-z_][A-Za-z0-9_]*)\\.([A-Za-z_][A-Za-z0-9_]*)$");

    private final String typeName;
    private final String namespace;
    private final String atomicTypeName;
    private final List<ParsedType> typeParameters;
    private final List<ParsedTypeProperty> typeProperties;

    /**
     * Parses a potentially qualified type name with optional generic parameters and properties.
     *
     * @param typeName the type name to parse
     */
    public ParsedType(String typeName) {
        this.typeName = typeName;
        this.namespace = ParsedType.parseNameSpace(typeName);
        this.atomicTypeName = ParsedType.parseAtomicTypeName(typeName);
        this.typeParameters = ParsedType.parseTypeParameters(typeName);
        this.typeProperties = ParsedType.parseTypeProperties(typeName);
    }

    /**
     * Returns the type string used to parse the type.
     */
    public String getTypeString() {
        return this.typeName;
    }

    /**
     * Returns the unqualified base type name without generic parameters or properties.
     *
     * @return the atomic type name
     */
    public String getAtomicTypeName() {
        return this.atomicTypeName;
    }

    /**
     * Returns the namespace that qualifies the base type name.
     *
     * @return the namespace, or an empty string when the type is unqualified
     */
    public String getNamespace() {
        return this.namespace;
    }

    /**
     * Returns the individual segments of the namespace.
     *
     * @return the namespace segments
     */
    public String[] getSplitNamespace() {
        return this.namespace.split("\\.");
    }

    /**
     * Returns the type name (including potential namespace) with no generic type parameters or properties.
     *
     * @return the type name without generic content
     */
    public String stripGenerics() {
        return stripGenerics(this.typeName);
    }

    /**
     * Returns the type name with all namespaces removed, but with potential generic type parameters and properties.
     * Namespaces are stripped recursively from nested type parameters, while type properties are kept as they are.
     *
     * @return the unqualified type name including generic content
     */
    public String stripNamespace() {
        if (this.typeParameters.isEmpty() && this.typeProperties.isEmpty()) {
            return this.atomicTypeName;
        }

        String parameters =
                this.typeParameters.stream().map(ParsedType::stripNamespace).collect(Collectors.joining(", "));
        String properties = this.typeProperties.isEmpty()
                ? ""
                : "; " + this.typeProperties.stream().map(Object::toString).collect(Collectors.joining(", "));

        return this.atomicTypeName + "<" + parameters + properties + ">";
    }

    /**
     * Returns the parsed top-level generic type parameters.
     *
     * @return the type parameters
     */
    public List<ParsedType> getTypeParameters() {
        return typeParameters;
    }

    /**
     * Returns the parsed top-level type properties.
     *
     * @return the type properties
     */
    public List<ParsedTypeProperty> getTypeProperties() {
        return typeProperties;
    }

    /**
     * Returns whether this is a generic type (i.e. has type parameters).
     */
    public boolean isGeneric() {
        return !typeParameters.isEmpty();
    }

    private static String parseNameSpace(String typeName) {
        String typeNameWithoutGenerics = stripGenerics(typeName);
        int namespaceEnd = typeNameWithoutGenerics.lastIndexOf('.');
        return namespaceEnd == -1 ? "" : typeNameWithoutGenerics.substring(0, namespaceEnd);
    }

    private static String parseAtomicTypeName(String typeName) {
        String typeNameWithoutGenerics = stripGenerics(typeName);
        int namespaceEnd = typeNameWithoutGenerics.lastIndexOf('.');
        return typeNameWithoutGenerics.substring(namespaceEnd + 1);
    }

    private static String stripGenerics(String typeName) {
        int genericStart = typeName.indexOf('<');
        return genericStart == -1 ? typeName : typeName.substring(0, genericStart);
    }

    private static List<ParsedType> parseTypeParameters(String typeName) {
        String genericContent = parseGenericContent(typeName);
        if (genericContent.isEmpty()) {
            return List.of();
        }

        int propertiesStart = findTopLevelDelimiter(genericContent, ';');
        String parameters = propertiesStart == -1 ? genericContent : genericContent.substring(0, propertiesStart);

        return splitTopLevel(parameters, ',').stream()
                .map(String::trim)
                .filter(parameter -> !parameter.isEmpty())
                .map(ParsedType::new)
                .toList();
    }

    private static List<ParsedTypeProperty> parseTypeProperties(String typeName) {
        String genericContent = parseGenericContent(typeName);
        int propertiesStart = findTopLevelDelimiter(genericContent, ';');
        if (propertiesStart == -1) {
            return List.of();
        }

        return splitTopLevel(genericContent.substring(propertiesStart + 1), ',').stream()
                .map(String::trim)
                .filter(property -> !property.isEmpty())
                .map(ParsedType::parseTypeProperty)
                .toList();
    }

    private static ParsedTypeProperty parseTypeProperty(String property) {
        int assignment = property.indexOf('=');
        if (assignment == -1) {
            throw new IllegalArgumentException("Type property must have the form 'property=value': " + property);
        }

        String propertyName = property.substring(0, assignment).trim();
        String value = property.substring(assignment + 1).trim();
        if (propertyName.isEmpty() || value.isEmpty()) {
            throw new IllegalArgumentException("Type property must have a non-empty name and value: " + property);
        }

        Matcher inputPropertyReference = INPUT_PROPERTY_REFERENCE.matcher(value);
        if (inputPropertyReference.matches()) {
            return new ParsedTypeProperty.Assignment(
                    propertyName, inputPropertyReference.group(1), inputPropertyReference.group(2));
        }

        return new ParsedTypeProperty.Constant(propertyName, value);
    }

    private static String parseGenericContent(String typeName) {
        int genericStart = typeName.indexOf('<');
        if (genericStart == -1) {
            return "";
        }

        int depth = 0;
        for (int index = genericStart; index < typeName.length(); index++) {
            char character = typeName.charAt(index);
            if (character == '<') {
                depth++;
            } else if (character == '>' && --depth == 0) {
                return typeName.substring(genericStart + 1, index);
            }
        }

        return typeName.substring(genericStart + 1);
    }

    private static int findTopLevelDelimiter(String value, char delimiter) {
        int depth = 0;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '<') {
                depth++;
            } else if (character == '>') {
                depth--;
            } else if (character == delimiter && depth == 0) {
                return index;
            }
        }
        return -1;
    }

    private static List<String> splitTopLevel(String value, char delimiter) {
        List<String> parts = new ArrayList<>();
        int partStart = 0;
        int depth = 0;

        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '<') {
                depth++;
            } else if (character == '>') {
                depth--;
            } else if (character == delimiter && depth == 0) {
                parts.add(value.substring(partStart, index));
                partStart = index + 1;
            }
        }

        parts.add(value.substring(partStart));
        return parts;
    }

    /**
     * Compares this parsed type with another parsed type by its original type name.
     *
     * @param object the object to compare with
     * @return whether both objects represent the same original type name
     */
    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof ParsedType parsedType)) {
            return false;
        }
        return this.typeName.equals(parsedType.typeName);
    }

    /**
     * Returns a hash code derived from the original type name.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(this.typeName);
    }
}
