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
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PointList;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Peter Karich
 */
class VirtualEdgeIterator implements EdgeIterator, CHEdgeIteratorState {
    private final List<EdgeIteratorState> edges;
    private int current;

    public VirtualEdgeIterator(int edgeCount) {
        edges = new ArrayList<>(edgeCount);
        reset();
    }

    void add(EdgeIteratorState edge) {
        edges.add(edge);
    }

    EdgeIterator reset() {
        current = -1;
        return this;
    }

    int count() {
        return edges.size();
    }

    @Override
    public boolean next() {
        current++;
        return current < edges.size();
    }

    @Override
    public EdgeIteratorState detach(boolean reverse) {
        if (reverse)
            throw new IllegalStateException("Not yet supported");
        return edges.get(current);
    }

    @Override
    public int getEdge() {
        return edges.get(current).getEdge();
    }

    @Override
    public int getBaseNode() {
        return edges.get(current).getBaseNode();
    }

    @Override
    public int getAdjNode() {
        return edges.get(current).getAdjNode();
    }

    @Override
    public PointList fetchWayGeometry(int mode) {
        return edges.get(current).fetchWayGeometry(mode);
    }

    @Override
    public EdgeIteratorState setWayGeometry(PointList list) {
        return edges.get(current).setWayGeometry(list);
    }

    @Override
    public double getDistance() {
        return edges.get(current).getDistance();
    }

    @Override
    public EdgeIteratorState setDistance(double dist) {
        return edges.get(current).setDistance(dist);
    }

    @Override
    public IntsRef getFlags() {
        return edges.get(current).getFlags();
    }

    @Override
    public EdgeIteratorState setFlags(IntsRef flags) {
        return edges.get(current).setFlags(flags);
    }

    @Override
    public EdgeIteratorState set(BooleanEncodedValue property, boolean value) {
        edges.get(current).set(property, value);
        return this;
    }

    @Override
    public boolean get(BooleanEncodedValue property) {
        return edges.get(current).get(property);
    }

    @Override
    public EdgeIteratorState setReverse(BooleanEncodedValue property, boolean value) {
        edges.get(current).setReverse(property, value);
        return this;
    }

    @Override
    public boolean getReverse(BooleanEncodedValue property) {
        return edges.get(current).getReverse(property);
    }

    @Override
    public EdgeIteratorState set(IntEncodedValue property, int value) {
        edges.get(current).set(property, value);
        return this;
    }

    @Override
    public int get(IntEncodedValue property) {
        return edges.get(current).get(property);
    }

    @Override
    public EdgeIteratorState setReverse(IntEncodedValue property, int value) {
        edges.get(current).setReverse(property, value);
        return this;
    }

    @Override
    public int getReverse(IntEncodedValue property) {
        return edges.get(current).getReverse(property);
    }

    @Override
    public EdgeIteratorState set(DecimalEncodedValue property, double value) {
        edges.get(current).set(property, value);
        return this;
    }

    @Override
    public double get(DecimalEncodedValue property) {
        return edges.get(current).get(property);
    }

    @Override
    public EdgeIteratorState setReverse(DecimalEncodedValue property, double value) {
        edges.get(current).setReverse(property, value);
        return this;
    }

    @Override
    public double getReverse(DecimalEncodedValue property) {
        return edges.get(current).getReverse(property);
    }

    @Override
    public <T extends Enum> EdgeIteratorState set(EnumEncodedValue<T> property, T value) {
        edges.get(current).set(property, value);
        return this;
    }

    @Override
    public <T extends Enum> T get(EnumEncodedValue<T> property) {
        return edges.get(current).get(property);
    }

    @Override
    public <T extends Enum> EdgeIteratorState setReverse(EnumEncodedValue<T> property, T value) {
        edges.get(current).setReverse(property, value);
        return this;
    }

    @Override
    public <T extends Enum> T getReverse(EnumEncodedValue<T> property) {
        return edges.get(current).getReverse(property);
    }

    @Override
    public String getName() {
        return edges.get(current).getName();
    }

    @Override
    public EdgeIteratorState setName(String name) {
        return edges.get(current).setName(name);
    }

    @Override
    public String toString() {
        return edges.toString();
    }

    @Override
    public int getAdditionalField() {
        return edges.get(current).getAdditionalField();
    }

    @Override
    public EdgeIteratorState setAdditionalField(int value) {
        return edges.get(current).setAdditionalField(value);
    }

    @Override
    public EdgeIteratorState copyPropertiesFrom(EdgeIteratorState edge) {
        return edges.get(current).copyPropertiesFrom(edge);
    }

    @Override
    public boolean isShortcut() {
        EdgeIteratorState edge = edges.get(current);
        return edge instanceof CHEdgeIteratorState && ((CHEdgeIteratorState) edge).isShortcut();
    }

    @Override
    public double getWeight() {
        // will be called only from PreparationWeighting and if isShortcut is true
        return ((CHEdgeIteratorState) edges.get(current)).getWeight();
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
        return edges.get(current).getOrigEdgeFirst();
    }

    @Override
    public int getOrigEdgeLast() {
        return edges.get(current).getOrigEdgeLast();
    }

    @Override
    public int getMergeStatus(int flags) {
        throw new UnsupportedOperationException("Not supported.");
    }
}
