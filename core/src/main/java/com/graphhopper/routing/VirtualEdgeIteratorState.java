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
package com.graphhopper.routing;

import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.util.CHEdgeIteratorState;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.PointList;

/**
 * Creates an edge state decoupled from a graph where nodes, pointList, etc are kept in memory.
 * <p>
 * Note, this class is not suited for public use and can change with minor releases unexpectedly or
 * even gets removed.
 */
public class VirtualEdgeIteratorState implements EdgeIteratorState, CHEdgeIteratorState {
    private final PointList pointList;
    private final int edgeId;
    private final int baseNode;
    private final int adjNode;
    private final int originalTraversalKey;
    private double distance;
    private long flags;
    private String name;
    // indication if edges are dispreferred as start/stop edge 
    private boolean unfavored;
    private EdgeIteratorState reverseEdge;

    public VirtualEdgeIteratorState(int originalTraversalKey, int edgeId, int baseNode, int adjNode, double distance, long flags, String name, PointList pointList) {
        this.originalTraversalKey = originalTraversalKey;
        this.edgeId = edgeId;
        this.baseNode = baseNode;
        this.adjNode = adjNode;
        this.distance = distance;
        this.flags = flags;
        this.name = name;
        this.pointList = pointList;
    }

    /**
     * This method returns the original edge via its traversal key. I.e. also the direction is
     * already correctly encoded.
     * <p>
     *
     * @see GHUtility#createEdgeKey(int, int, int, boolean)
     */
    public int getOriginalTraversalKey() {
        return originalTraversalKey;
    }

    @Override
    public int getEdge() {
        return edgeId;
    }

    @Override
    public int getBaseNode() {
        return baseNode;
    }

    @Override
    public int getAdjNode() {
        return adjNode;
    }

    @Override
    public PointList fetchWayGeometry(int mode) {
        if (pointList.getSize() == 0)
            return PointList.EMPTY;
        // due to API we need to create a new instance per call!
        if (mode == 3)
            return pointList.clone(false);
        else if (mode == 1)
            return pointList.copy(0, pointList.getSize() - 1);
        else if (mode == 2)
            return pointList.copy(1, pointList.getSize());
        else if (mode == 0) {
            if (pointList.getSize() == 1)
                return PointList.EMPTY;
            return pointList.copy(1, pointList.getSize() - 1);
        }
        throw new UnsupportedOperationException("Illegal mode:" + mode);
    }

    @Override
    public EdgeIteratorState setWayGeometry(PointList list) {
        throw new UnsupportedOperationException("Not supported for virtual edge. Set when creating it.");
    }

    @Override
    public double getDistance() {
        return distance;
    }

    @Override
    public EdgeIteratorState setDistance(double dist) {
        this.distance = dist;
        return this;
    }

    @Override
    public long getFlags() {
        return flags;
    }

    @Override
    public EdgeIteratorState setFlags(long flags) {
        this.flags = flags;
        return this;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public EdgeIteratorState setName(String name) {
        this.name = name;
        return this;
    }

    @Override
    public boolean getBool(int key, boolean _default) {
        if (key == EdgeIteratorState.K_UNFAVORED_EDGE)
            return unfavored;

        // for non-existent keys return default
        return _default;
    }

    /**
     * This method sets edge to unfavored status for routing from or to the start/stop points.
     */
    public void setUnfavored(boolean unfavored) {
        this.unfavored = unfavored;
    }

    @Override
    public String toString() {
        return baseNode + "->" + adjNode;
    }

    @Override
    public boolean isShortcut() {
        return false;
    }

    @Override
    public boolean isForward(FlagEncoder encoder) {
        return encoder.isForward(getFlags());
    }

    @Override
    public boolean isBackward(FlagEncoder encoder) {
        return encoder.isBackward(getFlags());
    }

    @Override
    public int getAdditionalField() {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public int getMergeStatus(long flags) {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public int getSkippedEdge1() {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public int getSkippedEdge2() {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public void setSkippedEdges(int edge1, int edge2) {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public EdgeIteratorState detach(boolean reverse) {
        if (reverse) {
            return reverseEdge;
        } else {
            return this;
        }
    }

    @Override
    public EdgeIteratorState setAdditionalField(int value) {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public EdgeIteratorState copyPropertiesTo(EdgeIteratorState edge) {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public CHEdgeIteratorState setWeight(double weight) {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public double getWeight() {
        throw new UnsupportedOperationException("Not supported.");
    }

    void setReverseEdge(EdgeIteratorState reverseEdge) {
        this.reverseEdge = reverseEdge;
    }

}
