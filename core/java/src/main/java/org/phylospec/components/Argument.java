
package org.phylospec.components;

import java.util.LinkedHashMap;
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
    "type",
    "required",
    "recommended",
    "default",
    "dimension",
    "description",
    "uiHints"
})
@Generated("jsonschema2pojo")
public class Argument {

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
    @JsonProperty("type")
    private String type;
    @JsonProperty("required")
    private Boolean required;
    /**
     * Indicates that this argument is recommended but not required
     * 
     */
    @JsonProperty("recommended")
    @JsonPropertyDescription("Indicates that this argument is recommended but not required")
    private Boolean recommended;
    /**
     * Default value for this argument if not specified
     * 
     */
    @JsonProperty("default")
    @JsonPropertyDescription("Default value for this argument if not specified")
    private Object _default;
    /**
     * Expected dimension of the argument. Can be a fixed integer or an expression that references other parts of the model
     * 
     */
    @JsonProperty("dimension")
    @JsonPropertyDescription("Expected dimension of the argument. Can be a fixed integer or an expression that references other parts of the model")
    private Object dimension;
    /**
     * Human-readable description of the argument
     * 
     */
    @JsonProperty("description")
    @JsonPropertyDescription("Human-readable description of the argument")
    private String description;
    /**
     * UI hints for rendering this argument
     * 
     */
    @JsonProperty("uiHints")
    @JsonPropertyDescription("UI hints for rendering this argument")
    private UiHints uiHints;
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
    @JsonProperty("type")
    public String getType() {
        return type;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("type")
    public void setType(String type) {
        this.type = type;
    }

    @JsonProperty("required")
    public Boolean getRequired() {
        return required;
    }

    @JsonProperty("required")
    public void setRequired(Boolean required) {
        this.required = required;
    }

    /**
     * Indicates that this argument is recommended but not required
     * 
     */
    @JsonProperty("recommended")
    public Boolean getRecommended() {
        return recommended;
    }

    /**
     * Indicates that this argument is recommended but not required
     * 
     */
    @JsonProperty("recommended")
    public void setRecommended(Boolean recommended) {
        this.recommended = recommended;
    }

    /**
     * Default value for this argument if not specified
     * 
     */
    @JsonProperty("default")
    public Object getDefault() {
        return _default;
    }

    /**
     * Default value for this argument if not specified
     * 
     */
    @JsonProperty("default")
    public void setDefault(Object _default) {
        this._default = _default;
    }

    /**
     * Expected dimension of the argument. Can be a fixed integer or an expression that references other parts of the model
     * 
     */
    @JsonProperty("dimension")
    public Object getDimension() {
        return dimension;
    }

    /**
     * Expected dimension of the argument. Can be a fixed integer or an expression that references other parts of the model
     * 
     */
    @JsonProperty("dimension")
    public void setDimension(Object dimension) {
        this.dimension = dimension;
    }

    /**
     * Human-readable description of the argument
     * 
     */
    @JsonProperty("description")
    public String getDescription() {
        return description;
    }

    /**
     * Human-readable description of the argument
     * 
     */
    @JsonProperty("description")
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * UI hints for rendering this argument
     * 
     */
    @JsonProperty("uiHints")
    public UiHints getUiHints() {
        return uiHints;
    }

    /**
     * UI hints for rendering this argument
     * 
     */
    @JsonProperty("uiHints")
    public void setUiHints(UiHints uiHints) {
        this.uiHints = uiHints;
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
        sb.append(Argument.class.getName()).append('@').append(Integer.toHexString(System.identityHashCode(this))).append('[');
        sb.append("name");
        sb.append('=');
        sb.append(((this.name == null)?"<null>":this.name));
        sb.append(',');
        sb.append("type");
        sb.append('=');
        sb.append(((this.type == null)?"<null>":this.type));
        sb.append(',');
        sb.append("required");
        sb.append('=');
        sb.append(((this.required == null)?"<null>":this.required));
        sb.append(',');
        sb.append("recommended");
        sb.append('=');
        sb.append(((this.recommended == null)?"<null>":this.recommended));
        sb.append(',');
        sb.append("_default");
        sb.append('=');
        sb.append(((this._default == null)?"<null>":this._default));
        sb.append(',');
        sb.append("dimension");
        sb.append('=');
        sb.append(((this.dimension == null)?"<null>":this.dimension));
        sb.append(',');
        sb.append("description");
        sb.append('=');
        sb.append(((this.description == null)?"<null>":this.description));
        sb.append(',');
        sb.append("uiHints");
        sb.append('=');
        sb.append(((this.uiHints == null)?"<null>":this.uiHints));
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
        result = ((result* 31)+((this._default == null)? 0 :this._default.hashCode()));
        result = ((result* 31)+((this.uiHints == null)? 0 :this.uiHints.hashCode()));
        result = ((result* 31)+((this.name == null)? 0 :this.name.hashCode()));
        result = ((result* 31)+((this.description == null)? 0 :this.description.hashCode()));
        result = ((result* 31)+((this.additionalProperties == null)? 0 :this.additionalProperties.hashCode()));
        result = ((result* 31)+((this.type == null)? 0 :this.type.hashCode()));
        result = ((result* 31)+((this.dimension == null)? 0 :this.dimension.hashCode()));
        result = ((result* 31)+((this.required == null)? 0 :this.required.hashCode()));
        result = ((result* 31)+((this.recommended == null)? 0 :this.recommended.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Argument) == false) {
            return false;
        }
        Argument rhs = ((Argument) other);
        return ((((((((((this._default == rhs._default)||((this._default!= null)&&this._default.equals(rhs._default)))&&((this.uiHints == rhs.uiHints)||((this.uiHints!= null)&&this.uiHints.equals(rhs.uiHints))))&&((this.name == rhs.name)||((this.name!= null)&&this.name.equals(rhs.name))))&&((this.description == rhs.description)||((this.description!= null)&&this.description.equals(rhs.description))))&&((this.additionalProperties == rhs.additionalProperties)||((this.additionalProperties!= null)&&this.additionalProperties.equals(rhs.additionalProperties))))&&((this.type == rhs.type)||((this.type!= null)&&this.type.equals(rhs.type))))&&((this.dimension == rhs.dimension)||((this.dimension!= null)&&this.dimension.equals(rhs.dimension))))&&((this.required == rhs.required)||((this.required!= null)&&this.required.equals(rhs.required))))&&((this.recommended == rhs.recommended)||((this.recommended!= null)&&this.recommended.equals(rhs.recommended))));
    }

}
