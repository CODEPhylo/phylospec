
package org.phylospec.components;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Component Library Schema v3
 * <p>
 * Schema for validating component libraries with enhanced metadata, UI hints, and examples
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "componentLibrary"
})
@Generated("jsonschema2pojo")
public class ComponentLibrarySchema {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("componentLibrary")
    private ComponentLibrary componentLibrary;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new LinkedHashMap<String, Object>();

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("componentLibrary")
    public ComponentLibrary getComponentLibrary() {
        return componentLibrary;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("componentLibrary")
    public void setComponentLibrary(ComponentLibrary componentLibrary) {
        this.componentLibrary = componentLibrary;
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
        sb.append(ComponentLibrarySchema.class.getName()).append('@').append(Integer.toHexString(System.identityHashCode(this))).append('[');
        sb.append("componentLibrary");
        sb.append('=');
        sb.append(((this.componentLibrary == null)?"<null>":this.componentLibrary));
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
        result = ((result* 31)+((this.componentLibrary == null)? 0 :this.componentLibrary.hashCode()));
        result = ((result* 31)+((this.additionalProperties == null)? 0 :this.additionalProperties.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof ComponentLibrarySchema) == false) {
            return false;
        }
        ComponentLibrarySchema rhs = ((ComponentLibrarySchema) other);
        return (((this.componentLibrary == rhs.componentLibrary)||((this.componentLibrary!= null)&&this.componentLibrary.equals(rhs.componentLibrary)))&&((this.additionalProperties == rhs.additionalProperties)||((this.additionalProperties!= null)&&this.additionalProperties.equals(rhs.additionalProperties))));
    }

}
