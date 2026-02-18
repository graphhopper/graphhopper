package com.graphhopper.gtfs.analysis;

import com.graphhopper.gtfs.PtGraph;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.search.KVStorage;
import com.graphhopper.storage.*;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.BBox;

import java.util.Iterator;
import java.util.Map;

class PtGraphAsAdjacencyList implements Graph {
    private final PtGraph ptGraph;

    public PtGraphAsAdjacencyList(PtGraph ptGraph) {
        this.ptGraph = ptGraph;
    }

    @Override
    public BaseGraph getBaseGraph() {
        throw new RuntimeException();
    }

    @Override
    public int getNodes() {
        return ptGraph.getNodeCount();
    }

    @Override
    public int getEdges() {
        throw new RuntimeException();
    }

    @Override
    public NodeAccess getNodeAccess() {
        throw new RuntimeException();
    }

    @Override
    public BBox getBounds() {
        throw new RuntimeException();
    }

    @Override
    public EdgeIteratorState edge(int a, int b) {
        throw new RuntimeException();
    }

    @Override
    public EdgeIteratorState getEdgeIteratorState(int edgeId, int adjNode) {
        throw new RuntimeException();
    }

    @Override
    public EdgeIteratorState getEdgeIteratorStateForKey(int edgeKey) {
        throw new RuntimeException();
    }

    @Override
    public int getOtherNode(int edge, int node) {
        throw new RuntimeException();
    }

    @Override
    public boolean isAdjacentToNode(int edge, int node) {
        throw new RuntimeException();
    }

    @Override
    public AllEdgesIterator getAllEdges() {
        throw new RuntimeException();
    }

    @Override
    public EdgeExplorer createEdgeExplorer(EdgeFilter filter) {
        return new StationGraphEdgeExplorer();
    }

    @Override
    public TurnCostStorage getTurnCostStorage() {
        throw new RuntimeException();
    }

    @Override
    public Weighting wrapWeighting(Weighting weighting) {
        throw new RuntimeException();
    }

    private class StationGraphEdgeExplorer implements EdgeExplorer {
        private int baseNode;

        @Override
        public EdgeIterator setBaseNode(int baseNode) {
            this.baseNode = baseNode;
            return new StationGraphEdgeIterator(ptGraph.edgesAround(baseNode).iterator());
        }

        private class StationGraphEdgeIterator implements EdgeIterator {
            private final Iterator<PtGraph.PtEdge> iterator;
            private PtGraph.PtEdge currentElement;

            public StationGraphEdgeIterator(Iterator<PtGraph.PtEdge> iterator) {
                this.iterator = iterator;
            }

            @Override
            public boolean next() {
                if (iterator.hasNext()) {
                    this.currentElement = iterator.next();
                    return true;
                } else {
                    return false;
                }
            }

            @Override
            public int getEdge() {
                throw new RuntimeException();
            }

            @Override
            public int getEdgeKey() {
                throw new RuntimeException();
            }

            @Override
            public int getReverseEdgeKey() {
                throw new RuntimeException();
            }

            @Override
            public int getBaseNode() {
                throw new RuntimeException();
            }

            @Override
            public int getAdjNode() {
                assert currentElement.getBaseNode() == baseNode;
                return currentElement.getAdjNode();
            }

            @Override
            public PointList fetchWayGeometry(FetchMode mode) {
                throw new RuntimeException();
            }

            @Override
            public EdgeIteratorState setWayGeometry(PointList list) {
                throw new RuntimeException();
            }

            @Override
            public double getDistance() {
                throw new RuntimeException();
            }

            @Override
            public EdgeIteratorState setDistance(double dist) {
                throw new RuntimeException();
            }

            @Override
            public long getDistance_mm() {
                throw new RuntimeException();
            }

            @Override
            public EdgeIteratorState setDistance_mm(long distance_mm) {
                throw new RuntimeException();
            }

            @Override
            public IntsRef getFlags() {
                throw new RuntimeException();
            }

            @Override
            public EdgeIteratorState setFlags(IntsRef edgeFlags) {
                throw new RuntimeException();
            }

            @Override
            public boolean get(BooleanEncodedValue property) {
                throw new RuntimeException();
            }

            @Override
            public EdgeIteratorState set(BooleanEncodedValue property, boolean value) {
                throw new RuntimeException();
            }

            @Override
            public boolean getReverse(BooleanEncodedValue property) {
                throw new RuntimeException();
            }

            @Override
            public EdgeIteratorState setReverse(BooleanEncodedValue property, boolean value) {
                throw new RuntimeException();
            }

            @Override
            public EdgeIteratorState set(BooleanEncodedValue property, boolean fwd, boolean bwd) {
                throw new RuntimeException();
            }

            @Override
            public int get(IntEncodedValue property) {
                throw new RuntimeException();
            }

            @Override
            public EdgeIteratorState set(IntEncodedValue property, int value) {
                throw new RuntimeException();
            }

            @Override
            public int getReverse(IntEncodedValue property) {
                throw new RuntimeException();
            }

            @Override
            public EdgeIteratorState setReverse(IntEncodedValue property, int value) {
                throw new RuntimeException();
            }

            @Override
            public EdgeIteratorState set(IntEncodedValue property, int fwd, int bwd) {
                throw new RuntimeException();
            }

            @Override
            public double get(DecimalEncodedValue property) {
                throw new RuntimeException();
            }

            @Override
            public EdgeIteratorState set(DecimalEncodedValue property, double value) {
                throw new RuntimeException();
            }

            @Override
            public double getReverse(DecimalEncodedValue property) {
                throw new RuntimeException();
            }

            @Override
            public EdgeIteratorState setReverse(DecimalEncodedValue property, double value) {
                throw new RuntimeException();
            }

            @Override
            public EdgeIteratorState set(DecimalEncodedValue property, double fwd, double bwd) {
                throw new RuntimeException();
            }

            @Override
            public <T extends Enum<?>> T get(EnumEncodedValue<T> property) {
                throw new RuntimeException();
            }

            @Override
            public <T extends Enum<?>> EdgeIteratorState set(EnumEncodedValue<T> property, T value) {
                throw new RuntimeException();
            }

            @Override
            public <T extends Enum<?>> T getReverse(EnumEncodedValue<T> property) {
                throw new RuntimeException();
            }

            @Override
            public <T extends Enum<?>> EdgeIteratorState setReverse(EnumEncodedValue<T> property, T value) {
                throw new RuntimeException();
            }

            @Override
            public <T extends Enum<?>> EdgeIteratorState set(EnumEncodedValue<T> property, T fwd, T bwd) {
                throw new RuntimeException();
            }

            @Override
            public String get(StringEncodedValue property) {
                throw new RuntimeException();
            }

            @Override
            public EdgeIteratorState set(StringEncodedValue property, String value) {
                throw new RuntimeException();
            }

            @Override
            public String getReverse(StringEncodedValue property) {
                throw new RuntimeException();
            }

            @Override
            public EdgeIteratorState setReverse(StringEncodedValue property, String value) {
                throw new RuntimeException();
            }

            @Override
            public EdgeIteratorState set(StringEncodedValue property, String fwd, String bwd) {
                throw new RuntimeException();
            }

            @Override
            public String getName() {
                throw new RuntimeException();
            }

            @Override
            public EdgeIteratorState setKeyValues(Map<String, KVStorage.KValue> map) {
                throw new RuntimeException();
            }

            @Override
            public Map<String, KVStorage.KValue> getKeyValues() {
                throw new RuntimeException();
            }

            @Override
            public Object getValue(String key) {
                throw new RuntimeException();
            }

            @Override
            public EdgeIteratorState detach(boolean reverse) {
                throw new RuntimeException();
            }

            @Override
            public EdgeIteratorState copyPropertiesFrom(EdgeIteratorState e) {
                throw new RuntimeException();
            }
        }
    }
}
