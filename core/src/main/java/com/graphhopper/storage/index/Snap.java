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
package com.graphhopper.storage.index;

import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.GHPoint;
import com.graphhopper.util.shapes.GHPoint3D;

import java.util.List;

/**
 * Result of LocationIndex lookup.
 * <pre> X=query coordinates S=snapped coordinates: "snapping" real coords to road N=tower or pillar
 * node T=closest tower node XS=distance
 * X
 * |
 * T--S----N
 * </pre>
 * <p>
 *
 * @author Peter Karich
 */
public class Snap {
    public static final int INVALID_NODE = -1;
    private final GHPoint queryPoint;
    private double queryDistance = Double.MAX_VALUE;
    private int wayIndex = -1;
    private int closestNode = INVALID_NODE;
    private EdgeIteratorState closestEdge;
    private GHPoint3D snappedPoint;
    private Position snappedPosition;

    public Snap(double queryLat, double queryLon) {
        queryPoint = new GHPoint(queryLat, queryLon);
    }

    /**
     * Returns the closest matching node. This is either a tower node of the base graph
     * or a virtual node (see also {@link QueryGraph#create(BaseGraph, List)}).
     *
     * @return {@link #INVALID_NODE} if nothing found, this should be avoided via a call of 'isValid'
     */
    public int getClosestNode() {
        return closestNode;
    }

    public void setClosestNode(int node) {
        closestNode = node;
    }

    /**
     * @return the distance of the query to the snapped coordinates. In meter
     */
    public double getQueryDistance() {
        return queryDistance;
    }

    public void setQueryDistance(double dist) {
        queryDistance = dist;
    }

    public int getWayIndex() {
        return wayIndex;
    }

    public void setWayIndex(int wayIndex) {
        this.wayIndex = wayIndex;
    }

    /**
     * @return 0 if on edge. 1 if on pillar node and 2 if on tower node.
     */
    public Position getSnappedPosition() {
        return snappedPosition;
    }

    public void setSnappedPosition(Position pos) {
        this.snappedPosition = pos;
    }

    /**
     * @return true if a closest node was found
     */
    public boolean isValid() {
        return closestNode >= 0;
    }

    public EdgeIteratorState getClosestEdge() {
        return closestEdge;
    }

    public void setClosestEdge(EdgeIteratorState edge) {
        closestEdge = edge;
    }

    public GHPoint getQueryPoint() {
        return queryPoint;
    }

    /**
     * Calculates the position of the query point 'snapped' to a close road segment or node. Call
     * calcSnappedPoint before, if not, an IllegalStateException is thrown.
     */
    public GHPoint3D getSnappedPoint() {
        if (snappedPoint == null)
            throw new IllegalStateException("Calculate snapped point before!");
        return snappedPoint;
    }

    /**
     * Calculates the closest point on the edge from the query point. If too close to a tower or pillar node this method
     * might change the snappedPosition and wayIndex.
     */
    public void calcSnappedPoint(DistanceCalc distCalc) {
        if (closestEdge == null)
            throw new IllegalStateException("No closest edge?");
        if (snappedPoint != null)
            throw new IllegalStateException("Calculate snapped point only once");

        PointList fullPL = getClosestEdge().fetchWayGeometry(FetchMode.ALL);
        double tmpLat = fullPL.getLat(wayIndex);
        double tmpLon = fullPL.getLon(wayIndex);
        double tmpEle = fullPL.getEle(wayIndex);
        if (snappedPosition != Position.EDGE) {
            snappedPoint = new GHPoint3D(tmpLat, tmpLon, tmpEle);
            return;
        }

        double queryLat = getQueryPoint().lat, queryLon = getQueryPoint().lon;
        double adjLat = fullPL.getLat(wayIndex + 1), adjLon = fullPL.getLon(wayIndex + 1);
        if (distCalc.validEdgeDistance(queryLat, queryLon, tmpLat, tmpLon, adjLat, adjLon)) {
            GHPoint crossingPoint = distCalc.calcCrossingPointToEdge(queryLat, queryLon, tmpLat, tmpLon, adjLat, adjLon);
            double adjEle = fullPL.getEle(wayIndex + 1);

            // if crossingPoint is too close to a pillar or tower node we have to fix the snappedPosition and wayIndex to make it consistent with GHPoint::equals
            if (NumHelper.equalsEps(crossingPoint.lat, tmpLat) && NumHelper.equalsEps(crossingPoint.lon, tmpLon)) {
                snappedPosition = wayIndex == 0 ? Position.TOWER : Position.PILLAR;
                snappedPoint = new GHPoint3D(tmpLat, tmpLon, tmpEle);
            } else if (NumHelper.equalsEps(crossingPoint.lat, adjLat) && NumHelper.equalsEps(crossingPoint.lon, adjLon)) {
                wayIndex++;
                snappedPosition = wayIndex == fullPL.size() - 1 ? Position.TOWER : Position.PILLAR;
                snappedPoint = new GHPoint3D(adjLat, adjLon, adjEle);
            } else {
                snappedPoint = new GHPoint3D(crossingPoint.lat, crossingPoint.lon, (tmpEle + adjEle) / 2);
            }
        } else {
            // outside of edge segment [wayIndex, wayIndex+1]
            snappedPoint = new GHPoint3D(tmpLat, tmpLon, tmpEle);
            assert snappedPosition == Position.PILLAR : "incorrect pos: " + snappedPosition + " for " + snappedPoint + ", " + fullPL + ", " + wayIndex;
        }
    }

    @Override
    public String toString() {
        if (closestEdge != null)
            return snappedPosition + ", " + closestNode + " " + closestEdge.getEdge() + ":" + closestEdge.getBaseNode() + "-" + closestEdge.getAdjNode() +
                    " snap: [" + Helper.round6(snappedPoint.getLat()) + ", " + Helper.round6(snappedPoint.getLon()) + "]," +
                    " query: [" + Helper.round6(queryPoint.getLat()) + "," + Helper.round6(queryPoint.getLon()) + "]";
        return closestNode + ", " + queryPoint + ", " + wayIndex;
    }

    /**
     * Whether the query point is projected onto a tower node, pillar node or somewhere within
     * the closest edge.
     * <p>
     * Due to precision differences it is hard to define when something is exactly 90° or "on-node"
     * like TOWER or PILLAR or if it is more "on-edge" (EDGE). The default mechanism is to prefer
     * "on-edge" even if it could be 90°. To prefer "on-node" you could use e.g. GHPoint.equals with
     * a default precision of 1e-6.
     * <p>
     *
     * @see DistanceCalc#validEdgeDistance
     */
    public enum Position {
        EDGE, TOWER, PILLAR
    }
}
