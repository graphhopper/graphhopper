package com.graphhopper.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.graphhopper.util.PMap;
import com.graphhopper.util.shapes.GHPoint;

import java.util.Collections;
import java.util.List;

/**
 * @author Peter Karich
 */
public class GHMRequest {
    private String profile;
    private List<GHPoint> points;
    private List<GHPoint> fromPoints;
    private List<GHPoint> toPoints;

    private List<String> pointHints;
    private List<String> fromPointHints;
    private List<String> toPointHints;

    private List<String> curbsides;
    private List<String> fromCurbsides;
    private List<String> toCurbsides;

    private List<String> snapPreventions;
    private final PMap hints = new PMap();
    private List<String> outArrays = Collections.EMPTY_LIST;
    private boolean failFast = false;

    public GHMRequest setProfile(String profile) {
        this.profile = profile;
        return this;
    }

    public String getProfile() {
        return profile;
    }

    public GHMRequest setPoints(List<GHPoint> points) {
        this.points = points;
        return this;
    }

    public List<GHPoint> getPoints() {
        return points;
    }

    public GHMRequest setFromPoints(List<GHPoint> fromPoints) {
        this.fromPoints = fromPoints;
        return this;
    }

    public List<GHPoint> getFromPoints() {
        return fromPoints;
    }

    public GHMRequest setToPoints(List<GHPoint> toPoints) {
        this.toPoints = toPoints;
        return this;
    }

    public List<GHPoint> getToPoints() {
        return toPoints;
    }

    public GHMRequest setPointHints(List<String> pointHints) {
        this.pointHints = pointHints;
        return this;
    }

    public List<String> getPointHints() {
        return pointHints;
    }

    public GHMRequest setFromPointHints(List<String> fromPointHints) {
        this.fromPointHints = fromPointHints;
        return this;
    }

    public List<String> getFromPointHints() {
        return fromPointHints;
    }

    public GHMRequest setToPointHints(List<String> toPointHints) {
        this.toPointHints = toPointHints;
        return this;
    }

    public List<String> getToPointHints() {
        return toPointHints;
    }

    public GHMRequest setCurbsides(List<String> curbsides) {
        this.curbsides = curbsides;
        return this;
    }

    public List<String> getCurbsides() {
        return curbsides;
    }

    public GHMRequest setFromCurbsides(List<String> fromCurbsides) {
        this.fromCurbsides = fromCurbsides;
        return this;
    }

    public List<String> getFromCurbsides() {
        return fromCurbsides;
    }

    public GHMRequest setToCurbsides(List<String> toCurbsides) {
        this.toCurbsides = toCurbsides;
        return this;
    }

    public List<String> getToCurbsides() {
        return toCurbsides;
    }

    public GHMRequest setSnapPreventions(List<String> snapPreventions) {
        this.snapPreventions = snapPreventions;
        return this;
    }

    public List<String> getSnapPreventions() {
        return snapPreventions;
    }

    // a good trick to serialize unknown properties into the HintsMap
    @JsonAnySetter
    public GHMRequest putHint(String fieldName, Object value) {
        hints.putObject(fieldName, value);
        return this;
    }

    public PMap getHints() {
        return hints;
    }

    /**
     * Possible values are 'weights', 'times', 'distances'
     */
    public GHMRequest setOutArrays(List<String> outArrays) {
        this.outArrays = outArrays;
        return this;
    }

    public List<String> getOutArrays() {
        return outArrays;
    }

    /**
     * @param failFast if false the matrix calculation will be continued even when some points are not connected
     */
    @JsonProperty("fail_fast")
    public void setFailFast(boolean failFast) {
        this.failFast = failFast;
    }

    public boolean getFailFast() {
        return failFast;
    }

}
