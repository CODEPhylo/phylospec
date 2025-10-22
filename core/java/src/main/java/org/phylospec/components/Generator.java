
package org.phylospec.components;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonValue;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "name",
    "description",
    "namespace",
    "generatorType",
    "generatedType",
    "typeParameters",
    "arguments",
    "ioHints",
    "examples"
})
@Generated("jsonschema2pojo")
public class Generator {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("name")
    private String name;
    @JsonProperty("description")
    private String description;
    @JsonProperty("namespace")
    private String namespace;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("generatorType")
    private Generator.GeneratorType generatorType;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("generatedType")
    private String generatedType;
    /**
     * Type parameters for generic generators (e.g., ['T'] for Mixture<T>)
     * 
     */
    @JsonProperty("typeParameters")
    @JsonPropertyDescription("Type parameters for generic generators (e.g., ['T'] for Mixture<T>)")
    private List<String> typeParameters = new ArrayList<String>();
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("arguments")
    private List<Argument> arguments = new ArrayList<Argument>();
    /**
     * File I/O metadata for data-loading or exporting functions
     * 
     */
    @JsonProperty("ioHints")
    @JsonPropertyDescription("File I/O metadata for data-loading or exporting functions")
    private IoHints ioHints;
    /**
     * Example usage snippets for this generator
     * 
     */
    @JsonProperty("examples")
    @JsonPropertyDescription("Example usage snippets for this generator")
    private List<String> examples = new ArrayList<String>();
    @JsonIgnore
    private Map<String, Object> additionalProperties = new LinkedHashMap<String, Object>();

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("name")
    public String getName() {
        return name;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("name")
    public void setName(String name) {
        this.name = name;
    }

    @JsonProperty("description")
    public String getDescription() {
        return description;
    }

    @JsonProperty("description")
    public void setDescription(String description) {
        this.description = description;
    }

    @JsonProperty("namespace")
    public String getNamespace() {
        return namespace;
    }

    @JsonProperty("namespace")
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("generatorType")
    public Generator.GeneratorType getGeneratorType() {
        return generatorType;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("generatorType")
    public void setGeneratorType(Generator.GeneratorType generatorType) {
        this.generatorType = generatorType;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("generatedType")
    public String getGeneratedType() {
        return generatedType;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("generatedType")
    public void setGeneratedType(String generatedType) {
        this.generatedType = generatedType;
    }

    /**
     * Type parameters for generic generators (e.g., ['T'] for Mixture<T>)
     * 
     */
    @JsonProperty("typeParameters")
    public List<String> getTypeParameters() {
        return typeParameters;
    }

    /**
     * Type parameters for generic generators (e.g., ['T'] for Mixture<T>)
     * 
     */
    @JsonProperty("typeParameters")
    public void setTypeParameters(List<String> typeParameters) {
        this.typeParameters = typeParameters;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("arguments")
    public List<Argument> getArguments() {
        return arguments;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("arguments")
    public void setArguments(List<Argument> arguments) {
        this.arguments = arguments;
    }

    /**
     * File I/O metadata for data-loading or exporting functions
     * 
     */
    @JsonProperty("ioHints")
    public IoHints getIoHints() {
        return ioHints;
    }

    /**
     * File I/O metadata for data-loading or exporting functions
     * 
     */
    @JsonProperty("ioHints")
    public void setIoHints(IoHints ioHints) {
        this.ioHints = ioHints;
    }

    /**
     * Example usage snippets for this generator
     * 
     */
    @JsonProperty("examples")
    public List<String> getExamples() {
        return examples;
    }

    /**
     * Example usage snippets for this generator
     * 
     */
    @JsonProperty("examples")
    public void setExamples(List<String> examples) {
        this.examples = examples;
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(Generator.class.getName()).append('@').append(Integer.toHexString(System.identityHashCode(this))).append('[');
        sb.append("name");
        sb.append('=');
        sb.append(((this.name == null)?"<null>":this.name));
        sb.append(',');
        sb.append("description");
        sb.append('=');
        sb.append(((this.description == null)?"<null>":this.description));
        sb.append(',');
        sb.append("namespace");
        sb.append('=');
        sb.append(((this.namespace == null)?"<null>":this.namespace));
        sb.append(',');
        sb.append("generatorType");
        sb.append('=');
        sb.append(((this.generatorType == null)?"<null>":this.generatorType));
        sb.append(',');
        sb.append("generatedType");
        sb.append('=');
        sb.append(((this.generatedType == null)?"<null>":this.generatedType));
        sb.append(',');
        sb.append("typeParameters");
        sb.append('=');
        sb.append(((this.typeParameters == null)?"<null>":this.typeParameters));
        sb.append(',');
        sb.append("arguments");
        sb.append('=');
        sb.append(((this.arguments == null)?"<null>":this.arguments));
        sb.append(',');
        sb.append("ioHints");
        sb.append('=');
        sb.append(((this.ioHints == null)?"<null>":this.ioHints));
        sb.append(',');
        sb.append("examples");
        sb.append('=');
        sb.append(((this.examples == null)?"<null>":this.examples));
        sb.append(',');
        sb.append("additionalProperties");
        sb.append('=');
        sb.append(((this.additionalProperties == null)?"<null>":this.additionalProperties));
        sb.append(',');
        if (sb.charAt((sb.length()- 1)) == ',') {
            sb.setCharAt((sb.length()- 1), ']');
        } else {
            sb.append(']');
        }
        return sb.toString();
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.generatedType == null)? 0 :this.generatedType.hashCode()));
        result = ((result* 31)+((this.examples == null)? 0 :this.examples.hashCode()));
        result = ((result* 31)+((this.ioHints == null)? 0 :this.ioHints.hashCode()));
        result = ((result* 31)+((this.name == null)? 0 :this.name.hashCode()));
        result = ((result* 31)+((this.namespace == null)? 0 :this.namespace.hashCode()));
        result = ((result* 31)+((this.description == null)? 0 :this.description.hashCode()));
        result = ((result* 31)+((this.arguments == null)? 0 :this.arguments.hashCode()));
        result = ((result* 31)+((this.additionalProperties == null)? 0 :this.additionalProperties.hashCode()));
        result = ((result* 31)+((this.typeParameters == null)? 0 :this.typeParameters.hashCode()));
        result = ((result* 31)+((this.generatorType == null)? 0 :this.generatorType.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Generator) == false) {
            return false;
        }
        Generator rhs = ((Generator) other);
        return (((((((((((this.generatedType == rhs.generatedType)||((this.generatedType!= null)&&this.generatedType.equals(rhs.generatedType)))&&((this.examples == rhs.examples)||((this.examples!= null)&&this.examples.equals(rhs.examples))))&&((this.ioHints == rhs.ioHints)||((this.ioHints!= null)&&this.ioHints.equals(rhs.ioHints))))&&((this.name == rhs.name)||((this.name!= null)&&this.name.equals(rhs.name))))&&((this.namespace == rhs.namespace)||((this.namespace!= null)&&this.namespace.equals(rhs.namespace))))&&((this.description == rhs.description)||((this.description!= null)&&this.description.equals(rhs.description))))&&((this.arguments == rhs.arguments)||((this.arguments!= null)&&this.arguments.equals(rhs.arguments))))&&((this.additionalProperties == rhs.additionalProperties)||((this.additionalProperties!= null)&&this.additionalProperties.equals(rhs.additionalProperties))))&&((this.typeParameters == rhs.typeParameters)||((this.typeParameters!= null)&&this.typeParameters.equals(rhs.typeParameters))))&&((this.generatorType == rhs.generatorType)||((this.generatorType!= null)&&this.generatorType.equals(rhs.generatorType))));
    }

    @Generated("jsonschema2pojo")
    public enum GeneratorType {

        DISTRIBUTION("distribution"),
        FUNCTION("function");
        private final String value;
        private final static Map<String, Generator.GeneratorType> CONSTANTS = new HashMap<String, Generator.GeneratorType>();

        static {
            for (Generator.GeneratorType c: values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        GeneratorType(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return this.value;
        }

        @JsonValue
        public String value() {
            return this.value;
        }

        @JsonCreator
        public static Generator.GeneratorType fromValue(String value) {
            Generator.GeneratorType constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

}
