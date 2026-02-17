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
package com.graphhopper.routing.querygraph;

import com.graphhopper.routing.ev.*;
import com.graphhopper.search.KVStorage;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.FetchMode;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.PointList;

import java.util.Map;

import static com.graphhopper.storage.BaseGraph.MAX_DIST_METERS;
import static com.graphhopper.util.Parameters.Details.STREET_NAME;

/**
 * Creates an edge state decoupled from a graph where nodes, pointList, etc are kept in memory.
 * <p>
 * Note, this class is not suited for public use and can change with minor releases unexpectedly or
 * even gets removed.
 */
public class VirtualEdgeIteratorState implements EdgeIteratorState {
    private final PointList pointList;
    private final int edgeKey;
    private final int baseNode;
    private final int adjNode;
    private final int originalEdgeKey;
    private long distance_mm;
    private IntsRef edgeFlags;
    private EdgeIntAccess edgeIntAccess;
    private Map<String, KVStorage.KValue> keyValues;
    // true if edge should be avoided as start/stop
    private boolean unfavored;
    private EdgeIteratorState reverseEdge;
    private final boolean reverse;

    public VirtualEdgeIteratorState(int originalEdgeKey, int edgeKey, int baseNode, int adjNode, double distance,
                                    IntsRef edgeFlags, Map<String, KVStorage.KValue> keyValues, PointList pointList, boolean reverse) {
        this.originalEdgeKey = originalEdgeKey;
        this.edgeKey = edgeKey;
        this.baseNode = baseNode;
        this.adjNode = adjNode;
        if (distance < 0)
            throw new IllegalArgumentException("distances must be non-negative, got: " + distance);
        if (distance > MAX_DIST_METERS)
            distance = MAX_DIST_METERS;
        this.distance_mm = Math.round(distance * 1000);
        this.edgeFlags = edgeFlags;
        this.edgeIntAccess = new IntsRefEdgeIntAccess(edgeFlags);
        this.keyValues = keyValues;
        this.pointList = pointList;
        this.reverse = reverse;
    }

    /**
     * This method returns the original (not virtual!) edge key. I.e. also the direction is
     * already correctly encoded.
     *
     * @see EdgeIteratorState#getEdgeKey()
     */
    public int getOriginalEdgeKey() {
        return originalEdgeKey;
    }

    @Override
    public int getEdge() {
        return GHUtility.getEdgeFromEdgeKey(edgeKey);
    }

    @Override
    public int getEdgeKey() {
        return edgeKey;
    }

    @Override
    public int getReverseEdgeKey() {
        return GHUtility.reverseEdgeKey(edgeKey);
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
    public PointList fetchWayGeometry(FetchMode mode) {
        if (pointList.isEmpty())
            return PointList.EMPTY;
        // due to API we need to create a new instance per call!
        if (mode == FetchMode.TOWER_ONLY) {
            if (pointList.size() < 3)
                return pointList.clone(false);
            PointList towerNodes = new PointList(2, pointList.is3D());
            towerNodes.add(pointList, 0);
            towerNodes.add(pointList, pointList.size() - 1);
            return towerNodes;
        } else if (mode == FetchMode.ALL)
            return pointList.clone(false);
        else if (mode == FetchMode.BASE_AND_PILLAR)
            return pointList.copy(0, pointList.size() - 1);
        else if (mode == FetchMode.PILLAR_AND_ADJ)
            return pointList.copy(1, pointList.size());
        else if (mode == FetchMode.PILLAR_ONLY) {
            if (pointList.size() == 1)
                return PointList.EMPTY;
            return pointList.copy(1, pointList.size() - 1);
        }
        throw new UnsupportedOperationException("Illegal mode:" + mode);
    }

    @Override
    public EdgeIteratorState setWayGeometry(PointList list) {
        throw new UnsupportedOperationException("Not supported for virtual edge. Set when creating it.");
    }

    @Override
    public double getDistance() {
        return distance_mm * 1000.0;
    }

    @Override
    public EdgeIteratorState setDistance(double distance) {
        this.distance_mm = Math.round(distance * 1000.0);
        return this;
    }

    @Override
    public long getDistance_mm() {
        return distance_mm;
    }

    @Override
    public EdgeIteratorState setDistance_mm(long distance_mm) {
        this.distance_mm = distance_mm;
        return this;
    }

    @Override
    public IntsRef getFlags() {
        return edgeFlags;
    }

    @Override
    public EdgeIteratorState setFlags(IntsRef flags) {
        this.edgeFlags = flags;
        this.edgeIntAccess = new IntsRefEdgeIntAccess(flags);
        return this;
    }

    @Override
    public boolean get(BooleanEncodedValue property) {
        if (property == EdgeIteratorState.UNFAVORED_EDGE)
            return unfavored;

        return property.getBool(reverse, GHUtility.getEdgeFromEdgeKey(originalEdgeKey), edgeIntAccess);
    }

    @Override
    public EdgeIteratorState set(BooleanEncodedValue property, boolean value) {
        property.setBool(reverse, GHUtility.getEdgeFromEdgeKey(originalEdgeKey), edgeIntAccess, value);
        return this;
    }

    @Override
    public boolean getReverse(BooleanEncodedValue property) {
        if (property == EdgeIteratorState.UNFAVORED_EDGE)
            return unfavored;
        return property.getBool(!reverse, GHUtility.getEdgeFromEdgeKey(originalEdgeKey), edgeIntAccess);
    }

    @Override
    public EdgeIteratorState setReverse(BooleanEncodedValue property, boolean value) {
        property.setBool(!reverse, GHUtility.getEdgeFromEdgeKey(originalEdgeKey), edgeIntAccess, value);
        return this;
    }

    @Override
    public EdgeIteratorState set(BooleanEncodedValue property, boolean fwd, boolean bwd) {
        if (!property.isStoreTwoDirections())
            throw new IllegalArgumentException("EncodedValue " + property.getName() + " supports only one direction");
        property.setBool(reverse, GHUtility.getEdgeFromEdgeKey(originalEdgeKey), edgeIntAccess, fwd);
        property.setBool(!reverse, GHUtility.getEdgeFromEdgeKey(originalEdgeKey), edgeIntAccess, bwd);
        return this;
    }

    @Override
    public int get(IntEncodedValue property) {
        return property.getInt(reverse, GHUtility.getEdgeFromEdgeKey(originalEdgeKey), edgeIntAccess);
    }

    @Override
    public EdgeIteratorState set(IntEncodedValue property, int value) {
        property.setInt(reverse, GHUtility.getEdgeFromEdgeKey(originalEdgeKey), edgeIntAccess, value);
        return this;
    }

    @Override
    public int getReverse(IntEncodedValue property) {
        return property.getInt(!reverse, GHUtility.getEdgeFromEdgeKey(originalEdgeKey), edgeIntAccess);
    }

    @Override
    public EdgeIteratorState setReverse(IntEncodedValue property, int value) {
        property.setInt(!reverse, GHUtility.getEdgeFromEdgeKey(originalEdgeKey), edgeIntAccess, value);
        return this;
    }

    @Override
    public EdgeIteratorState set(IntEncodedValue property, int fwd, int bwd) {
        if (!property.isStoreTwoDirections())
            throw new IllegalArgumentException("EncodedValue " + property.getName() + " supports only one direction");
        property.setInt(reverse, GHUtility.getEdgeFromEdgeKey(originalEdgeKey), edgeIntAccess, fwd);
        property.setInt(!reverse, GHUtility.getEdgeFromEdgeKey(originalEdgeKey), edgeIntAccess, bwd);
        return this;
    }

    @Override
    public double get(DecimalEncodedValue property) {
        return property.getDecimal(reverse, GHUtility.getEdgeFromEdgeKey(originalEdgeKey), edgeIntAccess);
    }

    @Override
    public EdgeIteratorState set(DecimalEncodedValue property, double value) {
        property.setDecimal(reverse, GHUtility.getEdgeFromEdgeKey(originalEdgeKey), edgeIntAccess, value);
        return this;
    }

    @Override
    public double getReverse(DecimalEncodedValue property) {
        return property.getDecimal(!reverse, GHUtility.getEdgeFromEdgeKey(originalEdgeKey), edgeIntAccess);
    }

    @Override
    public EdgeIteratorState setReverse(DecimalEncodedValue property, double value) {
        property.setDecimal(!reverse, GHUtility.getEdgeFromEdgeKey(originalEdgeKey), edgeIntAccess, value);
        return this;
    }

    @Override
    public EdgeIteratorState set(DecimalEncodedValue property, double fwd, double bwd) {
        if (!property.isStoreTwoDirections())
            throw new IllegalArgumentException("EncodedValue " + property.getName() + " supports only one direction");
        property.setDecimal(reverse, GHUtility.getEdgeFromEdgeKey(originalEdgeKey), edgeIntAccess, fwd);
        property.setDecimal(!reverse, GHUtility.getEdgeFromEdgeKey(originalEdgeKey), edgeIntAccess, bwd);
        return this;
    }

    @Override
    public <T extends Enum<?>> T get(EnumEncodedValue<T> property) {
        return property.getEnum(reverse, GHUtility.getEdgeFromEdgeKey(originalEdgeKey), edgeIntAccess);
    }

    @Override
    public <T extends Enum<?>> EdgeIteratorState set(EnumEncodedValue<T> property, T value) {
        property.setEnum(reverse, GHUtility.getEdgeFromEdgeKey(originalEdgeKey), edgeIntAccess, value);
        return this;
    }

    @Override
    public <T extends Enum<?>> T getReverse(EnumEncodedValue<T> property) {
        return property.getEnum(!reverse, GHUtility.getEdgeFromEdgeKey(originalEdgeKey), edgeIntAccess);
    }

    @Override
    public <T extends Enum<?>> EdgeIteratorState setReverse(EnumEncodedValue<T> property, T value) {
        property.setEnum(!reverse, GHUtility.getEdgeFromEdgeKey(originalEdgeKey), edgeIntAccess, value);
        return this;
    }

    @Override
    public <T extends Enum<?>> EdgeIteratorState set(EnumEncodedValue<T> property, T fwd, T bwd) {
        if (!property.isStoreTwoDirections())
            throw new IllegalArgumentException("EncodedValue " + property.getName() + " supports only one direction");
        property.setEnum(reverse, GHUtility.getEdgeFromEdgeKey(originalEdgeKey), edgeIntAccess, fwd);
        property.setEnum(!reverse, GHUtility.getEdgeFromEdgeKey(originalEdgeKey), edgeIntAccess, bwd);
        return this;
    }

    @Override
    public String get(StringEncodedValue property) {
        return property.getString(reverse, GHUtility.getEdgeFromEdgeKey(originalEdgeKey), edgeIntAccess);
    }

    @Override
    public EdgeIteratorState set(StringEncodedValue property, String value) {
        property.setString(reverse, GHUtility.getEdgeFromEdgeKey(originalEdgeKey), edgeIntAccess, value);
        return this;
    }

    @Override
    public String getReverse(StringEncodedValue property) {
        return property.getString(!reverse, GHUtility.getEdgeFromEdgeKey(originalEdgeKey), edgeIntAccess);
    }

    @Override
    public EdgeIteratorState setReverse(StringEncodedValue property, String value) {
        property.setString(!reverse, GHUtility.getEdgeFromEdgeKey(originalEdgeKey), edgeIntAccess, value);
        return this;
    }

    @Override
    public EdgeIteratorState set(StringEncodedValue property, String fwd, String bwd) {
        if (!property.isStoreTwoDirections())
            throw new IllegalArgumentException("EncodedValue " + property.getName() + " supports only one direction");
        property.setString(reverse, GHUtility.getEdgeFromEdgeKey(originalEdgeKey), edgeIntAccess, fwd);
        property.setString(!reverse, GHUtility.getEdgeFromEdgeKey(originalEdgeKey), edgeIntAccess, bwd);
        return this;
    }

    @Override
    public String getName() {
        String name = (String) getValue(STREET_NAME);
        // preserve backward compatibility (returns empty string if name tag missing)
        return name == null ? "" : name;
    }

    @Override
    public EdgeIteratorState setKeyValues(Map<String, KVStorage.KValue> list) {
        this.keyValues = list;
        return this;
    }

    @Override
    public Map<String, KVStorage.KValue> getKeyValues() {
        return keyValues;
    }

    @Override
    public Object getValue(String key) {
        KVStorage.KValue value = keyValues.get(key);
        if (value != null) {
            if (!reverse && value.getFwd() != null) return value.getFwd();
            if (reverse && value.getBwd() != null) return value.getBwd();
        }
        return null;
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
    public EdgeIteratorState detach(boolean reverse) {
        if (reverse) {
            // update properties of reverse edge
            // TODO copy pointList (geometry) too
            reverseEdge.setFlags(getFlags());
            reverseEdge.setKeyValues(getKeyValues());
            reverseEdge.setDistance_mm(getDistance_mm());
            return reverseEdge;
        } else {
            return this;
        }
    }

    @Override
    public EdgeIteratorState copyPropertiesFrom(EdgeIteratorState fromEdge) {
        throw new RuntimeException("Not supported.");
    }

    public void setReverseEdge(EdgeIteratorState reverseEdge) {
        this.reverseEdge = reverseEdge;
    }

}
