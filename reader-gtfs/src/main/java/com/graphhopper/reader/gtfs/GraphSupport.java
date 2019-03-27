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

import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.profiles.EnumEncodedValue;
import com.graphhopper.routing.profiles.IntEncodedValue;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.*;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.BBox;

class GraphSupport {

    private GraphSupport() {
    }


    /**
     * Creates a read-only view of a Graph, presenting the sub-graph consisting of all the nodes; and those
     * edges accepted by edgeFilter.
     * <p>
     * Devised to pass into LocationIndexTree, and works fine there (because LocationIndexTree doesn't index nodes
     * directly, but only goes through the edges). Probably not useful in other contexts.
     *
     * @param baseGraph  The graph to construct a view for.
     * @param edgeFilter The filter to filter with.
     * @return The filtered view.
     */
    static Graph filteredView(GraphHopperStorage baseGraph, EdgeFilter edgeFilter) {
        return new Graph() {
            @Override
            public Graph getBaseGraph() {
                return baseGraph;
            }

            @Override
            public int getNodes() {
                return baseGraph.getNodes();
            }

            @Override
            public int getEdges() {
                return baseGraph.getEdges();
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
                throw new UnsupportedOperationException();
            }

            @Override
            public EdgeIteratorState edge(int a, int b, double distance, boolean bothDirections) {
                throw new UnsupportedOperationException();
            }

            @Override
            public EdgeIteratorState getEdgeIteratorState(int edgeId, int adjNode) {
                throw new UnsupportedOperationException();
            }

            @Override
            public AllEdgesIterator getAllEdges() {
                final AllEdgesIterator edge = baseGraph.getAllEdges();
                return new AllEdgesIterator() {
                    @Override
                    public int length() {
                        return edge.length();
                    }

                    @Override
                    public boolean next() {
                        while (edge.next()) {
                            if (edgeFilter.accept(edge)) {
                                return true;
                            }
                        }
                        return false;
                    }

                    @Override
                    public int getEdge() {
                        return edge.getEdge();
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
                        return edge.getBaseNode();
                    }

                    @Override
                    public int getAdjNode() {
                        return edge.getAdjNode();
                    }

                    @Override
                    public PointList fetchWayGeometry(int mode) {
                        return edge.fetchWayGeometry(mode);
                    }

                    @Override
                    public EdgeIteratorState setWayGeometry(PointList list) {
                        edge.setWayGeometry(list);
                        return this;
                    }

                    @Override
                    public double getDistance() {
                        return edge.getDistance();
                    }

                    @Override
                    public EdgeIteratorState setDistance(double dist) {
                        edge.setDistance(dist);
                        return this;
                    }

                    @Override
                    public IntsRef getFlags() {
                        return edge.getFlags();
                    }

                    @Override
                    public EdgeIteratorState setFlags(IntsRef flags) {
                        edge.setFlags(flags);
                        return this;
                    }

                    @Override
                    public int getAdditionalField() {
                        return edge.getAdditionalField();
                    }

                    @Override
                    public EdgeIteratorState setAdditionalField(int value) {
                        edge.setAdditionalField(value);
                        return this;
                    }

                    @Override
                    public String getName() {
                        return edge.getName();
                    }

                    @Override
                    public EdgeIteratorState setName(String name) {
                        edge.setName(name);
                        return this;
                    }

                    @Override
                    public EdgeIteratorState detach(boolean reverse) {
                        return edge.detach(reverse);
                    }

                    @Override
                    public boolean get(BooleanEncodedValue property) {
                        return edge.get(property);
                    }

                    @Override
                    public EdgeIteratorState set(BooleanEncodedValue property, boolean value) {
                        edge.set(property, value);
                        return this;
                    }

                    @Override
                    public boolean getReverse(BooleanEncodedValue property) {
                        return edge.getReverse(property);
                    }

                    @Override
                    public EdgeIteratorState setReverse(BooleanEncodedValue property, boolean value) {
                        edge.setReverse(property, value);
                        return this;
                    }

                    @Override
                    public int get(IntEncodedValue property) {
                        return edge.get(property);
                    }

                    @Override
                    public EdgeIteratorState set(IntEncodedValue property, int value) {
                        edge.set(property, value);
                        return this;
                    }

                    @Override
                    public int getReverse(IntEncodedValue property) {
                        return edge.getReverse(property);
                    }

                    @Override
                    public EdgeIteratorState setReverse(IntEncodedValue property, int value) {
                        edge.setReverse(property, value);
                        return this;
                    }

                    @Override
                    public double get(DecimalEncodedValue property) {
                        return edge.get(property);
                    }

                    @Override
                    public EdgeIteratorState set(DecimalEncodedValue property, double value) {
                        edge.set(property, value);
                        return this;
                    }

                    @Override
                    public double getReverse(DecimalEncodedValue property) {
                        return edge.getReverse(property);
                    }

                    @Override
                    public EdgeIteratorState setReverse(DecimalEncodedValue property, double value) {
                        edge.setReverse(property, value);
                        return this;
                    }

                    @Override
                    public <T extends Enum> T get(EnumEncodedValue<T> property) {
                        return edge.get(property);
                    }

                    @Override
                    public <T extends Enum> EdgeIteratorState set(EnumEncodedValue<T> property, T value) {
                        edge.set(property, value);
                        return this;
                    }

                    @Override
                    public <T extends Enum> T getReverse(EnumEncodedValue<T> property) {
                        return edge.getReverse(property);
                    }

                    @Override
                    public <T extends Enum> EdgeIteratorState setReverse(EnumEncodedValue<T> property, T value) {
                        edge.setReverse(property, value);
                        return this;
                    }

                    @Override
                    public EdgeIteratorState copyPropertiesFrom(EdgeIteratorState e) {
                        throw new UnsupportedOperationException();
                    }
                };
            }

            @Override
            public EdgeExplorer createEdgeExplorer(EdgeFilter filter) {
                throw new UnsupportedOperationException();
            }

            @Override
            public EdgeExplorer createEdgeExplorer() {
                return baseGraph.createEdgeExplorer(edgeFilter);
            }

            @Override
            public Graph copyTo(Graph g) {
                throw new UnsupportedOperationException();
            }

            @Override
            public GraphExtension getExtension() {
                throw new UnsupportedOperationException();
            }

            @Override
            public int getOtherNode(int edge, int node) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
