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

package com.graphhopper.gtfs;

import com.carrotsearch.hppc.IntObjectHashMap;
import com.carrotsearch.hppc.IntObjectMap;
import com.google.common.collect.ArrayListMultimap;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.IntEncodedValue;
import com.graphhopper.routing.ev.StringEncodedValue;
import com.graphhopper.routing.querygraph.VirtualEdgeIteratorState;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.TurnCostStorage;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.BBox;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

public class WrapperGraph implements Graph {

    private final Graph mainGraph;
    private final IntObjectMap<EdgeIteratorState> extraEdges = new IntObjectHashMap<>();
    private final ArrayListMultimap<Integer, VirtualEdgeIteratorState> extraEdgesBySource = ArrayListMultimap.create();
    private final ArrayListMultimap<Integer, VirtualEdgeIteratorState> extraEdgesByDestination = ArrayListMultimap.create();


    public WrapperGraph(Graph mainGraph, List<VirtualEdgeIteratorState> extraEdges) {
        this.mainGraph = mainGraph;
        extraEdges.forEach(e -> this.extraEdges.put(e.getEdge(), e));
        for (VirtualEdgeIteratorState extraEdge : extraEdges) {
            if (extraEdge == null) {
                throw new RuntimeException();
            }
            extraEdgesBySource.put(extraEdge.getBaseNode(), extraEdge);
            extraEdgesByDestination.put(extraEdge.getAdjNode(), new VirtualEdgeIteratorState(extraEdge.getOriginalEdgeKey(), extraEdge.getEdgeKey(), extraEdge.getAdjNode(),
                    extraEdge.getBaseNode(), extraEdge.getDistance(), extraEdge.getFlags(), extraEdge.getName(), extraEdge.fetchWayGeometry(FetchMode.ALL), true));
        }
    }

    @Override
    public Graph getBaseGraph() {
        return this;
    }

    @Override
    public int getNodes() {
        return IntStream.concat(
                IntStream.of(mainGraph.getNodes() - 1),
                StreamSupport.stream(extraEdges.values().spliterator(), false)
                        .flatMapToInt(cursor -> IntStream.of(cursor.value.getBaseNode(), cursor.value.getAdjNode()))
        ).max().getAsInt() + 1;
    }

    @Override
    public int getEdges() {
        return getAllEdges().length();
    }

    @Override
    public NodeAccess getNodeAccess() {
        return mainGraph.getNodeAccess();
    }

    @Override
    public BBox getBounds() {
        return mainGraph.getBounds();
    }

    @Override
    public EdgeIteratorState edge(int a, int b) {
        throw new RuntimeException();
    }

    @Override
    public EdgeIteratorState getEdgeIteratorState(int edgeId, int adjNode) {
        EdgeIteratorState edgeIteratorState = extraEdges.get(edgeId);
        if (edgeIteratorState != null) {
            return edgeIteratorState;
        } else {
            return mainGraph.getEdgeIteratorState(edgeId, adjNode);
        }
    }

    @Override
    public EdgeIteratorState getEdgeIteratorStateForKey(int edgeKey) {
        throw new RuntimeException("not implemented yet");
    }

    @Override
    public AllEdgesIterator getAllEdges() {
        return new AllEdgesIterator() {
            @Override
            public int length() {
                return IntStream.concat(
                        IntStream.of(mainGraph.getAllEdges().length() - 1),
                        StreamSupport.stream(extraEdges.values().spliterator(), false).mapToInt(cursor -> cursor.value.getEdge()))
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
            public int getEdgeKey() {
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
            public PointList fetchWayGeometry(FetchMode mode) {
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
            public EdgeIteratorState set(BooleanEncodedValue property, boolean fwd, boolean bwd) {
                return this;
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
            public EdgeIteratorState set(IntEncodedValue property, int fwd, int bwd) {
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
            public EdgeIteratorState set(DecimalEncodedValue property, double fwd, double bwd) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T extends Enum<?>> T get(EnumEncodedValue<T> property) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T extends Enum<?>> T getReverse(EnumEncodedValue<T> property) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T extends Enum<?>> EdgeIteratorState set(EnumEncodedValue<T> property, T value) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T extends Enum<?>> EdgeIteratorState setReverse(EnumEncodedValue<T> property, T value) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T extends Enum<?>> EdgeIteratorState set(EnumEncodedValue<T> property, T fwd, T bwd) {
                throw new UnsupportedOperationException();
            }
            
            @Override
            public String get(StringEncodedValue property) {
                throw new UnsupportedOperationException();
            }
            
            @Override
            public EdgeIteratorState set(StringEncodedValue property, String value) {
                throw new UnsupportedOperationException();
            }
            
            @Override
            public String getReverse(StringEncodedValue property) {
                throw new UnsupportedOperationException();
            }
            
            @Override
            public EdgeIteratorState setReverse(StringEncodedValue property, String value) {
                throw new UnsupportedOperationException();
            }
            
            @Override
            public EdgeIteratorState set(StringEncodedValue property, String fwd, String bwd) {
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
        EdgeExplorer baseGraphEdgeExplorer = mainGraph.createEdgeExplorer(filter);
        return new EdgeExplorer() {
            @Override
            public EdgeIterator setBaseNode(int baseNode) {
                final List<VirtualEdgeIteratorState> extraEdges = new ArrayList<>();
                extraEdges.addAll(extraEdgesBySource.get(baseNode));
                extraEdges.addAll(extraEdgesByDestination.get(baseNode));
                Iterator<VirtualEdgeIteratorState> iterator = extraEdges.iterator();
                return new EdgeIterator() {

                    EdgeIteratorState current = null;
                    EdgeIterator baseGraphEdgeIterator = baseGraphIterator();

                    private EdgeIterator baseGraphIterator() {
                        if (baseNode < mainGraph.getNodes()) {
                            return baseGraphEdgeExplorer.setBaseNode(baseNode);
                        } else {
                            return null;
                        }
                    }

                    @Override
                    public boolean next() {
                        if (baseGraphEdgeIterator != null) {
                            if (baseGraphEdgeIterator.next()) {
                                current = baseGraphEdgeIterator;
                                return true;
                            } else {
                                baseGraphEdgeIterator = null;
                            }
                        }
                        while (iterator.hasNext()) {
                            current = iterator.next();
                            if (filter.accept(current)) {
                                return true;
                            }
                        }
                        return false;
                    }

                    @Override
                    public int getEdge() {
                        return current.getEdge();
                    }

                    @Override
                    public int getEdgeKey() {
                        return current.getEdgeKey();
                    }

                    @Override
                    public int getOrigEdgeFirst() {
                        return current.getOrigEdgeFirst();
                    }

                    @Override
                    public int getOrigEdgeLast() {
                        return current.getOrigEdgeLast();
                    }

                    @Override
                    public int getBaseNode() {
                        return current.getBaseNode();
                    }

                    @Override
                    public int getAdjNode() {
                        return current.getAdjNode();
                    }

                    @Override
                    public PointList fetchWayGeometry(FetchMode mode) {
                        return current.fetchWayGeometry(mode);
                    }

                    @Override
                    public EdgeIteratorState setWayGeometry(PointList list) {
                        current.setWayGeometry(list);
                        return this;
                    }

                    @Override
                    public double getDistance() {
                        return current.getDistance();
                    }

                    @Override
                    public EdgeIteratorState setDistance(double dist) {
                        current.setDistance(dist);
                        return this;
                    }

                    @Override
                    public IntsRef getFlags() {
                        return current.getFlags();
                    }

                    @Override
                    public EdgeIteratorState setFlags(IntsRef edgeFlags) {
                        current.setFlags(edgeFlags);
                        return this;
                    }

                    @Override
                    public boolean get(BooleanEncodedValue property) {
                        return current.get(property);
                    }

                    @Override
                    public EdgeIteratorState set(BooleanEncodedValue property, boolean value) {
                        current.set(property, value);
                        return this;
                    }

                    @Override
                    public boolean getReverse(BooleanEncodedValue property) {
                        return current.getReverse(property);
                    }

                    @Override
                    public EdgeIteratorState setReverse(BooleanEncodedValue property, boolean value) {
                        current.setReverse(property, value);
                        return this;
                    }

                    @Override
                    public EdgeIteratorState set(BooleanEncodedValue property, boolean fwd, boolean bwd) {
                        current.set(property, fwd, bwd);
                        return this;
                    }

                    @Override
                    public int get(IntEncodedValue property) {
                        return current.get(property);
                    }

                    @Override
                    public EdgeIteratorState set(IntEncodedValue property, int value) {
                        current.set(property, value);
                        return this;
                    }

                    @Override
                    public int getReverse(IntEncodedValue property) {
                        return current.getReverse(property);
                    }

                    @Override
                    public EdgeIteratorState setReverse(IntEncodedValue property, int value) {
                        current.setReverse(property, value);
                        return this;
                    }

                    @Override
                    public EdgeIteratorState set(IntEncodedValue property, int fwd, int bwd) {
                        current.set(property, fwd, bwd);
                        return this;
                    }

                    @Override
                    public double get(DecimalEncodedValue property) {
                        return current.get(property);
                    }

                    @Override
                    public EdgeIteratorState set(DecimalEncodedValue property, double value) {
                        current.set(property, value);
                        return this;
                    }

                    @Override
                    public double getReverse(DecimalEncodedValue property) {
                        return current.getReverse(property);
                    }

                    @Override
                    public EdgeIteratorState setReverse(DecimalEncodedValue property, double value) {
                        current.setReverse(property, value);
                        return this;
                    }

                    @Override
                    public EdgeIteratorState set(DecimalEncodedValue property, double fwd, double bwd) {
                        current.set(property, fwd, bwd);
                        return this;
                    }

                    @Override
                    public <T extends Enum<?>> T get(EnumEncodedValue<T> property) {
                        return current.get(property);
                    }

                    @Override
                    public <T extends Enum<?>> EdgeIteratorState set(EnumEncodedValue<T> property, T value) {
                        current.set(property, value);
                        return this;
                    }

                    @Override
                    public <T extends Enum<?>> T getReverse(EnumEncodedValue<T> property) {
                        return current.getReverse(property);
                    }

                    @Override
                    public <T extends Enum<?>> EdgeIteratorState setReverse(EnumEncodedValue<T> property, T value) {
                        current.setReverse(property, value);
                        return this;
                    }

                    @Override
                    public <T extends Enum<?>> EdgeIteratorState set(EnumEncodedValue<T> property, T fwd, T bwd) {
                        current.set(property, fwd, bwd);
                        return this;
                    }
                    
                    @Override
                    public String get(StringEncodedValue property) {
                        return current.get(property);
                    }
                    
                    @Override
                    public EdgeIteratorState set(StringEncodedValue property, String value) {
                        current.set(property, value);
                        return this;
                    }
                    
                    @Override
                    public String getReverse(StringEncodedValue property) {
                        return current.getReverse(property);
                    }
                    
                    @Override
                    public EdgeIteratorState setReverse(StringEncodedValue property, String value) {
                        current.setReverse(property, value);
                        return this;
                    }
                    
                    @Override
                    public EdgeIteratorState set(StringEncodedValue property, String fwd, String bwd) {
                        current.set(property, fwd, bwd);
                        return this;
                    }

                    @Override
                    public String getName() {
                        return current.getName();
                    }

                    @Override
                    public EdgeIteratorState setName(String name) {
                        current.setName(name);
                        return this;
                    }

                    @Override
                    public EdgeIteratorState detach(boolean reverse) {
                        return current.detach(reverse);
                    }

                    @Override
                    public EdgeIteratorState copyPropertiesFrom(EdgeIteratorState e) {
                        return current.copyPropertiesFrom(e);
                    }

                    @Override
                    public String toString() {
                        return current.toString();
                    }
                };
            }
        };
    }

    @Override
    public TurnCostStorage getTurnCostStorage() {
        return mainGraph.getTurnCostStorage();
    }

    @Override
    public Weighting wrapWeighting(Weighting weighting) {
        return mainGraph.wrapWeighting(weighting);
    }

    @Override
    public int getOtherNode(int edge, int node) {
        return mainGraph.getOtherNode(edge, node);
    }

    @Override
    public boolean isAdjacentToNode(int edge, int node) {
        return mainGraph.isAdjacentToNode(edge, node);
    }
}
