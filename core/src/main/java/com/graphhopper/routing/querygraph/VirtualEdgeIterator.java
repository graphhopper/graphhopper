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
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.search.KVStorage;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.FetchMode;
import com.graphhopper.util.PointList;

import java.util.List;
import java.util.Map;

/**
 * @author Peter Karich
 */
public class VirtualEdgeIterator implements EdgeIterator {
    private final EdgeFilter edgeFilter;
    private List<EdgeIteratorState> edges;
    private int current;

    VirtualEdgeIterator(EdgeFilter edgeFilter, List<EdgeIteratorState> edges) {
        this.edges = edges;
        this.current = -1;
        this.edgeFilter = edgeFilter;
    }

    EdgeIterator reset(List<EdgeIteratorState> edges) {
        this.edges = edges;
        current = -1;
        return this;
    }

    @Override
    public boolean next() {
        current++;
        while (current < edges.size() && !edgeFilter.accept(edges.get(current))) {
            current++;
        }
        return current < edges.size();
    }

    @Override
    public EdgeIteratorState detach(boolean reverse) {
        if (reverse)
            throw new IllegalStateException("Not yet supported");
        return getCurrentEdge();
    }

    @Override
    public int getEdge() {
        return getCurrentEdge().getEdge();
    }

    @Override
    public int getEdgeKey() {
        return getCurrentEdge().getEdgeKey();
    }

    @Override
    public int getReverseEdgeKey() {
        return getCurrentEdge().getReverseEdgeKey();
    }

    @Override
    public int getBaseNode() {
        return getCurrentEdge().getBaseNode();
    }

    @Override
    public int getAdjNode() {
        return getCurrentEdge().getAdjNode();
    }

    @Override
    public PointList fetchWayGeometry(FetchMode mode) {
        return getCurrentEdge().fetchWayGeometry(mode);
    }

    @Override
    public EdgeIteratorState setWayGeometry(PointList list) {
        return getCurrentEdge().setWayGeometry(list);
    }

    @Override
    public double getDistance() {
        return getCurrentEdge().getDistance();
    }

    @Override
    public EdgeIteratorState setDistance(double dist) {
        return getCurrentEdge().setDistance(dist);
    }

    @Override
    public IntsRef getFlags() {
        return getCurrentEdge().getFlags();
    }

    @Override
    public EdgeIteratorState setFlags(IntsRef flags) {
        return getCurrentEdge().setFlags(flags);
    }

    @Override
    public EdgeIteratorState set(BooleanEncodedValue property, boolean value) {
        getCurrentEdge().set(property, value);
        return this;
    }

    @Override
    public boolean get(BooleanEncodedValue property) {
        return getCurrentEdge().get(property);
    }

    @Override
    public EdgeIteratorState setReverse(BooleanEncodedValue property, boolean value) {
        getCurrentEdge().setReverse(property, value);
        return this;
    }

    @Override
    public boolean getReverse(BooleanEncodedValue property) {
        return getCurrentEdge().getReverse(property);
    }

    @Override
    public EdgeIteratorState set(BooleanEncodedValue property, boolean fwd, boolean bwd) {
        getCurrentEdge().set(property, fwd, bwd);
        return this;
    }

    @Override
    public EdgeIteratorState set(IntEncodedValue property, int value) {
        getCurrentEdge().set(property, value);
        return this;
    }

    @Override
    public int get(IntEncodedValue property) {
        return getCurrentEdge().get(property);
    }

    @Override
    public EdgeIteratorState setReverse(IntEncodedValue property, int value) {
        getCurrentEdge().setReverse(property, value);
        return this;
    }

    @Override
    public int getReverse(IntEncodedValue property) {
        return getCurrentEdge().getReverse(property);
    }

    @Override
    public EdgeIteratorState set(IntEncodedValue property, int fwd, int bwd) {
        getCurrentEdge().set(property, fwd, bwd);
        return this;
    }

    @Override
    public EdgeIteratorState set(DecimalEncodedValue property, double value) {
        getCurrentEdge().set(property, value);
        return this;
    }

    @Override
    public double get(DecimalEncodedValue property) {
        return getCurrentEdge().get(property);
    }

    @Override
    public EdgeIteratorState setReverse(DecimalEncodedValue property, double value) {
        getCurrentEdge().setReverse(property, value);
        return this;
    }

    @Override
    public double getReverse(DecimalEncodedValue property) {
        return getCurrentEdge().getReverse(property);
    }

    @Override
    public EdgeIteratorState set(DecimalEncodedValue property, double fwd, double bwd) {
        getCurrentEdge().set(property, fwd, bwd);
        return this;
    }

    @Override
    public <T extends Enum<?>> EdgeIteratorState set(EnumEncodedValue<T> property, T value) {
        getCurrentEdge().set(property, value);
        return this;
    }

    @Override
    public <T extends Enum<?>> T get(EnumEncodedValue<T> property) {
        return getCurrentEdge().get(property);
    }

    @Override
    public <T extends Enum<?>> EdgeIteratorState setReverse(EnumEncodedValue<T> property, T value) {
        getCurrentEdge().setReverse(property, value);
        return this;
    }

    @Override
    public <T extends Enum<?>> T getReverse(EnumEncodedValue<T> property) {
        return getCurrentEdge().getReverse(property);
    }

    @Override
    public <T extends Enum<?>> EdgeIteratorState set(EnumEncodedValue<T> property, T fwd, T bwd) {
        getCurrentEdge().set(property, fwd, bwd);
        return this;
    }

    @Override
    public String get(StringEncodedValue property) {
        return getCurrentEdge().get(property);
    }

    @Override
    public EdgeIteratorState set(StringEncodedValue property, String value) {
        return getCurrentEdge().set(property, value);
    }

    @Override
    public String getReverse(StringEncodedValue property) {
        return getCurrentEdge().getReverse(property);
    }

    @Override
    public EdgeIteratorState setReverse(StringEncodedValue property, String value) {
        return getCurrentEdge().setReverse(property, value);
    }

    @Override
    public EdgeIteratorState set(StringEncodedValue property, String fwd, String bwd) {
        return getCurrentEdge().set(property, fwd, bwd);
    }

    @Override
    public String getName() {
        return getCurrentEdge().getName();
    }

    @Override
    public Map<String, KVStorage.KValue> getKeyValues() {
        return getCurrentEdge().getKeyValues();
    }

    @Override
    public EdgeIteratorState setKeyValues(Map<String, KVStorage.KValue> list) {
        return getCurrentEdge().setKeyValues(list);
    }

    @Override
    public Object getValue(String key) {
        return getCurrentEdge().getValue(key);
    }

    @Override
    public boolean isVirtual() {
        return getCurrentEdge().isVirtual();
    }

    @Override
    public String toString() {
        if (current >= 0 && current < edges.size()) {
            return "virtual edge: " + getCurrentEdge() + ", all: " + edges.toString();
        } else {
            return "virtual edge: (invalid)" + ", all: " + edges.toString();
        }
    }

    @Override
    public EdgeIteratorState copyPropertiesFrom(EdgeIteratorState edge) {
        return getCurrentEdge().copyPropertiesFrom(edge);
    }

    private EdgeIteratorState getCurrentEdge() {
        return edges.get(current);
    }

    public List<EdgeIteratorState> getEdges() {
        return edges;
    }

}
