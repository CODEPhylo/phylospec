
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


/**
 * UI hints for rendering this argument
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "widget",
    "order",
    "group"
})
@Generated("jsonschema2pojo")
public class UiHints {

    /**
     * Suggested widget type (e.g., 'slider', 'checkbox', 'file-picker')
     * 
     */
    @JsonProperty("widget")
    @JsonPropertyDescription("Suggested widget type (e.g., 'slider', 'checkbox', 'file-picker')")
    private String widget;
    /**
     * Display order relative to other arguments
     * 
     */
    @JsonProperty("order")
    @JsonPropertyDescription("Display order relative to other arguments")
    private Integer order;
    /**
     * Group name for organizing related arguments
     * 
     */
    @JsonProperty("group")
    @JsonPropertyDescription("Group name for organizing related arguments")
    private String group;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new LinkedHashMap<String, Object>();

    /**
     * Suggested widget type (e.g., 'slider', 'checkbox', 'file-picker')
     * 
     */
    @JsonProperty("widget")
    public String getWidget() {
        return widget;
    }

    /**
     * Suggested widget type (e.g., 'slider', 'checkbox', 'file-picker')
     * 
     */
    @JsonProperty("widget")
    public void setWidget(String widget) {
        this.widget = widget;
    }

    /**
     * Display order relative to other arguments
     * 
     */
    @JsonProperty("order")
    public Integer getOrder() {
        return order;
    }

    /**
     * Display order relative to other arguments
     * 
     */
    @JsonProperty("order")
    public void setOrder(Integer order) {
        this.order = order;
    }

    /**
     * Group name for organizing related arguments
     * 
     */
    @JsonProperty("group")
    public String getGroup() {
        return group;
    }

    /**
     * Group name for organizing related arguments
     * 
     */
    @JsonProperty("group")
    public void setGroup(String group) {
        this.group = group;
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
        sb.append(UiHints.class.getName()).append('@').append(Integer.toHexString(System.identityHashCode(this))).append('[');
        sb.append("widget");
        sb.append('=');
        sb.append(((this.widget == null)?"<null>":this.widget));
        sb.append(',');
        sb.append("order");
        sb.append('=');
        sb.append(((this.order == null)?"<null>":this.order));
        sb.append(',');
        sb.append("group");
        sb.append('=');
        sb.append(((this.group == null)?"<null>":this.group));
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
        result = ((result* 31)+((this.widget == null)? 0 :this.widget.hashCode()));
        result = ((result* 31)+((this.additionalProperties == null)? 0 :this.additionalProperties.hashCode()));
        result = ((result* 31)+((this.order == null)? 0 :this.order.hashCode()));
        result = ((result* 31)+((this.group == null)? 0 :this.group.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof UiHints) == false) {
            return false;
        }
        UiHints rhs = ((UiHints) other);
        return (((((this.widget == rhs.widget)||((this.widget!= null)&&this.widget.equals(rhs.widget)))&&((this.additionalProperties == rhs.additionalProperties)||((this.additionalProperties!= null)&&this.additionalProperties.equals(rhs.additionalProperties))))&&((this.order == rhs.order)||((this.order!= null)&&this.order.equals(rhs.order))))&&((this.group == rhs.group)||((this.group!= null)&&this.group.equals(rhs.group))));
    }

}
