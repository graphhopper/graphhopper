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

import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.profiles.EnumEncodedValue;
import com.graphhopper.routing.profiles.IntEncodedValue;
import com.graphhopper.storage.IntsRef;
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
    private final int originalEdgeKey;
    private double distance;
    private IntsRef edgeFlags;
    private String name;
    // true if edge should be avoided as start/stop
    private boolean unfavored;
    private EdgeIteratorState reverseEdge;
    private final boolean reverse;

    public VirtualEdgeIteratorState(int originalEdgeKey, int edgeId, int baseNode, int adjNode, double distance,
                                    IntsRef edgeFlags, String name, PointList pointList, boolean reverse) {
        this.originalEdgeKey = originalEdgeKey;
        this.edgeId = edgeId;
        this.baseNode = baseNode;
        this.adjNode = adjNode;
        this.distance = distance;
        this.edgeFlags = edgeFlags;
        this.name = name;
        this.pointList = pointList;
        this.reverse = reverse;
    }

    /**
     * This method returns the original edge via its key. I.e. also the direction is
     * already correctly encoded.
     *
     * @see GHUtility#createEdgeKey(int, int, int, boolean)
     */
    public int getOriginalEdgeKey() {
        return originalEdgeKey;
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
    public IntsRef getFlags() {
        return edgeFlags;
    }

    @Override
    public EdgeIteratorState setFlags(IntsRef flags) {
        this.edgeFlags = flags;
        return this;
    }

    @Override
    public boolean get(BooleanEncodedValue property) {
        if (property == EdgeIteratorState.UNFAVORED_EDGE)
            return unfavored;

        return property.getBool(reverse, edgeFlags);
    }

    @Override
    public EdgeIteratorState set(BooleanEncodedValue property, boolean value) {
        property.setBool(reverse, edgeFlags, value);
        return this;
    }

    @Override
    public boolean getReverse(BooleanEncodedValue property) {
        if (property == EdgeIteratorState.UNFAVORED_EDGE)
            return unfavored;
        return property.getBool(!reverse, edgeFlags);
    }

    @Override
    public EdgeIteratorState setReverse(BooleanEncodedValue property, boolean value) {
        property.setBool(!reverse, edgeFlags, value);
        return this;
    }

    @Override
    public int get(IntEncodedValue property) {
        return property.getInt(reverse, edgeFlags);
    }

    @Override
    public EdgeIteratorState set(IntEncodedValue property, int value) {
        property.setInt(reverse, edgeFlags, value);
        return this;
    }

    @Override
    public int getReverse(IntEncodedValue property) {
        return property.getInt(!reverse, edgeFlags);
    }

    @Override
    public EdgeIteratorState setReverse(IntEncodedValue property, int value) {
        property.setInt(!reverse, edgeFlags, value);
        return this;
    }

    @Override
    public double get(DecimalEncodedValue property) {
        return property.getDecimal(reverse, edgeFlags);
    }

    @Override
    public EdgeIteratorState set(DecimalEncodedValue property, double value) {
        property.setDecimal(reverse, edgeFlags, value);
        return this;
    }

    @Override
    public double getReverse(DecimalEncodedValue property) {
        return property.getDecimal(!reverse, edgeFlags);
    }

    @Override
    public EdgeIteratorState setReverse(DecimalEncodedValue property, double value) {
        property.setDecimal(!reverse, edgeFlags, value);
        return this;
    }

    @Override
    public <T extends Enum> T get(EnumEncodedValue<T> property) {
        return property.getEnum(reverse, edgeFlags);
    }

    @Override
    public <T extends Enum> EdgeIteratorState set(EnumEncodedValue<T> property, T value) {
        property.setEnum(reverse, edgeFlags, value);
        return this;
    }

    @Override
    public <T extends Enum> T getReverse(EnumEncodedValue<T> property) {
        return property.getEnum(!reverse, edgeFlags);
    }

    @Override
    public <T extends Enum> EdgeIteratorState setReverse(EnumEncodedValue<T> property, T value) {
        property.setEnum(!reverse, edgeFlags, value);
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

    /**
     * This method sets edge to unfavored status for routing from the start or to the stop location.
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
    public int getAdditionalField() {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public int getMergeStatus(int flags) {
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
    public CHEdgeIteratorState setSkippedEdges(int edge1, int edge2) {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public CHEdgeIteratorState setFirstAndLastOrigEdges(int firstOrigEdge, int lastOrigEdge) {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public int getOrigEdgeFirst() {
        return getEdge();
    }

    @Override
    public int getOrigEdgeLast() {
        return getEdge();
    }

    @Override
    public EdgeIteratorState detach(boolean reverse) {
        if (reverse) {
            // update properties of reverse edge
            // TODO copy wayGeometry too
            reverseEdge.setFlags(getFlags());
            reverseEdge.setName(getName());
            reverseEdge.setDistance(getDistance());
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
    public EdgeIteratorState copyPropertiesFrom(EdgeIteratorState fromEdge) {
        throw new RuntimeException("Not supported.");
    }

    @Override
    public CHEdgeIteratorState setWeight(double weight) {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public void setFlagsAndWeight(int flags, double weight) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public double getWeight() {
        throw new UnsupportedOperationException("Not supported.");
    }

    public void setReverseEdge(EdgeIteratorState reverseEdge) {
        this.reverseEdge = reverseEdge;
    }

}
