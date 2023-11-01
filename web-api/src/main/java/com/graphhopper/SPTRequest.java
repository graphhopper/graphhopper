package com.graphhopper;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.graphhopper.util.CustomModel;
import com.graphhopper.util.shapes.GHPoint;

import java.util.List;

public class SPTRequest {

    CustomModel customModel;
    String profile;
    boolean reverseFlow;
    GHPoint point;
    List<String> columns;
    @JsonProperty("time_limit")
    long timeLimitInSeconds = 600;
    @JsonProperty("distance_limit")
    long distanceInMeter = -1;

    public void setCustomModel(CustomModel customModel) {
        this.customModel = customModel;
    }

    public CustomModel getCustomModel() {
        return customModel;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    public String getProfile() {
        return profile;
    }

    public void setReverseFlow(boolean reverseFlow) {
        this.reverseFlow = reverseFlow;
    }

    public boolean isReverseFlow() {
        return reverseFlow;
    }

    public void setPoint(GHPoint point) {
        this.point = point;
    }

    public GHPoint getPoint() {
        return point;
    }

    public void setColumns(List<String> columns) {
        this.columns = columns;
    }

    public List<String> getColumns() {
        return columns;
    }

    public void setTimeLimit(long timeLimitInSeconds) {
        this.timeLimitInSeconds = timeLimitInSeconds;
    }

    public long getTimeLimit() {
        return timeLimitInSeconds;
    }

    public void setDistanceLimit(long distanceInMeter) {
        this.distanceInMeter = distanceInMeter;
    }

    public long getDistanceLimit() {
        return distanceInMeter;
    }
}
