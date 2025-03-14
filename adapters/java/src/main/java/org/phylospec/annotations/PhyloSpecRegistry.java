package org.phylospec.annotations;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Registry for PhyloSpec-annotated classes.
 * 
 * This class provides a central registry for discovering and accessing
 * classes annotated with PhyloSpec annotations across different
 * phylogenetic modeling frameworks.
 */
public class PhyloSpecRegistry {
    private static PhyloSpecRegistry instance;
    
    // Maps component names to implementing classes
    private final Map<String, List<Class<?>>> componentMap = new ConcurrentHashMap<>();
    
    // Maps by category
    private final Map<PhyloSpec.Category, List<Class<?>>> categoryMap = new ConcurrentHashMap<>();
    
    // Maps by role
    private final Map<PhyloSpec.Role, List<Class<?>>> roleMap = new ConcurrentHashMap<>();
    
    // Maps classes to their parameter mappings
    private final Map<Class<?>, Map<String, ParameterMapping>> parameterMappings = new ConcurrentHashMap<>();
    
    /**
     * Get the singleton instance of the registry.
     */
    public static synchronized PhyloSpecRegistry getInstance() {
        if (instance == null) {
            instance = new PhyloSpecRegistry();
        }
        return instance;
    }
    
    private PhyloSpecRegistry() {
        // Initialize maps
        for (PhyloSpec.Category category : PhyloSpec.Category.values()) {
            categoryMap.put(category, new ArrayList<>());
        }
        
        for (PhyloSpec.Role role : PhyloSpec.Role.values()) {
            roleMap.put(role, new ArrayList<>());
        }
    }
    
    /**
     * Initialize the registry by scanning for PhyloSpec annotations in the specified packages.
     * 
     * @param packages Packages to scan for annotated classes
     * @throws ReflectiveOperationException If there's an error accessing classes
     */
    public void initialize(List<String> packages) throws ReflectiveOperationException {
        ClassFinder finder = new ClassFinder();
        
        for (String packageName : packages) {
            Set<Class<?>> classes = finder.findAnnotatedClasses(packageName, PhyloSpec.class);
            for (Class<?> clazz : classes) {
                registerClass(clazz);
            }
        }
    }
    
    /**
     * Register a class with PhyloSpec annotations.
     * 
     * @param clazz The class to register
     */
    private void registerClass(Class<?> clazz) {
        PhyloSpec spec = clazz.getAnnotation(PhyloSpec.class);
        if (spec == null) return;
        
        String componentName = spec.value();
        PhyloSpec.Category category = spec.category();
        PhyloSpec.Role role = spec.role();
        
        // Add to component map
        componentMap.computeIfAbsent(componentName, k -> new ArrayList<>()).add(clazz);
        
        // Add to category and role maps
        categoryMap.get(category).add(clazz);
        roleMap.get(role).add(clazz);
        
        // Process parameter mappings
        registerParameterMappings(clazz);
    }
    
    /**
     * Register parameter mappings for a class.
     * 
     * @param clazz The class to process
     */
    private void registerParameterMappings(Class<?> clazz) {
        Map<String, ParameterMapping> mappings = new HashMap<>();
        
        // Process field annotations
        for (Field field : clazz.getDeclaredFields()) {
            PhyloParam param = field.getAnnotation(PhyloParam.class);
            if (param != null) {
                String paramName = param.value();
                mappings.put(paramName, new ParameterMapping(
                    paramName, field.getName(), ParameterMapping.Type.FIELD,
                    param.required(), param.defaultValue(), param.type()
                ));
            }
        }
        
        // Process method annotations (setters)
        for (Method method : clazz.getDeclaredMethods()) {
            PhyloParam param = method.getAnnotation(PhyloParam.class);
            if (param != null) {
                String paramName = param.value();
                mappings.put(paramName, new ParameterMapping(
                    paramName, method.getName(), ParameterMapping.Type.METHOD,
                    param.required(), param.defaultValue(), param.type()
                ));
            }
        }
        
        parameterMappings.put(clazz, mappings);
    }
    
    /**
     * Get a class that implements the specified component.
     * 
     * @param componentName Name of the component (e.g., "HKY", "Yule")
     * @param category Category of the component
     * @return The implementing class, or null if not found
     */
    public Class<?> getImplementation(String componentName, PhyloSpec.Category category) {
        List<Class<?>> implementations = componentMap.get(componentName);
        if (implementations == null || implementations.isEmpty()) {
            return null;
        }
        
        // Filter by category
        List<Class<?>> filtered = implementations.stream()
            .filter(cls -> cls.getAnnotation(PhyloSpec.class).category() == category)
            .collect(Collectors.toList());
        
        return filtered.isEmpty() ? implementations.get(0) : filtered.get(0);
    }
    
    /**
     * Get all implementations for a specific category.
     * 
     * @param category The category to filter by
     * @return List of implementing classes
     */
    public List<Class<?>> getImplementationsByCategory(PhyloSpec.Category category) {
        return categoryMap.getOrDefault(category, Collections.emptyList());
    }
    
    /**
     * Get all implementations for a specific role.
     * 
     * @param role The role to filter by
     * @return List of implementing classes
     */
    public List<Class<?>> getImplementationsByRole(PhyloSpec.Role role) {
        return roleMap.getOrDefault(role, Collections.emptyList());
    }
    
    /**
     * Get parameter mappings for a class.
     * 
     * @param clazz The class to get mappings for
     * @return Map of parameter names to parameter mappings
     */
    public Map<String, ParameterMapping> getParameterMappings(Class<?> clazz) {
        return parameterMappings.getOrDefault(clazz, Collections.emptyMap());
    }
    
    /**
     * Get a specific parameter mapping.
     * 
     * @param clazz The class to get mapping for
     * @param paramName The parameter name
     * @return The parameter mapping, or null if not found
     */
    public ParameterMapping getParameterMapping(Class<?> clazz, String paramName) {
        Map<String, ParameterMapping> mappings = parameterMappings.get(clazz);
        return mappings != null ? mappings.get(paramName) : null;
    }
    
    /**
     * Class representing a mapping between a PhyloSpec parameter and an implementation.
     */
    public static class ParameterMapping {
        private final String phyloSpecName;
        private final String implementationName;
        private final Type type;
        private final boolean required;
        private final String defaultValue;
        private final PhyloParam.ParamType paramType;
        
        public ParameterMapping(String phyloSpecName, String implementationName, Type type,
                                boolean required, String defaultValue, PhyloParam.ParamType paramType) {
            this.phyloSpecName = phyloSpecName;
            this.implementationName = implementationName;
            this.type = type;
            this.required = required;
            this.defaultValue = defaultValue;
            this.paramType = paramType;
        }
        
        /**
         * Get the PhyloSpec parameter name.
         */
        public String getPhyloSpecName() {
            return phyloSpecName;
        }
        
        /**
         * Get the implementation field or method name.
         */
        public String getImplementationName() {
            return implementationName;
        }
        
        /**
         * Get the mapping type (field or method).
         */
        public Type getType() {
            return type;
        }
        
        /**
         * Check if this parameter is required.
         */
        public boolean isRequired() {
            return required;
        }
        
        /**
         * Get the default value.
         */
        public String getDefaultValue() {
            return defaultValue;
        }
        
        /**
         * Get the parameter type.
         */
        public PhyloParam.ParamType getParamType() {
            return paramType;
        }
        
        /**
         * Types of parameter mappings.
         */
        public enum Type {
            FIELD,  // Maps to a field in the class
            METHOD  // Maps to a method (setter) in the class
        }
    }
    
    /**
     * Helper class to find annotated classes in a package.
     * 
     * In a real implementation, this would use a library like Reflections
     * to efficiently scan the classpath for annotations.
     */
    private static class ClassFinder {
        public Set<Class<?>> findAnnotatedClasses(String packageName, Class<? extends Annotation> annotation) 
                throws ReflectiveOperationException {
            // This is a simplified placeholder implementation
            // A real implementation would use a classpath scanning library
            Set<Class<?>> result = new HashSet<>();
            
            // In a real implementation, scan the package for classes with the annotation
            // For now, return an empty set
            
            return result;
        }
    }
}