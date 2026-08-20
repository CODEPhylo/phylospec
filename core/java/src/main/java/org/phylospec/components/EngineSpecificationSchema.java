package org.phylospec.components;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.processing.Generated;

/**
 * Engine Specification Schema v1
 * <p>
 * Schema for validating engine specifications describing an inference engine's generators and installation metadata
 *
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "name",
    "engineVersion",
    "dependsOn",
    "generators",
    "installationInstructions",
    "installationWebsite"
})
@Generated("jsonschema2pojo")
public class EngineSpecificationSchema {

    /**
     * Name of the engine
     * (Required)
     *
     */
    @JsonProperty("name")
    @JsonPropertyDescription("Name of the engine")
    private String name;
    /**
     * Version of the engine this specification targets
     * (Required)
     *
     */
    @JsonProperty("engineVersion")
    @JsonPropertyDescription("Version of the engine this specification targets")
    private String engineVersion;
    /**
     * Names of other engines this engine depends on
     * (Required)
     *
     */
    @JsonProperty("dependsOn")
    @JsonPropertyDescription("Names of other engines this engine depends on")
    private List<String> dependsOn = new ArrayList<String>();
    /**
     *
     * (Required)
     *
     */
    @JsonProperty("generators")
    private List<Generator__1> generators = new ArrayList<Generator__1>();
    /**
     * Human-readable instructions for installing the engine
     * (Required)
     *
     */
    @JsonProperty("installationInstructions")
    @JsonPropertyDescription("Human-readable instructions for installing the engine")
    private String installationInstructions;
    /**
     * URL of the website with installation information for the engine
     * (Required)
     *
     */
    @JsonProperty("installationWebsite")
    @JsonPropertyDescription("URL of the website with installation information for the engine")
    private String installationWebsite;

    @JsonIgnore
    private Map<String, Object> additionalProperties = new LinkedHashMap<String, Object>();

    /**
     * Name of the engine
     * (Required)
     *
     */
    @JsonProperty("name")
    public String getName() {
        return name;
    }

    /**
     * Name of the engine
     * (Required)
     *
     */
    @JsonProperty("name")
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Version of the engine this specification targets
     * (Required)
     *
     */
    @JsonProperty("engineVersion")
    public String getEngineVersion() {
        return engineVersion;
    }

    /**
     * Version of the engine this specification targets
     * (Required)
     *
     */
    @JsonProperty("engineVersion")
    public void setEngineVersion(String engineVersion) {
        this.engineVersion = engineVersion;
    }

    /**
     * Names of other engines this engine depends on
     * (Required)
     *
     */
    @JsonProperty("dependsOn")
    public List<String> getDependsOn() {
        return dependsOn;
    }

    /**
     * Names of other engines this engine depends on
     * (Required)
     *
     */
    @JsonProperty("dependsOn")
    public void setDependsOn(List<String> dependsOn) {
        this.dependsOn = dependsOn;
    }

    /**
     *
     * (Required)
     *
     */
    @JsonProperty("generators")
    public List<Generator__1> getGenerators() {
        return generators;
    }

    /**
     *
     * (Required)
     *
     */
    @JsonProperty("generators")
    public void setGenerators(List<Generator__1> generators) {
        this.generators = generators;
    }

    /**
     * Human-readable instructions for installing the engine
     * (Required)
     *
     */
    @JsonProperty("installationInstructions")
    public String getInstallationInstructions() {
        return installationInstructions;
    }

    /**
     * Human-readable instructions for installing the engine
     * (Required)
     *
     */
    @JsonProperty("installationInstructions")
    public void setInstallationInstructions(String installationInstructions) {
        this.installationInstructions = installationInstructions;
    }

    /**
     * URL of the website with installation information for the engine
     * (Required)
     *
     */
    @JsonProperty("installationWebsite")
    public String getInstallationWebsite() {
        return installationWebsite;
    }

    /**
     * URL of the website with installation information for the engine
     * (Required)
     *
     */
    @JsonProperty("installationWebsite")
    public void setInstallationWebsite(String installationWebsite) {
        this.installationWebsite = installationWebsite;
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
        sb.append(EngineSpecificationSchema.class.getName())
                .append('@')
                .append(Integer.toHexString(System.identityHashCode(this)))
                .append('[');
        sb.append("name");
        sb.append('=');
        sb.append(((this.name == null) ? "<null>" : this.name));
        sb.append(',');
        sb.append("engineVersion");
        sb.append('=');
        sb.append(((this.engineVersion == null) ? "<null>" : this.engineVersion));
        sb.append(',');
        sb.append("dependsOn");
        sb.append('=');
        sb.append(((this.dependsOn == null) ? "<null>" : this.dependsOn));
        sb.append(',');
        sb.append("generators");
        sb.append('=');
        sb.append(((this.generators == null) ? "<null>" : this.generators));
        sb.append(',');
        sb.append("installationInstructions");
        sb.append('=');
        sb.append(((this.installationInstructions == null) ? "<null>" : this.installationInstructions));
        sb.append(',');
        sb.append("installationWebsite");
        sb.append('=');
        sb.append(((this.installationWebsite == null) ? "<null>" : this.installationWebsite));
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
        result = ((result * 31) + ((this.engineVersion == null) ? 0 : this.engineVersion.hashCode()));
        result = ((result * 31)
                + ((this.installationInstructions == null) ? 0 : this.installationInstructions.hashCode()));
        result = ((result * 31) + ((this.installationWebsite == null) ? 0 : this.installationWebsite.hashCode()));
        result = ((result * 31) + ((this.dependsOn == null) ? 0 : this.dependsOn.hashCode()));
        result = ((result * 31) + ((this.name == null) ? 0 : this.name.hashCode()));
        result = ((result * 31) + ((this.generators == null) ? 0 : this.generators.hashCode()));
        result = ((result * 31) + ((this.additionalProperties == null) ? 0 : this.additionalProperties.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof EngineSpecificationSchema) == false) {
            return false;
        }
        EngineSpecificationSchema rhs = ((EngineSpecificationSchema) other);
        return ((((((((this.engineVersion == rhs.engineVersion)
                                                                || ((this.engineVersion != null)
                                                                        && this.engineVersion.equals(
                                                                                rhs.engineVersion)))
                                                        && ((this.installationInstructions
                                                                        == rhs.installationInstructions)
                                                                || ((this.installationInstructions != null)
                                                                        && this.installationInstructions.equals(
                                                                                rhs.installationInstructions))))
                                                && ((this.installationWebsite == rhs.installationWebsite)
                                                        || ((this.installationWebsite != null)
                                                                && this.installationWebsite.equals(
                                                                        rhs.installationWebsite))))
                                        && ((this.dependsOn == rhs.dependsOn)
                                                || ((this.dependsOn != null) && this.dependsOn.equals(rhs.dependsOn))))
                                && ((this.name == rhs.name) || ((this.name != null) && this.name.equals(rhs.name))))
                        && ((this.generators == rhs.generators)
                                || ((this.generators != null) && this.generators.equals(rhs.generators))))
                && ((this.additionalProperties == rhs.additionalProperties)
                        || ((this.additionalProperties != null)
                                && this.additionalProperties.equals(rhs.additionalProperties))));
    }
}
