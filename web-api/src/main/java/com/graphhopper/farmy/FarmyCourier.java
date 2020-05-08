package com.graphhopper.farmy;

import com.fasterxml.jackson.annotation.*;

import java.util.HashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "id",
        "isPlus",
        "name",
        "farmyVehicle"
})
public class FarmyCourier {

    @JsonProperty("id")
    private Object id;
    @JsonProperty("isPlus")
    private Boolean isPlus;
    @JsonProperty("name")
    private String name;
    @JsonProperty("vehicle")
    private FarmyVehicle farmyVehicle;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("id")
    public Object getId() {
        return id;
    }

    @JsonProperty("id")
    public void setId(Object id) {
        this.id = id;
    }

    @JsonProperty("isPlus")
    public Boolean getIsPlus() {
        return isPlus;
    }

    @JsonProperty("isPlus")
    public void setIsPlus(Boolean isPlus) {
        this.isPlus = isPlus;
    }

    @JsonProperty("name")
    public String getName() {
        return name;
    }

    @JsonProperty("name")
    public void setName(String name) {
        this.name = name;
    }

    @JsonProperty("farmyVehicle")
    public FarmyVehicle getFarmyVehicle() {
        return farmyVehicle;
    }

    @JsonProperty("farmyVehicle")
    public void setFarmyVehicle(FarmyVehicle farmyVehicle) {
        this.farmyVehicle = farmyVehicle;
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }
}

