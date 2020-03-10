package com.graphhopper.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.graphhopper.GHRequest;
import com.graphhopper.util.Helper;
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
    private List<String> fromCurbsides;
    private List<String> toCurbsides;
    private int called = 0;
    boolean identicalLists = true;
    private final Set<String> outArrays = new HashSet<>(5);
    private boolean failFast = true;

    public GHMRequest() {
        this(10);
    }

    public GHMRequest(int size) {
        super(0);
        fromPoints = new ArrayList<>(size);
        toPoints = new ArrayList<>(size);
        fromPointHints = new ArrayList<>(size);
        toPointHints = new ArrayList<>(size);
        fromCurbsides = new ArrayList<>(size);
        toCurbsides = new ArrayList<>(size);
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

    /**
     * This methods adds the coordinate as 'from' and 'to' to the request.
     */
    @Override
    public GHMRequest addPoint(GHPoint point) {
        fromPoints.add(point);
        toPoints.add(point);
        return this;
    }

    @Override
    public List<GHPoint> getPoints() {
        throw new IllegalStateException("use getFromPoints or getToPoints");
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

    public List<GHPoint> getFromPoints() {
        return fromPoints;
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

    public List<GHPoint> getToPoints() {
        return toPoints;
    }

    @Override
    public GHRequest setPointHints(List<String> pointHints) {
        setToPointHints(pointHints);
        this.fromPointHints = this.toPointHints;
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

    public GHRequest addFromPointHint(String pointHint) {
        this.fromPointHints.add(pointHint);
        return this;
    }

    public GHRequest setFromPointHints(List<String> pointHints) {
        // create new array as we modify pointHints in compactPointHints
        this.fromPointHints = new ArrayList<>(pointHints);
        return this;
    }

    public List<String> getFromPointHints() {
        return fromPointHints;
    }

    public GHRequest addToPointHint(String pointHint) {
        this.toPointHints.add(pointHint);
        return this;
    }

    public GHRequest setToPointHints(List<String> pointHints) {
        // create new array as we modify pointHints in compactPointHints
        this.toPointHints = new ArrayList<>(pointHints);
        return this;
    }

    public List<String> getToPointHints() {
        return toPointHints;
    }

    public GHMRequest addFromCurbside(String curbside) {
        fromCurbsides.add(curbside);
        return this;
    }

    public GHMRequest setFromCurbsides(List<String> curbsides) {
        fromCurbsides = curbsides;
        return this;
    }

    public List<String> getFromCurbsides() {
        return fromCurbsides;
    }

    public GHMRequest addToCurbside(String curbside) {
        toCurbsides.add(curbside);
        return this;
    }

    public GHMRequest setToCurbsides(List<String> curbsides) {
        toCurbsides = curbsides;
        return this;
    }

    public List<String> getToCurbsides() {
        return toCurbsides;
    }

    @Override
    public GHRequest setCurbsides(List<String> curbsides) {
        fromCurbsides = curbsides;
        toCurbsides = curbsides;
        return this;
    }

    @Override
    public List<String> getCurbsides() {
        throw new IllegalStateException("Use getFromCurbsides or getToCurbsides");
    }

    @Override
    public boolean hasCurbsides() {
        return fromCurbsides.size() == fromPoints.size() && !fromPoints.isEmpty() &&
                toCurbsides.size() == toPoints.size() && !toPoints.isEmpty();
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

    /**
     * This method makes it more likely that hasPointHints returns true as often point hints are added although the
     * strings are empty. But because they could be used as placeholder we do not know earlier if they are meaningless.
     */
    void compactPointHints() {
        if (called > 0)
            throw new IllegalStateException("cannot call more than once");
        called++;
        boolean clear = true;
        for (String hint : toPointHints) {
            if (!Helper.isEmpty(hint)) {
                clear = false;
                break;
            }
        }
        if (clear)
            toPointHints.clear();

        clear = true;
        for (String hint : fromPointHints) {
            if (!Helper.isEmpty(hint)) {
                clear = false;
                break;
            }
        }
        if (clear)
            fromPointHints.clear();
    }
}
