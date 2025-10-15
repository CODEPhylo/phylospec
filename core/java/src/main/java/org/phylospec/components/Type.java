
package org.phylospec.components;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "name",
    "description",
    "extends",
    "typeParameters",
    "properties",
    "namespace"
})
@Generated("jsonschema2pojo")
public class Type {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("name")
    private String name;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("description")
    private String description;
    @JsonProperty("extends")
    private String _extends;
    /**
     * Type parameters for generic types (e.g., ['T'] for Vector<T>)
     * 
     */
    @JsonProperty("typeParameters")
    @JsonPropertyDescription("Type parameters for generic types (e.g., ['T'] for Vector<T>)")
    private List<String> typeParameters = new ArrayList<String>();
    @JsonProperty("properties")
    private Properties properties;
    /**
     * Namespace for this type
     * 
     */
    @JsonProperty("namespace")
    @JsonPropertyDescription("Namespace for this type")
    private String namespace;
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

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("description")
    public String getDescription() {
        return description;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("description")
    public void setDescription(String description) {
        this.description = description;
    }

    @JsonProperty("extends")
    public String getExtends() {
        return _extends;
    }

    @JsonProperty("extends")
    public void setExtends(String _extends) {
        this._extends = _extends;
    }

    /**
     * Type parameters for generic types (e.g., ['T'] for Vector<T>)
     * 
     */
    @JsonProperty("typeParameters")
    public List<String> getTypeParameters() {
        return typeParameters;
    }

    /**
     * Type parameters for generic types (e.g., ['T'] for Vector<T>)
     * 
     */
    @JsonProperty("typeParameters")
    public void setTypeParameters(List<String> typeParameters) {
        this.typeParameters = typeParameters;
    }

    @JsonProperty("properties")
    public Properties getProperties() {
        return properties;
    }

    @JsonProperty("properties")
    public void setProperties(Properties properties) {
        this.properties = properties;
    }

    /**
     * Namespace for this type
     * 
     */
    @JsonProperty("namespace")
    public String getNamespace() {
        return namespace;
    }

    /**
     * Namespace for this type
     * 
     */
    @JsonProperty("namespace")
    public void setNamespace(String namespace) {
        this.namespace = namespace;
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
        sb.append(Type.class.getName()).append('@').append(Integer.toHexString(System.identityHashCode(this))).append('[');
        sb.append("name");
        sb.append('=');
        sb.append(((this.name == null)?"<null>":this.name));
        sb.append(',');
        sb.append("description");
        sb.append('=');
        sb.append(((this.description == null)?"<null>":this.description));
        sb.append(',');
        sb.append("_extends");
        sb.append('=');
        sb.append(((this._extends == null)?"<null>":this._extends));
        sb.append(',');
        sb.append("typeParameters");
        sb.append('=');
        sb.append(((this.typeParameters == null)?"<null>":this.typeParameters));
        sb.append(',');
        sb.append("properties");
        sb.append('=');
        sb.append(((this.properties == null)?"<null>":this.properties));
        sb.append(',');
        sb.append("namespace");
        sb.append('=');
        sb.append(((this.namespace == null)?"<null>":this.namespace));
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
        result = ((result* 31)+((this._extends == null)? 0 :this._extends.hashCode()));
        result = ((result* 31)+((this.name == null)? 0 :this.name.hashCode()));
        result = ((result* 31)+((this.namespace == null)? 0 :this.namespace.hashCode()));
        result = ((result* 31)+((this.description == null)? 0 :this.description.hashCode()));
        result = ((result* 31)+((this.additionalProperties == null)? 0 :this.additionalProperties.hashCode()));
        result = ((result* 31)+((this.typeParameters == null)? 0 :this.typeParameters.hashCode()));
        result = ((result* 31)+((this.properties == null)? 0 :this.properties.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Type) == false) {
            return false;
        }
        Type rhs = ((Type) other);
        return ((((((((this._extends == rhs._extends)||((this._extends!= null)&&this._extends.equals(rhs._extends)))&&((this.name == rhs.name)||((this.name!= null)&&this.name.equals(rhs.name))))&&((this.namespace == rhs.namespace)||((this.namespace!= null)&&this.namespace.equals(rhs.namespace))))&&((this.description == rhs.description)||((this.description!= null)&&this.description.equals(rhs.description))))&&((this.additionalProperties == rhs.additionalProperties)||((this.additionalProperties!= null)&&this.additionalProperties.equals(rhs.additionalProperties))))&&((this.typeParameters == rhs.typeParameters)||((this.typeParameters!= null)&&this.typeParameters.equals(rhs.typeParameters))))&&((this.properties == rhs.properties)||((this.properties!= null)&&this.properties.equals(rhs.properties))));
    }

}
