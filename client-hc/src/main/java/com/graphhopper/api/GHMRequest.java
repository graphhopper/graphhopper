package com.graphhopper.api;

import com.graphhopper.GHRequest;
import com.graphhopper.util.shapes.GHPoint;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Peter Karich
 */
public class GHMRequest extends GHRequest {

    private List<GHPoint> fromPoints;
    private List<GHPoint> toPoints;
    boolean identicalLists = true;
    private final Set<String> outArrays = new HashSet<String>(5);

    public GHMRequest() {
        this(10);
    }

    public GHMRequest(int size) {
        super(0);
        fromPoints = new ArrayList<GHPoint>(size);
        toPoints = new ArrayList<GHPoint>(size);
    }

    /**
     * Currently: weights, times, distances and paths possible. Where paths is
     * the most expensive to calculate and limited to maximum 10*10 points (via
     * API end point).
     */
    public GHMRequest addOutArray(String type) {
        outArrays.add(type);
        return this;
    }

    public Set<String> getOutArrays() {
        return outArrays;
    }

    public GHMRequest addAllPoints(List<GHPoint> points) {
        for (GHPoint p : points) {
            addPoint(p);
        }
        return this;
    }

    @Override
    public List<GHPoint> getPoints() {
        throw new IllegalStateException("use getFromPlaces or getToPlaces");
    }

    public List<GHPoint> getFromPoints() {
        return fromPoints;
    }

    public List<GHPoint> getToPoints() {
        return toPoints;
    }

    /**
     * This methods adds the places as 'from' and 'to' place to the request.
     */
    @Override
    public GHMRequest addPoint(GHPoint point) {
        fromPoints.add(point);
        toPoints.add(point);
        return this;
    }

    public GHMRequest addFromPoint(GHPoint point) {
        fromPoints.add(point);
        identicalLists = false;
        return this;
    }

    public GHMRequest addFromPoints(List<GHPoint> points) {
        fromPoints = points;
        identicalLists = false;
        return this;
    }

    public GHMRequest addToPoint(GHPoint point) {
        toPoints.add(point);
        identicalLists = false;
        return this;
    }

    public GHMRequest addToPoints(List<GHPoint> points) {
        toPoints = points;
        identicalLists = false;
        return this;
    }
}
