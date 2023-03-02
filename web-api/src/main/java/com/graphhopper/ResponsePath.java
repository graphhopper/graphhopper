/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper;

import com.graphhopper.util.InstructionList;
import com.graphhopper.util.PointList;
import com.graphhopper.util.details.PathDetail;
import org.locationtech.jts.geom.Envelope;

import java.math.BigDecimal;
import java.util.*;

/**
 * This class holds the data like points and instructions of a Path.
 * <p>
 *
 * @author Peter Karich
 */
public class ResponsePath {
    private final List<Throwable> errors = new ArrayList<>(4);
    private List<String> description;
    private double distance;
    private double ascend;
    private double descend;
    private double routeWeight;
    private long time;
    private String debugInfo = "";
    private InstructionList instructions;
    private PointList waypointList = PointList.EMPTY;
    private List<Interval> waypointIntervals;
    private PointList pointList = PointList.EMPTY;
    private int numChanges;
    private final List<Trip.Leg> legs = new ArrayList<>(5);
    private final List<Integer> pointsOrder = new ArrayList<>(5);
    private final Map<String, List<PathDetail>> pathDetails = new HashMap<>();
    private BigDecimal fare;
    private boolean impossible = false;

    /**
     * @return the description of this route alternative to make it meaningful for the user e.g. it
     * displays one or two main roads of the route.
     */
    public List<String> getDescription() {
        if (description == null)
            return Collections.emptyList();
        return description;
    }

    public ResponsePath setDescription(List<String> names) {
        this.description = names;
        return this;
    }

    public ResponsePath addDebugInfo(String debugInfo) {
        if (debugInfo == null)
            throw new IllegalStateException("Debug information has to be none null");

        if (!this.debugInfo.isEmpty())
            this.debugInfo += ";";

        this.debugInfo += debugInfo;
        return this;
    }

    public String getDebugInfo() {
        return debugInfo;
    }

    public ResponsePath setPointsOrder(List<Integer> list) {
        pointsOrder.clear();
        pointsOrder.addAll(list);
        return this;
    }

    public List<Integer> getPointsOrder() {
        return pointsOrder;
    }

    /**
     * This method returns all points on the path. Keep in mind that calculating the distance from
     * these points might yield different results compared to getDistance as points could have been
     * simplified on import or after querying.
     */
    public PointList getPoints() {
        check("getPoints");
        return pointList;
    }

    public ResponsePath setPoints(PointList points) {
        if (pointList != PointList.EMPTY)
            throw new IllegalStateException("Cannot call setPoint twice");

        pointList = points;
        return this;
    }

    /**
     * This method returns the input points snapped to the road network.
     */
    public PointList getWaypoints() {
        check("getWaypoints");
        return waypointList;
    }

    /**
     * This method initializes this path with the snapped input points.
     */
    public ResponsePath setWaypoints(PointList wpList) {
        if (waypointList != PointList.EMPTY)
            throw new IllegalStateException("Cannot call setWaypoints twice");

        this.waypointList = wpList;
        return this;
    }

    public List<Interval> getWaypointIntervals() {
        check("getWaypointIntervals");
        return waypointIntervals;
    }

    public ResponsePath setWaypointIntervals(List<Interval> waypointIntervals) {
        this.waypointIntervals = waypointIntervals;
        return this;
    }

    /**
     * This method returns the distance of the path. Always prefer this method over
     * getPoints().calcDistance
     * <p>
     *
     * @return distance in meter
     */
    public double getDistance() {
        check("getDistance");
        return distance;
    }

    public ResponsePath setDistance(double distance) {
        this.distance = distance;
        return this;
    }

    /**
     * This method returns the total elevation change (going upwards) in meter.
     * <p>
     *
     * @return ascend in meter
     */
    public double getAscend() {
        return ascend;
    }

    public ResponsePath setAscend(double ascend) {
        if (ascend < 0 || Double.isNaN(ascend))
            throw new IllegalStateException("ascend has to be positive but was " + ascend);

        this.ascend = ascend;
        return this;
    }

    /**
     * This method returns the total elevation change (going downwards) in meter.
     * <p>
     *
     * @return decline in meter
     */
    public double getDescend() {
        return descend;
    }

    public ResponsePath setDescend(double descend) {
        if (descend < 0 || Double.isNaN(descend))
            throw new IllegalStateException("descend has to be positive but was " + descend);

        this.descend = descend;
        return this;
    }

    /**
     * @return time in millis
     */
    public long getTime() {
        check("getTimes");
        return time;
    }

    public ResponsePath setTime(long timeInMillis) {
        this.time = timeInMillis;
        return this;
    }

    /**
     * This method returns a double value which is better than the time for comparison of routes but
     * only if you know what you are doing, e.g. only to compare routes gained with the same query
     * parameters like vehicle.
     */
    public double getRouteWeight() {
        check("getRouteWeight");
        return routeWeight;
    }

    public ResponsePath setRouteWeight(double weight) {
        this.routeWeight = weight;
        return this;
    }

    /**
     * Calculates the 2D bounding box of this route
     */
    public Envelope calcBBox2D() {
        check("calcBBox2D");
        Envelope bounds = new Envelope();
        for (int i = 0; i < pointList.size(); i++) {
            bounds.expandToInclude(pointList.getLon(i), pointList.getLat(i));
        }
        return bounds;
    }

    @Override
    public String toString() {
        String str = "nodes:" + pointList.size() + "; " + pointList.toString();
        if (instructions != null && !instructions.isEmpty())
            str += ", " + instructions.toString();

        if (hasErrors())
            str += ", " + errors.toString();

        return str;
    }

    public InstructionList getInstructions() {
        check("getInstructions");
        if (instructions == null)
            throw new IllegalArgumentException("To access instructions you need to enable creation before routing");

        return instructions;
    }

    public void setInstructions(InstructionList instructions) {
        this.instructions = instructions;
    }

    /**
     * Adds the given PathDetails to the existing ones. If there are already PathDetails set, the number
     * details has to be equal to <code>details</code>.
     *
     * @param details The PathDetails to add
     */
    public void addPathDetails(Map<String, List<PathDetail>> details) {
        if (!this.pathDetails.isEmpty() && !details.isEmpty() && this.pathDetails.size() != details.size()) {
            throw new IllegalStateException("Details have to be the same size");
        }
        for (Map.Entry<String, List<PathDetail>> detailEntry : details.entrySet()) {
            String key = detailEntry.getKey();
            if (this.pathDetails.containsKey(key)) {
                this.pathDetails.get(key).addAll(detailEntry.getValue());
            } else {
                this.pathDetails.put(key, detailEntry.getValue());
            }
        }
    }

    public Map<String, List<PathDetail>> getPathDetails() {
        return this.pathDetails;
    }

    private void check(String method) {
        if (hasErrors()) {
            throw new RuntimeException("You cannot call " + method + " if response contains errors. Check this with ghResponse.hasErrors(). "
                    + "Errors are: " + getErrors());
        }
    }

    /**
     * @return true if this alternative response contains one or more errors
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public List<Throwable> getErrors() {
        return errors;
    }

    public ResponsePath addError(Throwable error) {
        errors.add(error);
        return this;
    }

    public ResponsePath addErrors(List<Throwable> errors) {
        this.errors.addAll(errors);
        return this;
    }

    public void setNumChanges(int numChanges) {
        this.numChanges = numChanges;
    }

    public int getNumChanges() {
        return numChanges;
    }

    public List<Trip.Leg> getLegs() {
        return legs;
    }

    public void setFare(BigDecimal fare) {
        this.fare = fare;
    }

    public BigDecimal getFare() {
        return fare;
    }

    public boolean isImpossible() {
        return impossible;
    }

    public void setImpossible(boolean impossible) {
        this.impossible = impossible;
    }

    public static class Interval {
        public int start;
        public int end;

        public Interval(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }
}
