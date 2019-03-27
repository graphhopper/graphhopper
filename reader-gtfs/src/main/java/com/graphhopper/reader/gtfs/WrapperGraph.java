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

package com.graphhopper.reader.gtfs;

import com.graphhopper.routing.VirtualEdgeIteratorState;
import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.profiles.EnumEncodedValue;
import com.graphhopper.routing.profiles.IntEncodedValue;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphExtension;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.BBox;

import java.util.List;
import java.util.stream.IntStream;

public class WrapperGraph implements Graph {

    private final Graph baseGraph;
    private final List<VirtualEdgeIteratorState> extraEdges;

    public WrapperGraph(Graph baseGraph, List<VirtualEdgeIteratorState> extraEdges) {
        this.baseGraph = baseGraph;
        this.extraEdges = extraEdges;
    }

    @Override
    public Graph getBaseGraph() {
        return baseGraph;
    }

    @Override
    public int getNodes() {
        return IntStream.concat(
                IntStream.of(baseGraph.getNodes() - 1),
                extraEdges.stream().flatMapToInt(edge -> IntStream.of(edge.getBaseNode(), edge.getAdjNode())))
                .max().getAsInt() + 1;
    }

    @Override
    public int getEdges() {
        return getAllEdges().length();
    }

    @Override
    public NodeAccess getNodeAccess() {
        return baseGraph.getNodeAccess();
    }

    @Override
    public BBox getBounds() {
        return baseGraph.getBounds();
    }

    @Override
    public EdgeIteratorState edge(int a, int b) {
        return baseGraph.getEdgeIteratorState(a, b);
    }

    @Override
    public EdgeIteratorState edge(int a, int b, double distance, boolean bothDirections) {
        throw new RuntimeException();
    }

    @Override
    public EdgeIteratorState getEdgeIteratorState(int edgeId, int adjNode) {
        return baseGraph.getEdgeIteratorState(edgeId, adjNode);
    }

    @Override
    public AllEdgesIterator getAllEdges() {
        return new AllEdgesIterator() {
            @Override
            public int length() {
                return IntStream.concat(
                        IntStream.of(baseGraph.getAllEdges().length() - 1),
                        extraEdges.stream().mapToInt(VirtualEdgeIteratorState::getEdge))
                        .max().getAsInt() + 1;
            }

            @Override
            public boolean next() {
                throw new UnsupportedOperationException();
            }

            @Override
            public int getEdge() {
                throw new UnsupportedOperationException();
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
            public int getBaseNode() {
                throw new UnsupportedOperationException();
            }

            @Override
            public int getAdjNode() {
                throw new UnsupportedOperationException();
            }

            @Override
            public PointList fetchWayGeometry(int mode) {
                throw new UnsupportedOperationException();
            }

            @Override
            public EdgeIteratorState setWayGeometry(PointList list) {
                throw new UnsupportedOperationException();
            }

            @Override
            public double getDistance() {
                throw new UnsupportedOperationException();
            }

            @Override
            public EdgeIteratorState setDistance(double dist) {
                throw new UnsupportedOperationException();
            }

            @Override
            public IntsRef getFlags() {
                throw new UnsupportedOperationException();
            }

            @Override
            public EdgeIteratorState setFlags(IntsRef flags) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int getAdditionalField() {
                throw new UnsupportedOperationException();
            }

            @Override
            public EdgeIteratorState setAdditionalField(int value) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean get(BooleanEncodedValue property) {
                throw new UnsupportedOperationException();
            }

            @Override
            public EdgeIteratorState set(BooleanEncodedValue property, boolean value) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean getReverse(BooleanEncodedValue property) {
                throw new UnsupportedOperationException();
            }

            @Override
            public EdgeIteratorState setReverse(BooleanEncodedValue property, boolean value) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int get(IntEncodedValue property) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int getReverse(IntEncodedValue property) {
                throw new UnsupportedOperationException();
            }

            @Override
            public EdgeIteratorState set(IntEncodedValue property, int value) {
                throw new UnsupportedOperationException();
            }

            @Override
            public EdgeIteratorState setReverse(IntEncodedValue property, int value) {
                throw new UnsupportedOperationException();
            }

            @Override
            public double get(DecimalEncodedValue property) {
                throw new UnsupportedOperationException();
            }

            @Override
            public double getReverse(DecimalEncodedValue property) {
                throw new UnsupportedOperationException();
            }

            @Override
            public EdgeIteratorState set(DecimalEncodedValue property, double value) {
                throw new UnsupportedOperationException();
            }

            @Override
            public EdgeIteratorState setReverse(DecimalEncodedValue property, double value) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T extends Enum> T get(EnumEncodedValue<T> property) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T extends Enum> T getReverse(EnumEncodedValue<T> property) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T extends Enum> EdgeIteratorState set(EnumEncodedValue<T> property, T value) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T extends Enum> EdgeIteratorState setReverse(EnumEncodedValue<T> property, T value) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getName() {
                throw new UnsupportedOperationException();
            }

            @Override
            public EdgeIteratorState setName(String name) {
                throw new UnsupportedOperationException();
            }

            @Override
            public EdgeIteratorState detach(boolean reverse) {
                throw new UnsupportedOperationException();
            }

            @Override
            public EdgeIteratorState copyPropertiesFrom(EdgeIteratorState e) {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Override
    public EdgeExplorer createEdgeExplorer(EdgeFilter filter) {
        return baseGraph.createEdgeExplorer(filter);
    }

    @Override
    public EdgeExplorer createEdgeExplorer() {
        return baseGraph.createEdgeExplorer();
    }

    @Override
    public Graph copyTo(Graph g) {
        throw new RuntimeException();
    }

    @Override
    public GraphExtension getExtension() {
        return baseGraph.getExtension();
    }

    @Override
    public int getOtherNode(int edge, int node) {
        return baseGraph.getOtherNode(edge, node);
    }
}
