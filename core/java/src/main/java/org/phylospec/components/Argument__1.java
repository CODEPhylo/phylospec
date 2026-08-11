package org.phylospec.components;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.processing.Generated;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"name", "type", "required", "canBeStochastic"})
@Generated("jsonschema2pojo")
public class Argument__1 {

    /**
     *
     * (Required)
     *
     */
    @JsonProperty("name")
    private String name;

    @JsonProperty("type")
    private String type;

    /**
     * Whether this argument is required
     * (Required)
     *
     */
    @JsonProperty("required")
    @JsonPropertyDescription("Whether this argument is required")
    private Boolean required;

    /**
     * Whether this argument can be a stochastic node
     * (Required)
     *
     */
    @JsonProperty("canBeStochastic")
    @JsonPropertyDescription("Whether this argument can be a stochastic node")
    private Boolean canBeStochastic;

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

    @JsonProperty("type")
    public String getType() {
        return type;
    }

    @JsonProperty("type")
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Whether this argument is required
     * (Required)
     *
     */
    @JsonProperty("required")
    public Boolean getRequired() {
        return required;
    }

    /**
     * Whether this argument is required
     * (Required)
     *
     */
    @JsonProperty("required")
    public void setRequired(Boolean required) {
        this.required = required;
    }

    /**
     * Whether this argument can be a stochastic node
     * (Required)
     *
     */
    @JsonProperty("canBeStochastic")
    public Boolean getCanBeStochastic() {
        return canBeStochastic;
    }

    /**
     * Whether this argument can be a stochastic node
     * (Required)
     *
     */
    @JsonProperty("canBeStochastic")
    public void setCanBeStochastic(Boolean canBeStochastic) {
        this.canBeStochastic = canBeStochastic;
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
        sb.append(Argument__1.class.getName())
                .append('@')
                .append(Integer.toHexString(System.identityHashCode(this)))
                .append('[');
        sb.append("name");
        sb.append('=');
        sb.append(((this.name == null) ? "<null>" : this.name));
        sb.append(',');
        sb.append("type");
        sb.append('=');
        sb.append(((this.type == null) ? "<null>" : this.type));
        sb.append(',');
        sb.append("required");
        sb.append('=');
        sb.append(((this.required == null) ? "<null>" : this.required));
        sb.append(',');
        sb.append("canBeStochastic");
        sb.append('=');
        sb.append(((this.canBeStochastic == null) ? "<null>" : this.canBeStochastic));
        sb.append(',');
        sb.append("additionalProperties");
        sb.append('=');
        sb.append(((this.additionalProperties == null) ? "<null>" : this.additionalProperties));
        sb.append(',');
        if (sb.charAt((sb.length() - 1)) == ',') {
            sb.setCharAt((sb.length() - 1), ']');
        } else {
            sb.append(']');
        }
        return sb.toString();
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result * 31) + ((this.name == null) ? 0 : this.name.hashCode()));
        result = ((result * 31) + ((this.additionalProperties == null) ? 0 : this.additionalProperties.hashCode()));
        result = ((result * 31) + ((this.type == null) ? 0 : this.type.hashCode()));
        result = ((result * 31) + ((this.canBeStochastic == null) ? 0 : this.canBeStochastic.hashCode()));
        result = ((result * 31) + ((this.required == null) ? 0 : this.required.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Argument__1) == false) {
            return false;
        }
        Argument__1 rhs = ((Argument__1) other);
        return ((((((this.name == rhs.name) || ((this.name != null) && this.name.equals(rhs.name)))
                                        && ((this.additionalProperties == rhs.additionalProperties)
                                                || ((this.additionalProperties != null)
                                                        && this.additionalProperties.equals(rhs.additionalProperties))))
                                && ((this.type == rhs.type) || ((this.type != null) && this.type.equals(rhs.type))))
                        && ((this.canBeStochastic == rhs.canBeStochastic)
                                || ((this.canBeStochastic != null)
                                        && this.canBeStochastic.equals(rhs.canBeStochastic))))
                && ((this.required == rhs.required)
                        || ((this.required != null) && this.required.equals(rhs.required))));
    }
}
