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
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphExtension;
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
                IntStream.of(baseGraph.getNodes()-1),
                extraEdges.stream().flatMapToInt(edge -> IntStream.of(edge.getBaseNode(), edge.getAdjNode())))
                .max().getAsInt()+1;
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
            public int getMaxId() {
                return IntStream.concat(
                        IntStream.of(baseGraph.getAllEdges().getMaxId()),
                        extraEdges.stream().mapToInt(VirtualEdgeIteratorState::getEdge))
                        .max().getAsInt();
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
            public long getFlags() {
                throw new UnsupportedOperationException();
            }

            @Override
            public EdgeIteratorState setFlags(long flags) {
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
            public boolean isForward(FlagEncoder encoder) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isBackward(FlagEncoder encoder) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean getBool(int key, boolean _default) {
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
            public EdgeIteratorState copyPropertiesTo(EdgeIteratorState e) {
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
}
