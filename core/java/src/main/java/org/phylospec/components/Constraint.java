package org.phylospec.components;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.processing.Generated;

/**
 * Compares a type property of an argument with either another argument's type property or a numeric constant.
 *
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"argument", "property", "operator", "otherArgument", "otherProperty", "constant"})
@Generated("jsonschema2pojo")
public class Constraint {

    /**
     * Name of the argument on the left-hand side
     * (Required)
     *
     */
    @JsonProperty("argument")
    @JsonPropertyDescription("Name of the argument on the left-hand side")
    private String argument;
    /**
     * Type property of the left-hand argument
     * (Required)
     *
     */
    @JsonProperty("property")
    @JsonPropertyDescription("Type property of the left-hand argument")
    private String property;
    /**
     * The comparison to perform
     * (Required)
     *
     */
    @JsonProperty("operator")
    @JsonPropertyDescription("The comparison to perform")
    private Constraint.Operator operator;
    /**
     * Name of the argument on the right-hand side
     *
     */
    @JsonProperty("otherArgument")
    @JsonPropertyDescription("Name of the argument on the right-hand side")
    private String otherArgument;
    /**
     * Type property of the right-hand argument
     *
     */
    @JsonProperty("otherProperty")
    @JsonPropertyDescription("Type property of the right-hand argument")
    private String otherProperty;
    /**
     * Numeric constant on the right-hand side
     *
     */
    @JsonProperty("constant")
    @JsonPropertyDescription("Numeric constant on the right-hand side")
    private Double constant;

    @JsonIgnore
    private Map<String, Object> additionalProperties = new LinkedHashMap<String, Object>();

    /**
     * Name of the argument on the left-hand side
     * (Required)
     *
     */
    @JsonProperty("argument")
    public String getArgument() {
        return argument;
    }

    /**
     * Name of the argument on the left-hand side
     * (Required)
     *
     */
    @JsonProperty("argument")
    public void setArgument(String argument) {
        this.argument = argument;
    }

    /**
     * Type property of the left-hand argument
     * (Required)
     *
     */
    @JsonProperty("property")
    public String getProperty() {
        return property;
    }

    /**
     * Type property of the left-hand argument
     * (Required)
     *
     */
    @JsonProperty("property")
    public void setProperty(String property) {
        this.property = property;
    }

    /**
     * The comparison to perform
     * (Required)
     *
     */
    @JsonProperty("operator")
    public Constraint.Operator getOperator() {
        return operator;
    }

    /**
     * The comparison to perform
     * (Required)
     *
     */
    @JsonProperty("operator")
    public void setOperator(Constraint.Operator operator) {
        this.operator = operator;
    }

    /**
     * Name of the argument on the right-hand side
     *
     */
    @JsonProperty("otherArgument")
    public String getOtherArgument() {
        return otherArgument;
    }

    /**
     * Name of the argument on the right-hand side
     *
     */
    @JsonProperty("otherArgument")
    public void setOtherArgument(String otherArgument) {
        this.otherArgument = otherArgument;
    }

    /**
     * Type property of the right-hand argument
     *
     */
    @JsonProperty("otherProperty")
    public String getOtherProperty() {
        return otherProperty;
    }

    /**
     * Type property of the right-hand argument
     *
     */
    @JsonProperty("otherProperty")
    public void setOtherProperty(String otherProperty) {
        this.otherProperty = otherProperty;
    }

    /**
     * Numeric constant on the right-hand side
     *
     */
    @JsonProperty("constant")
    public Double getConstant() {
        return constant;
    }

    /**
     * Numeric constant on the right-hand side
     *
     */
    @JsonProperty("constant")
    public void setConstant(Double constant) {
        this.constant = constant;
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
        sb.append(Constraint.class.getName())
                .append('@')
                .append(Integer.toHexString(System.identityHashCode(this)))
                .append('[');
        sb.append("argument");
        sb.append('=');
        sb.append(((this.argument == null) ? "<null>" : this.argument));
        sb.append(',');
        sb.append("property");
        sb.append('=');
        sb.append(((this.property == null) ? "<null>" : this.property));
        sb.append(',');
        sb.append("operator");
        sb.append('=');
        sb.append(((this.operator == null) ? "<null>" : this.operator));
        sb.append(',');
        sb.append("otherArgument");
        sb.append('=');
        sb.append(((this.otherArgument == null) ? "<null>" : this.otherArgument));
        sb.append(',');
        sb.append("otherProperty");
        sb.append('=');
        sb.append(((this.otherProperty == null) ? "<null>" : this.otherProperty));
        sb.append(',');
        sb.append("constant");
        sb.append('=');
        sb.append(((this.constant == null) ? "<null>" : this.constant));
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
        result = ((result * 31) + ((this.argument == null) ? 0 : this.argument.hashCode()));
        result = ((result * 31) + ((this.constant == null) ? 0 : this.constant.hashCode()));
        result = ((result * 31) + ((this.otherProperty == null) ? 0 : this.otherProperty.hashCode()));
        result = ((result * 31) + ((this.property == null) ? 0 : this.property.hashCode()));
        result = ((result * 31) + ((this.otherArgument == null) ? 0 : this.otherArgument.hashCode()));
        result = ((result * 31) + ((this.additionalProperties == null) ? 0 : this.additionalProperties.hashCode()));
        result = ((result * 31) + ((this.operator == null) ? 0 : this.operator.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Constraint) == false) {
            return false;
        }
        Constraint rhs = ((Constraint) other);
        return ((((((((this.argument == rhs.argument)
                                                                || ((this.argument != null)
                                                                        && this.argument.equals(rhs.argument)))
                                                        && ((this.constant == rhs.constant)
                                                                || ((this.constant != null)
                                                                        && this.constant.equals(rhs.constant))))
                                                && ((this.otherProperty == rhs.otherProperty)
                                                        || ((this.otherProperty != null)
                                                                && this.otherProperty.equals(rhs.otherProperty))))
                                        && ((this.property == rhs.property)
                                                || ((this.property != null) && this.property.equals(rhs.property))))
                                && ((this.otherArgument == rhs.otherArgument)
                                        || ((this.otherArgument != null)
                                                && this.otherArgument.equals(rhs.otherArgument))))
                        && ((this.additionalProperties == rhs.additionalProperties)
                                || ((this.additionalProperties != null)
                                        && this.additionalProperties.equals(rhs.additionalProperties))))
                && ((this.operator == rhs.operator)
                        || ((this.operator != null) && this.operator.equals(rhs.operator))));
    }

    /**
     * The comparison to perform
     *
     */
    @Generated("jsonschema2pojo")
    public enum Operator {
        EQUALS("equals"),
        NOT_EQUALS("notEquals"),
        LESS_THAN("lessThan"),
        LESS_THAN_OR_EQUAL("lessThanOrEqual"),
        GREATER_THAN("greaterThan"),
        GREATER_THAN_OR_EQUAL("greaterThanOrEqual");
        private final String value;
        private static final Map<String, Constraint.Operator> CONSTANTS = new HashMap<String, Constraint.Operator>();

        static {
            for (Constraint.Operator c : values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        Operator(String value) {
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
        public static Constraint.Operator fromValue(String value) {
            Constraint.Operator constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }
    }
}
