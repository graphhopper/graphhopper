package com.graphhopper.farmy;

import com.fasterxml.jackson.annotation.*;

import java.util.HashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "id",
        "name",
        "capacity",
        "fixed_costs",
        "variable_costs",
        "is_plus"
})
public class FarmyVehicle {

    @JsonProperty("id")
    private Object id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("courier")
    private FarmyCourier courier;

    @JsonProperty("capacity")
    private int capacity;

    @JsonProperty("fixed_costs")
    private int fixedCosts;

    @JsonProperty("variable_costs")
    private int variableCosts;

    @JsonProperty("is_plus")
    private boolean isPlus;

    private String FarmyErpVechicleId;

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

    @JsonAnyGetter
    public String getName() {
        return name;
    }

    @JsonAnySetter
    public void setName(String name) {
        this.name = name;
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }

    @JsonAnyGetter
    public FarmyCourier getCourier() {
        return courier;
    }

    @JsonAnySetter
    public void setCourier(FarmyCourier courier) {
        this.courier = courier;
    }

    @JsonAnyGetter
    public int getCapacity() {
        return capacity;
    }

    @JsonAnySetter
    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    @JsonAnyGetter
    public int getFixedCosts() {
        return fixedCosts;
    }

    @JsonAnySetter
    public void setFixedCosts(int fixedCosts) {
        this.fixedCosts = fixedCosts;
    }

    @JsonAnyGetter
    public int getVariableCosts() {
        return variableCosts;
    }

    @JsonAnySetter
    public void setVariableCosts(int variableCosts) {
        this.variableCosts = variableCosts;
    }

    @JsonAnyGetter
    public boolean isPlus() {
        return isPlus;
    }

    @JsonAnySetter
    public void setPlus(boolean plus) {
        isPlus = plus;
    }

    @JsonAnyGetter
    public String getFarmyErpVechicleId() {
        return FarmyErpVechicleId;
    }

    @JsonAnySetter
    public void setFarmyErpVechicleId(String farmyErpVechicleId) {
        FarmyErpVechicleId = farmyErpVechicleId;
    }
}
