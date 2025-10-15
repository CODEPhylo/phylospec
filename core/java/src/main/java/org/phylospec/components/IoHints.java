
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


/**
 * File I/O metadata for data-loading or exporting functions
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "role",
    "extensions",
    "fileArgument"
})
@Generated("jsonschema2pojo")
public class IoHints {

    @JsonProperty("role")
    private IoHints.Role role;
    /**
     * Supported file extensions (e.g. [".nex", ".fasta"])
     * 
     */
    @JsonProperty("extensions")
    @JsonPropertyDescription("Supported file extensions (e.g. [\".nex\", \".fasta\"])")
    private List<String> extensions = new ArrayList<String>();
    /**
     * Name of the argument that accepts the file path
     * 
     */
    @JsonProperty("fileArgument")
    @JsonPropertyDescription("Name of the argument that accepts the file path")
    private String fileArgument;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new LinkedHashMap<String, Object>();

    @JsonProperty("role")
    public IoHints.Role getRole() {
        return role;
    }

    @JsonProperty("role")
    public void setRole(IoHints.Role role) {
        this.role = role;
    }

    /**
     * Supported file extensions (e.g. [".nex", ".fasta"])
     * 
     */
    @JsonProperty("extensions")
    public List<String> getExtensions() {
        return extensions;
    }

    /**
     * Supported file extensions (e.g. [".nex", ".fasta"])
     * 
     */
    @JsonProperty("extensions")
    public void setExtensions(List<String> extensions) {
        this.extensions = extensions;
    }

    /**
     * Name of the argument that accepts the file path
     * 
     */
    @JsonProperty("fileArgument")
    public String getFileArgument() {
        return fileArgument;
    }

    /**
     * Name of the argument that accepts the file path
     * 
     */
    @JsonProperty("fileArgument")
    public void setFileArgument(String fileArgument) {
        this.fileArgument = fileArgument;
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
        sb.append(IoHints.class.getName()).append('@').append(Integer.toHexString(System.identityHashCode(this))).append('[');
        sb.append("role");
        sb.append('=');
        sb.append(((this.role == null)?"<null>":this.role));
        sb.append(',');
        sb.append("extensions");
        sb.append('=');
        sb.append(((this.extensions == null)?"<null>":this.extensions));
        sb.append(',');
        sb.append("fileArgument");
        sb.append('=');
        sb.append(((this.fileArgument == null)?"<null>":this.fileArgument));
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
        result = ((result* 31)+((this.extensions == null)? 0 :this.extensions.hashCode()));
        result = ((result* 31)+((this.role == null)? 0 :this.role.hashCode()));
        result = ((result* 31)+((this.fileArgument == null)? 0 :this.fileArgument.hashCode()));
        result = ((result* 31)+((this.additionalProperties == null)? 0 :this.additionalProperties.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof IoHints) == false) {
            return false;
        }
        IoHints rhs = ((IoHints) other);
        return (((((this.extensions == rhs.extensions)||((this.extensions!= null)&&this.extensions.equals(rhs.extensions)))&&((this.role == rhs.role)||((this.role!= null)&&this.role.equals(rhs.role))))&&((this.fileArgument == rhs.fileArgument)||((this.fileArgument!= null)&&this.fileArgument.equals(rhs.fileArgument))))&&((this.additionalProperties == rhs.additionalProperties)||((this.additionalProperties!= null)&&this.additionalProperties.equals(rhs.additionalProperties))));
    }

    @Generated("jsonschema2pojo")
    public enum Role {

        DATA_INPUT("dataInput"),
        DATA_OUTPUT("dataOutput");
        private final String value;
        private final static Map<String, IoHints.Role> CONSTANTS = new HashMap<String, IoHints.Role>();

        static {
            for (IoHints.Role c: values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        Role(String value) {
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
        public static IoHints.Role fromValue(String value) {
            IoHints.Role constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

}
