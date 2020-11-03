package com.graphhopper.farmy;

import com.fasterxml.jackson.annotation.*;

import java.util.HashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "id",
        "name",
        "capacity",
        "fixedCosts",
        "isPlus",
        "farmyErpVehicleId",
        "isReturnToDepot",
        "costPerDistance",
        "costsPerTransportTime",
        "costsPerServiceTime",
        "costPerWaitingTime",
        "latestArrival"
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

    @JsonProperty("fixedCosts")
    private double fixedCosts;

    @JsonProperty("isPlus")
    private boolean isPlus;

    @JsonProperty("farmyErpVehicleId")
    private String FarmyErpVechicleId;

    @JsonProperty("isReturnToDepot")
    private boolean returnToDepot;
    @JsonProperty("costPerDistance")
    private double costsPerDistance;
    @JsonProperty("costsPerTransportTime")
    private double costsPerTransportTime;
    @JsonProperty("costsPerServiceTime")
    private double costsPerServiceTime;
    @JsonProperty("costPerWaitingTime")
    private double costPerWaitingTime;
    @JsonProperty("latestArrival")
    private double latestArrival;

    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonAnyGetter
    public Object getId() {
        return id;
    }
    @JsonAnySetter
    public void setId(Object id) {
        this.id = id;
    }
    @JsonAnyGetter
    public String getName() {
        return name;
    }
    @JsonAnySetter
    public void setName(String name) { this.name = (name != null && !name.isEmpty() ? name : String.format("vehicle%s#%s", this.getId(), this.getFarmyErpVechicleId())); }
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
    public double getFixedCosts() { return fixedCosts; }
    @JsonAnySetter
    public void setFixedCosts(int fixedCosts) {
        this.fixedCosts = fixedCosts;
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
    @JsonAnyGetter
    public boolean isReturnToDepot() { return returnToDepot; }
    @JsonAnySetter
    public void setReturnToDepot(boolean returnToDepot) { this.returnToDepot = returnToDepot; }
    @JsonAnyGetter
    public double getCostsPerTransportTime() { return costsPerTransportTime; }
    @JsonAnySetter
    public void setCostsPerTransportTime(int costsPerTransportTime) { this.costsPerTransportTime = costsPerTransportTime; }
    @JsonAnyGetter
    public double getCostsPerDistance() { return costsPerDistance; }
    @JsonAnySetter
    public void setCostsPerDistance(int costsPerDistance) { this.costsPerDistance = costsPerDistance; }
    @JsonAnyGetter
    public double getCostsPerServiceTime() { return costsPerServiceTime; }
    @JsonAnySetter
    public void setCostsPerServiceTime(int costsPerServiceTime) { this.costsPerServiceTime = costsPerServiceTime; }
    @JsonAnyGetter
    public double getCostPerWaitingTime() { return costPerWaitingTime; }
    @JsonAnySetter
    public void setCostPerWaitingTime(int costPerWaitingTime) { this.costPerWaitingTime = costPerWaitingTime; }
    @JsonAnyGetter
    public double getLatestArrival() { return latestArrival; }
    @JsonAnySetter
    public void setLatestArrival(double latestArrival) { this.latestArrival = latestArrival; }
}
