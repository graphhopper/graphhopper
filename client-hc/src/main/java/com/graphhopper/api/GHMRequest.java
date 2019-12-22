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
    private List<String> fromPointHints;
    private List<String> toPointHints;
    boolean identicalLists = true;
    private final Set<String> outArrays = new HashSet<>(5);

    public GHMRequest() {
        this(10);
    }

    public GHMRequest(int size) {
        super(0);
        fromPoints = new ArrayList<>(size);
        toPoints = new ArrayList<>(size);
        fromPointHints = new ArrayList<>(size);
        toPointHints = new ArrayList<>(size);
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
        throw new IllegalStateException("use getFromPoints or getToPoints");
    }

    public List<GHPoint> getFromPoints() {
        return fromPoints;
    }

    public List<GHPoint> getToPoints() {
        return toPoints;
    }

    /**
     * This methods adds the coordinate as 'from' and 'to' to the request.
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

    public GHMRequest setFromPoints(List<GHPoint> points) {
        fromPoints = points;
        identicalLists = false;
        return this;
    }

    public GHRequest addFromPointHint(String pointHint) {
        this.fromPointHints.add(pointHint);
        return this;
    }

    public GHRequest setFromPointHints(List<String> pointHints) {
        this.fromPointHints = pointHints;
        return this;
    }

    public List<String> getFromPointHints() {
        return fromPointHints;
    }

    public GHMRequest addToPoint(GHPoint point) {
        toPoints.add(point);
        identicalLists = false;
        return this;
    }

    public GHMRequest setToPoints(List<GHPoint> points) {
        toPoints = points;
        identicalLists = false;
        return this;
    }

    public GHRequest addToPointHint(String pointHint) {
        this.toPointHints.add(pointHint);
        return this;
    }

    public GHRequest setToPointHints(List<String> pointHints) {
        this.toPointHints = pointHints;
        return this;
    }

    public List<String> getToPointHints() {
        return toPointHints;
    }

    @Override
    public GHRequest setPointHints(List<String> pointHints) {
        super.setPointHints(pointHints);
        this.fromPointHints = pointHints;
        this.toPointHints = pointHints;
        return this;
    }

    @Override
    public List<String> getPointHints() {
        throw new IllegalStateException("Use getFromPointHints or getToPointHints");
    }

    @Override
    public boolean hasPointHints() {
        return this.fromPointHints.size() == this.fromPoints.size() && !fromPoints.isEmpty() &&
                this.toPointHints.size() == this.toPoints.size() && !toPoints.isEmpty();
    }
}
