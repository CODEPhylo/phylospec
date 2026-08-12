package org.phylospec.typeresolver.properties;

import java.util.*;

/**
 * Stores the resolved type properties attached to a single resolved type. Unknown property values
 * are represented by the absence of the property.
 */
public class TypeProperties {

    private final Map<String, Object> typeProperties;

    public TypeProperties() {
        this.typeProperties = new HashMap<>();
    }

    public void attach(String propertyName, Object value) {
        if (value == null) return;
        this.typeProperties.put(propertyName, value);
    }

    public void attach(TypeProperties typeProperties) {
        this.typeProperties.putAll(typeProperties.getProperties());
    }

    public void replace(Map<String, Object> typeProperties) {
        this.typeProperties.clear();
        this.typeProperties.putAll(typeProperties);
    }

    public void replace(TypeProperties typeProperties) {
        this.typeProperties.clear();
        this.typeProperties.putAll(typeProperties.getProperties());
    }

    public Object get(String propertyName) {
        return this.typeProperties.get(propertyName);
    }

    public boolean has(String propertyName) {
        return this.typeProperties.get(propertyName) != null;
    }

    public Set<String> getPropertyNames() {
        return Set.copyOf(this.typeProperties.keySet());
    }

    /**
     * Creates a shallow copy of this {@link TypeProperties}, with an independent property map.
     * The property values themselves are not copied.
     */
    public TypeProperties copy() {
        TypeProperties copy = new TypeProperties();
        copy.attach(this);
        return copy;
    }

    private Map<String, Object> getProperties() {
        return this.typeProperties;
    }
}
