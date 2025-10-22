
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
    "version",
    "engine",
    "engineVersion",
    "description",
    "authors",
    "license",
    "types",
    "generators"
})
@Generated("jsonschema2pojo")
public class ComponentLibrary {

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
    @JsonProperty("version")
    private String version;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("engine")
    private String engine;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("engineVersion")
    private String engineVersion;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("description")
    private String description;
    /**
     * List of authors or maintainers of the schema
     * 
     */
    @JsonProperty("authors")
    @JsonPropertyDescription("List of authors or maintainers of the schema")
    private List<String> authors = new ArrayList<String>();
    /**
     * License identifier, e.g., MIT
     * 
     */
    @JsonProperty("license")
    @JsonPropertyDescription("License identifier, e.g., MIT")
    private String license;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("types")
    private List<Type> types = new ArrayList<Type>();
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("generators")
    private List<Generator> generators = new ArrayList<Generator>();
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
    @JsonProperty("version")
    public String getVersion() {
        return version;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("version")
    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("engine")
    public String getEngine() {
        return engine;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("engine")
    public void setEngine(String engine) {
        this.engine = engine;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("engineVersion")
    public String getEngineVersion() {
        return engineVersion;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("engineVersion")
    public void setEngineVersion(String engineVersion) {
        this.engineVersion = engineVersion;
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

    /**
     * List of authors or maintainers of the schema
     * 
     */
    @JsonProperty("authors")
    public List<String> getAuthors() {
        return authors;
    }

    /**
     * List of authors or maintainers of the schema
     * 
     */
    @JsonProperty("authors")
    public void setAuthors(List<String> authors) {
        this.authors = authors;
    }

    /**
     * License identifier, e.g., MIT
     * 
     */
    @JsonProperty("license")
    public String getLicense() {
        return license;
    }

    /**
     * License identifier, e.g., MIT
     * 
     */
    @JsonProperty("license")
    public void setLicense(String license) {
        this.license = license;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("types")
    public List<Type> getTypes() {
        return types;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("types")
    public void setTypes(List<Type> types) {
        this.types = types;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("generators")
    public List<Generator> getGenerators() {
        return generators;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("generators")
    public void setGenerators(List<Generator> generators) {
        this.generators = generators;
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
        sb.append(ComponentLibrary.class.getName()).append('@').append(Integer.toHexString(System.identityHashCode(this))).append('[');
        sb.append("name");
        sb.append('=');
        sb.append(((this.name == null)?"<null>":this.name));
        sb.append(',');
        sb.append("version");
        sb.append('=');
        sb.append(((this.version == null)?"<null>":this.version));
        sb.append(',');
        sb.append("engine");
        sb.append('=');
        sb.append(((this.engine == null)?"<null>":this.engine));
        sb.append(',');
        sb.append("engineVersion");
        sb.append('=');
        sb.append(((this.engineVersion == null)?"<null>":this.engineVersion));
        sb.append(',');
        sb.append("description");
        sb.append('=');
        sb.append(((this.description == null)?"<null>":this.description));
        sb.append(',');
        sb.append("authors");
        sb.append('=');
        sb.append(((this.authors == null)?"<null>":this.authors));
        sb.append(',');
        sb.append("license");
        sb.append('=');
        sb.append(((this.license == null)?"<null>":this.license));
        sb.append(',');
        sb.append("types");
        sb.append('=');
        sb.append(((this.types == null)?"<null>":this.types));
        sb.append(',');
        sb.append("generators");
        sb.append('=');
        sb.append(((this.generators == null)?"<null>":this.generators));
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
        result = ((result* 31)+((this.engineVersion == null)? 0 :this.engineVersion.hashCode()));
        result = ((result* 31)+((this.license == null)? 0 :this.license.hashCode()));
        result = ((result* 31)+((this.types == null)? 0 :this.types.hashCode()));
        result = ((result* 31)+((this.engine == null)? 0 :this.engine.hashCode()));
        result = ((result* 31)+((this.name == null)? 0 :this.name.hashCode()));
        result = ((result* 31)+((this.generators == null)? 0 :this.generators.hashCode()));
        result = ((result* 31)+((this.description == null)? 0 :this.description.hashCode()));
        result = ((result* 31)+((this.additionalProperties == null)? 0 :this.additionalProperties.hashCode()));
        result = ((result* 31)+((this.version == null)? 0 :this.version.hashCode()));
        result = ((result* 31)+((this.authors == null)? 0 :this.authors.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof ComponentLibrary) == false) {
            return false;
        }
        ComponentLibrary rhs = ((ComponentLibrary) other);
        return (((((((((((this.engineVersion == rhs.engineVersion)||((this.engineVersion!= null)&&this.engineVersion.equals(rhs.engineVersion)))&&((this.license == rhs.license)||((this.license!= null)&&this.license.equals(rhs.license))))&&((this.types == rhs.types)||((this.types!= null)&&this.types.equals(rhs.types))))&&((this.engine == rhs.engine)||((this.engine!= null)&&this.engine.equals(rhs.engine))))&&((this.name == rhs.name)||((this.name!= null)&&this.name.equals(rhs.name))))&&((this.generators == rhs.generators)||((this.generators!= null)&&this.generators.equals(rhs.generators))))&&((this.description == rhs.description)||((this.description!= null)&&this.description.equals(rhs.description))))&&((this.additionalProperties == rhs.additionalProperties)||((this.additionalProperties!= null)&&this.additionalProperties.equals(rhs.additionalProperties))))&&((this.version == rhs.version)||((this.version!= null)&&this.version.equals(rhs.version))))&&((this.authors == rhs.authors)||((this.authors!= null)&&this.authors.equals(rhs.authors))));
    }

}
