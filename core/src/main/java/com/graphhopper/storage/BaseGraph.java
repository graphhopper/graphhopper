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
package com.graphhopper.storage;

import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.search.KVStorage;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.BBox;

import java.io.Closeable;
import java.util.List;

import static com.graphhopper.util.Helper.nf;

/**
 * The base graph handles nodes and edges file format. It can be used with different Directory
 * implementations like RAMDirectory for fast access or via MMapDirectory for virtual-memory and not
 * thread safe usage.
 * <p>
 * Note: A RAM DataAccess Object is thread-safe in itself but if used in this Graph implementation
 * it is not write thread safe.
 * <p>
 * Life cycle: (1) object creation, (2) configuration via setters &amp; getters, (3) create or
 * loadExisting, (4) usage, (5) flush, (6) close
 */
public class BaseGraph implements Graph, Closeable {
    final static long MAX_UNSIGNED_INT = 0xFFFF_FFFFL;
    final BaseGraphNodesAndEdges store;
    final NodeAccess nodeAccess;
    final KVStorage edgeKVStorage;
    // can be null if turn costs are not supported
    final TurnCostStorage turnCostStorage;
    final BitUtil bitUtil;
    // length | nodeA | nextNode | ... | nodeB
    // as we use integer index in 'edges' area => 'geometry' area is limited to 4GB (we use pos&neg values!)
    private final DataAccess wayGeometry;
    private final Directory dir;
    private final int segmentSize;
    private boolean initialized = false;
    private long maxGeoRef;

    public BaseGraph(Directory dir, int intsForFlags, boolean withElevation, boolean withTurnCosts, int segmentSize) {
        this.dir = dir;
        this.bitUtil = BitUtil.LITTLE;
        this.wayGeometry = dir.create("geometry", segmentSize);
        this.edgeKVStorage = new KVStorage(dir, true);
        this.store = new BaseGraphNodesAndEdges(dir, intsForFlags, withElevation, withTurnCosts, segmentSize);
        this.nodeAccess = new GHNodeAccess(store);
        this.segmentSize = segmentSize;
        turnCostStorage = withTurnCosts ? new TurnCostStorage(this, dir.create("turn_costs", segmentSize)) : null;
    }

    private int getOtherNode(int nodeThis, long edgePointer) {
        int nodeA = store.getNodeA(edgePointer);
        return nodeThis == nodeA ? store.getNodeB(edgePointer) : nodeA;
    }

    private boolean isAdjacentToNode(int node, long edgePointer) {
        return store.getNodeA(edgePointer) == node || store.getNodeB(edgePointer) == node;
    }

    private static boolean isTestingEnabled() {
        boolean enableIfAssert = false;
        assert (enableIfAssert = true) : true;
        return enableIfAssert;
    }

    public void debugPrint() {
        store.debugPrint();
    }

    @Override
    public BaseGraph getBaseGraph() {
        return this;
    }

    public boolean isInitialized() {
        return initialized;
    }

    void checkNotInitialized() {
        if (initialized)
            throw new IllegalStateException("You cannot configure this BaseGraph "
                    + "after calling create or loadExisting. Calling one of the methods twice is also not allowed.");
    }

    private void loadWayGeometryHeader() {
        int geometryVersion = wayGeometry.getHeader(0);
        GHUtility.checkDAVersion(wayGeometry.getName(), Constants.VERSION_GEOMETRY, geometryVersion);
        maxGeoRef = bitUtil.combineIntsToLong(
                wayGeometry.getHeader(4),
                wayGeometry.getHeader(8)
        );
    }

    private void setWayGeometryHeader() {
        wayGeometry.setHeader(0, Constants.VERSION_GEOMETRY);
        wayGeometry.setHeader(4, bitUtil.getIntLow(maxGeoRef));
        wayGeometry.setHeader(8, bitUtil.getIntHigh(maxGeoRef));
    }

    private void setInitialized() {
        initialized = true;
    }

    boolean supportsTurnCosts() {
        return turnCostStorage != null;
    }

    @Override
    public int getNodes() {
        return store.getNodes();
    }

    @Override
    public int getEdges() {
        return store.getEdges();
    }

    @Override
    public NodeAccess getNodeAccess() {
        return nodeAccess;
    }

    @Override
    public BBox getBounds() {
        return store.getBounds();
    }

    public synchronized void freeze() {
        if (isFrozen())
            throw new IllegalStateException("base graph already frozen");
        store.setFrozen(true);
    }

    public synchronized boolean isFrozen() {
        return store.getFrozen();
    }

    public BaseGraph create(long initSize) {
        checkNotInitialized();
        dir.create();
        store.create(initSize);

        initSize = Math.min(initSize, 2000);
        wayGeometry.create(initSize);
        edgeKVStorage.create(initSize);
        if (supportsTurnCosts()) {
            turnCostStorage.create(initSize);
        }
        setInitialized();
        // 0 stands for no separate geoRef
        maxGeoRef = 4;
        return this;
    }

    public int getIntsForFlags() {
        return store.getIntsForFlags();
    }

    public String toDetailsString() {
        return store.toDetailsString() + ", "
                + "name:(" + edgeKVStorage.getCapacity() / Helper.MB + "MB), "
                + "geo:" + nf(maxGeoRef) + "(" + wayGeometry.getCapacity() / Helper.MB + "MB)";
    }

    /**
     * Flush and free resources that are not needed for post-processing (way geometries and KVStorage for edges).
     */
    public void flushAndCloseGeometryAndNameStorage() {
        setWayGeometryHeader();

        wayGeometry.flush();
        wayGeometry.close();

        edgeKVStorage.flush();
        edgeKVStorage.close();
    }

    public void flush() {
        if (!wayGeometry.isClosed()) {
            setWayGeometryHeader();
            wayGeometry.flush();
        }

        if (!edgeKVStorage.isClosed())
            edgeKVStorage.flush();

        store.flush();
        if (supportsTurnCosts()) {
            turnCostStorage.flush();
        }
    }

    @Override
    public void close() {
        if (!wayGeometry.isClosed())
            wayGeometry.close();
        if (!edgeKVStorage.isClosed())
            edgeKVStorage.close();
        store.close();
        if (supportsTurnCosts()) {
            turnCostStorage.close();
        }
    }

    public long getCapacity() {
        return store.getCapacity() + edgeKVStorage.getCapacity()
                + wayGeometry.getCapacity() + (supportsTurnCosts() ? turnCostStorage.getCapacity() : 0);
    }

    long getMaxGeoRef() {
        return maxGeoRef;
    }

    public boolean loadExisting() {
        checkNotInitialized();

        if (!store.loadExisting())
            return false;

        if (!wayGeometry.loadExisting())
            return false;

        if (!edgeKVStorage.loadExisting())
            return false;

        if (supportsTurnCosts() && !turnCostStorage.loadExisting())
            return false;

        setInitialized();
        loadWayGeometryHeader();
        return true;
    }

    /**
     * This method copies the properties of one {@link EdgeIteratorState} to another.
     *
     * @return the updated iterator the properties where copied to.
     */
    EdgeIteratorState copyProperties(EdgeIteratorState from, EdgeIteratorStateImpl to) {
        long edgePointer = store.toEdgePointer(to.getEdge());
        store.writeFlags(edgePointer, from.getFlags());

        // copy the rest with higher level API
        to.setDistance(from.getDistance()).
                setKeyValues(from.getKeyValues()).
                setWayGeometry(from.fetchWayGeometry(FetchMode.PILLAR_ONLY));

        return to;
    }

    /**
     * Create edge between nodes a and b
     *
     * @return EdgeIteratorState of newly created edge
     */
    @Override
    public EdgeIteratorState edge(int nodeA, int nodeB) {
        if (isFrozen())
            throw new IllegalStateException("Cannot create edge if graph is already frozen");
        int edgeId = store.edge(nodeA, nodeB);
        EdgeIteratorStateImpl edge = new EdgeIteratorStateImpl(this);
        boolean valid = edge.init(edgeId, nodeB);
        assert valid;
        return edge;
    }

    @Override
    public EdgeIteratorState getEdgeIteratorState(int edgeId, int adjNode) {
        EdgeIteratorStateImpl edge = new EdgeIteratorStateImpl(this);
        if (edge.init(edgeId, adjNode))
            return edge;
        // if edgeId exists but adjacent nodes do not match
        return null;
    }

    @Override
    public EdgeIteratorState getEdgeIteratorStateForKey(int edgeKey) {
        EdgeIteratorStateImpl edge = new EdgeIteratorStateImpl(this);
        edge.init(edgeKey);
        return edge;
    }

    @Override
    public EdgeExplorer createEdgeExplorer(EdgeFilter filter) {
        return new EdgeIteratorImpl(this, filter);
    }

    @Override
    public EdgeExplorer createEdgeExplorer() {
        return createEdgeExplorer(EdgeFilter.ALL_EDGES);
    }

    @Override
    public AllEdgesIterator getAllEdges() {
        return new AllEdgeIterator(this);
    }

    @Override
    public TurnCostStorage getTurnCostStorage() {
        return turnCostStorage;
    }

    @Override
    public Weighting wrapWeighting(Weighting weighting) {
        return weighting;
    }

    @Override
    public int getOtherNode(int edge, int node) {
        long edgePointer = store.toEdgePointer(edge);
        return getOtherNode(node, edgePointer);
    }

    @Override
    public boolean isAdjacentToNode(int edge, int node) {
        long edgePointer = store.toEdgePointer(edge);
        return isAdjacentToNode(node, edgePointer);
    }

    private void setWayGeometry_(PointList pillarNodes, long edgePointer, boolean reverse) {
        if (pillarNodes != null && !pillarNodes.isEmpty()) {
            if (pillarNodes.getDimension() != nodeAccess.getDimension())
                throw new IllegalArgumentException("Cannot use pointlist which is " + pillarNodes.getDimension()
                        + "D for graph which is " + nodeAccess.getDimension() + "D");

            long existingGeoRef = Helper.toUnsignedLong(store.getGeoRef(edgePointer));

            int len = pillarNodes.size();
            int dim = nodeAccess.getDimension();
            if (existingGeoRef > 0) {
                final int count = wayGeometry.getInt(existingGeoRef * 4L);
                if (len <= count) {
                    setWayGeometryAtGeoRef(pillarNodes, edgePointer, reverse, existingGeoRef);
                    return;
                }
            }

            long nextGeoRef = nextGeoRef(len * dim);
            setWayGeometryAtGeoRef(pillarNodes, edgePointer, reverse, nextGeoRef);
        } else {
            store.setGeoRef(edgePointer, 0);
        }
    }

    public EdgeIntAccess createEdgeIntAccess() {
        return new EdgeIntAccess() {
            @Override
            public int getInt(int edgeId, int index) {
                long edgePointer = store.toEdgePointer(edgeId);
                return store.getFlagInt(edgePointer, index);
            }

            @Override
            public void setInt(int edgeId, int index, int value) {
                long edgePointer = store.toEdgePointer(edgeId);
                store.setFlagInt(edgePointer, index, value);
            }
        };
    }

    private void setWayGeometryAtGeoRef(PointList pillarNodes, long edgePointer, boolean reverse, long geoRef) {
        int len = pillarNodes.size();
        int dim = nodeAccess.getDimension();
        long geoRefPosition = geoRef * 4;
        int totalLen = len * dim * 4 + 4;
        ensureGeometry(geoRefPosition, totalLen);
        byte[] wayGeometryBytes = createWayGeometryBytes(pillarNodes, reverse);
        wayGeometry.setBytes(geoRefPosition, wayGeometryBytes, wayGeometryBytes.length);
        store.setGeoRef(edgePointer, Helper.toSignedInt(geoRef));
    }

    private byte[] createWayGeometryBytes(PointList pillarNodes, boolean reverse) {
        int len = pillarNodes.size();
        int dim = nodeAccess.getDimension();
        int totalLen = len * dim * 4 + 4;
        byte[] bytes = new byte[totalLen];
        bitUtil.fromInt(bytes, len, 0);
        if (reverse)
            pillarNodes.reverse();

        int tmpOffset = 4;
        boolean is3D = nodeAccess.is3D();
        for (int i = 0; i < len; i++) {
            double lat = pillarNodes.getLat(i);
            bitUtil.fromInt(bytes, Helper.degreeToInt(lat), tmpOffset);
            tmpOffset += 4;
            bitUtil.fromInt(bytes, Helper.degreeToInt(pillarNodes.getLon(i)), tmpOffset);
            tmpOffset += 4;

            if (is3D) {
                bitUtil.fromInt(bytes, Helper.eleToInt(pillarNodes.getEle(i)), tmpOffset);
                tmpOffset += 4;
            }
        }
        return bytes;
    }

    private PointList fetchWayGeometry_(long edgePointer, boolean reverse, FetchMode mode, int baseNode, int adjNode) {
        if (mode == FetchMode.TOWER_ONLY) {
            // no reverse handling required as adjNode and baseNode is already properly switched
            PointList pillarNodes = new PointList(2, nodeAccess.is3D());
            pillarNodes.add(nodeAccess, baseNode);
            pillarNodes.add(nodeAccess, adjNode);
            return pillarNodes;
        }
        long geoRef = Helper.toUnsignedLong(store.getGeoRef(edgePointer));
        int count = 0;
        byte[] bytes = null;
        if (geoRef > 0) {
            geoRef *= 4L;
            count = wayGeometry.getInt(geoRef);

            geoRef += 4L;
            bytes = new byte[count * nodeAccess.getDimension() * 4];
            wayGeometry.getBytes(geoRef, bytes, bytes.length);
        } else if (mode == FetchMode.PILLAR_ONLY)
            return PointList.EMPTY;

        PointList pillarNodes = new PointList(getPointListLength(count, mode), nodeAccess.is3D());
        if (reverse) {
            if (mode == FetchMode.ALL || mode == FetchMode.PILLAR_AND_ADJ)
                pillarNodes.add(nodeAccess, adjNode);
        } else if (mode == FetchMode.ALL || mode == FetchMode.BASE_AND_PILLAR)
            pillarNodes.add(nodeAccess, baseNode);

        int index = 0;
        for (int i = 0; i < count; i++) {
            double lat = Helper.intToDegree(bitUtil.toInt(bytes, index));
            index += 4;
            double lon = Helper.intToDegree(bitUtil.toInt(bytes, index));
            index += 4;
            if (nodeAccess.is3D()) {
                pillarNodes.add(lat, lon, Helper.intToEle(bitUtil.toInt(bytes, index)));
                index += 4;
            } else {
                pillarNodes.add(lat, lon);
            }
        }

        if (reverse) {
            if (mode == FetchMode.ALL || mode == FetchMode.BASE_AND_PILLAR)
                pillarNodes.add(nodeAccess, baseNode);

            pillarNodes.reverse();
        } else if (mode == FetchMode.ALL || mode == FetchMode.PILLAR_AND_ADJ)
            pillarNodes.add(nodeAccess, adjNode);

        return pillarNodes;
    }

    static int getPointListLength(int pillarNodes, FetchMode mode) {
        switch (mode) {
            case TOWER_ONLY:
                return 2;
            case PILLAR_ONLY:
                return pillarNodes;
            case BASE_AND_PILLAR:
            case PILLAR_AND_ADJ:
                return pillarNodes + 1;
            case ALL:
                return pillarNodes + 2;
        }
        throw new IllegalArgumentException("Mode isn't handled " + mode);
    }

    private void ensureGeometry(long bytePos, int byteLength) {
        wayGeometry.ensureCapacity(bytePos + byteLength);
    }

    private long nextGeoRef(int arrayLength) {
        long tmp = maxGeoRef;
        maxGeoRef += arrayLength + 1L;
        if (maxGeoRef > MAX_UNSIGNED_INT)
            throw new IllegalStateException("Geometry too large, does not fit in 32 bits " + maxGeoRef);

        return tmp;
    }

    public boolean isClosed() {
        return store.isClosed();
    }

    public Directory getDirectory() {
        return dir;
    }

    public int getSegmentSize() {
        return segmentSize;
    }

    public static class Builder {
        private final int intsForFlags;
        private Directory directory = new RAMDirectory();
        private boolean withElevation = false;
        private boolean withTurnCosts = false;
        private long bytes = 100;
        private int segmentSize = -1;

        public Builder(EncodingManager em) {
            this(em.getIntsForFlags());
            withTurnCosts(em.needsTurnCostsSupport());
        }

        public Builder(int intsForFlags) {
            this.intsForFlags = intsForFlags;
        }

        // todo: maybe rename later, but for now this makes it easier to replace GraphBuilder
        public Builder setDir(Directory directory) {
            this.directory = directory;
            return this;
        }

        // todo: maybe rename later, but for now this makes it easier to replace GraphBuilder
        public Builder set3D(boolean withElevation) {
            this.withElevation = withElevation;
            return this;
        }

        // todo: maybe rename later, but for now this makes it easier to replace GraphBuilder
        public Builder withTurnCosts(boolean withTurnCosts) {
            this.withTurnCosts = withTurnCosts;
            return this;
        }

        public Builder setSegmentSize(int segmentSize) {
            this.segmentSize = segmentSize;
            return this;
        }

        public Builder setBytes(long bytes) {
            this.bytes = bytes;
            return this;
        }

        public BaseGraph build() {
            return new BaseGraph(directory, intsForFlags, withElevation, withTurnCosts, segmentSize);
        }

        public BaseGraph create() {
            BaseGraph baseGraph = build();
            baseGraph.create(bytes);
            return baseGraph;
        }
    }

    protected static class EdgeIteratorImpl extends EdgeIteratorStateImpl implements EdgeExplorer, EdgeIterator {
        final EdgeFilter filter;
        int nextEdgeId;

        public EdgeIteratorImpl(BaseGraph baseGraph, EdgeFilter filter) {
            super(baseGraph);
            if (filter == null)
                throw new IllegalArgumentException("Instead null filter use EdgeFilter.ALL_EDGES");
            this.filter = filter;
        }

        @Override
        public EdgeIterator setBaseNode(int baseNode) {
            nextEdgeId = edgeId = store.getEdgeRef(store.toNodePointer(baseNode));
            this.baseNode = baseNode;
            return this;
        }

        @Override
        public final boolean next() {
            while (EdgeIterator.Edge.isValid(nextEdgeId)) {
                goToNext();
                if (filter.accept(this))
                    return true;
            }
            return false;
        }

        void goToNext() {
            edgePointer = store.toEdgePointer(nextEdgeId);
            edgeId = nextEdgeId;
            int nodeA = store.getNodeA(edgePointer);
            boolean baseNodeIsNodeA = baseNode == nodeA;
            adjNode = baseNodeIsNodeA ? store.getNodeB(edgePointer) : nodeA;
            reverse = !baseNodeIsNodeA;

            // position to next edge
            nextEdgeId = baseNodeIsNodeA ? store.getLinkA(edgePointer) : store.getLinkB(edgePointer);
            assert nextEdgeId != edgeId : ("endless loop detected for base node: " + baseNode + ", adj node: " + adjNode
                    + ", edge pointer: " + edgePointer + ", edge: " + edgeId);
        }

        @Override
        public EdgeIteratorState detach(boolean reverseArg) {
            if (edgeId == nextEdgeId)
                throw new IllegalStateException("call next before detaching (edgeId:" + edgeId + " vs. next " + nextEdgeId + ")");
            return super.detach(reverseArg);
        }
    }

    /**
     * Include all edges of this storage in the iterator.
     */
    protected static class AllEdgeIterator extends EdgeIteratorStateImpl implements AllEdgesIterator {
        public AllEdgeIterator(BaseGraph baseGraph) {
            super(baseGraph);
        }

        @Override
        public int length() {
            return store.getEdges();
        }

        @Override
        public boolean next() {
            edgeId++;
            if (edgeId >= store.getEdges())
                return false;
            edgePointer = store.toEdgePointer(edgeId);
            baseNode = store.getNodeA(edgePointer);
            adjNode = store.getNodeB(edgePointer);
            reverse = false;
            return true;
        }

        @Override
        public final EdgeIteratorState detach(boolean reverseArg) {
            if (edgePointer < 0)
                throw new IllegalStateException("call next before detaching");

            AllEdgeIterator iter = new AllEdgeIterator(baseGraph);
            iter.edgeId = edgeId;
            iter.edgePointer = edgePointer;
            if (reverseArg) {
                iter.reverse = !this.reverse;
                iter.baseNode = adjNode;
                iter.adjNode = baseNode;
            } else {
                iter.reverse = this.reverse;
                iter.baseNode = baseNode;
                iter.adjNode = adjNode;
            }
            return iter;
        }
    }

    static class EdgeIteratorStateImpl implements EdgeIteratorState {
        final BaseGraph baseGraph;
        final BaseGraphNodesAndEdges store;
        long edgePointer = -1;
        int baseNode;
        int adjNode;
        // we need reverse if detach is called
        boolean reverse = false;
        int edgeId = -1;
        private final EdgeIntAccess edgeIntAccess;

        public EdgeIteratorStateImpl(BaseGraph baseGraph) {
            this.baseGraph = baseGraph;
            edgeIntAccess = baseGraph.createEdgeIntAccess();
            store = baseGraph.store;
        }

        /**
         * @return false if the edge has not a node equal to expectedAdjNode
         */
        final boolean init(int edgeId, int expectedAdjNode) {
            if (edgeId < 0 || edgeId >= store.getEdges())
                throw new IllegalArgumentException("edge: " + edgeId + " out of bounds: [0," + store.getEdges() + "[");
            this.edgeId = edgeId;
            edgePointer = store.toEdgePointer(edgeId);
            baseNode = store.getNodeA(edgePointer);
            adjNode = store.getNodeB(edgePointer);

            if (expectedAdjNode == adjNode || expectedAdjNode == Integer.MIN_VALUE) {
                reverse = false;
                return true;
            } else if (expectedAdjNode == baseNode) {
                reverse = true;
                baseNode = adjNode;
                adjNode = expectedAdjNode;
                return true;
            }
            return false;
        }

        /**
         * Similar to {@link #init(int edgeId, int adjNode)}, but here we retrieve the edge in a certain direction
         * directly using an edge key.
         */
        final void init(int edgeKey) {
            if (edgeKey < 0)
                throw new IllegalArgumentException("edge keys must not be negative, given: " + edgeKey);
            this.edgeId = GHUtility.getEdgeFromEdgeKey(edgeKey);
            edgePointer = store.toEdgePointer(edgeId);
            baseNode = store.getNodeA(edgePointer);
            adjNode = store.getNodeB(edgePointer);

            if (edgeKey % 2 == 0 || baseNode == adjNode) {
                reverse = false;
            } else {
                reverse = true;
                int tmp = baseNode;
                baseNode = adjNode;
                adjNode = tmp;
            }
        }

        @Override
        public final int getBaseNode() {
            return baseNode;
        }

        @Override
        public final int getAdjNode() {
            return adjNode;
        }

        @Override
        public double getDistance() {
            return store.getDist(edgePointer);
        }

        @Override
        public EdgeIteratorState setDistance(double dist) {
            store.setDist(edgePointer, dist);
            return this;
        }

        @Override
        public IntsRef getFlags() {
            IntsRef edgeFlags = new IntsRef(store.getIntsForFlags());
            store.readFlags(edgePointer, edgeFlags);
            return edgeFlags;
        }

        @Override
        public final EdgeIteratorState setFlags(IntsRef edgeFlags) {
            assert edgeId < store.getEdges() : "must be edge but was shortcut: " + edgeId + " >= " + store.getEdges() + ". Use setFlagsAndWeight";
            store.writeFlags(edgePointer, edgeFlags);
            return this;
        }

        @Override
        public boolean get(BooleanEncodedValue property) {
            return property.getBool(reverse, edgeId, edgeIntAccess);
        }

        @Override
        public EdgeIteratorState set(BooleanEncodedValue property, boolean value) {
            property.setBool(reverse, edgeId, edgeIntAccess, value);
            return this;
        }

        @Override
        public boolean getReverse(BooleanEncodedValue property) {
            return property.getBool(!reverse, edgeId, edgeIntAccess);
        }

        @Override
        public EdgeIteratorState setReverse(BooleanEncodedValue property, boolean value) {
            property.setBool(!reverse, edgeId, edgeIntAccess, value);
            return this;
        }

        @Override
        public EdgeIteratorState set(BooleanEncodedValue property, boolean fwd, boolean bwd) {
            if (!property.isStoreTwoDirections())
                throw new IllegalArgumentException("EncodedValue " + property.getName() + " supports only one direction");
            property.setBool(reverse, edgeId, edgeIntAccess, fwd);
            property.setBool(!reverse, edgeId, edgeIntAccess, bwd);
            return this;
        }

        @Override
        public int get(IntEncodedValue property) {
            return property.getInt(reverse, edgeId, edgeIntAccess);
        }

        @Override
        public EdgeIteratorState set(IntEncodedValue property, int value) {
            property.setInt(reverse, edgeId, edgeIntAccess, value);
            return this;
        }

        @Override
        public int getReverse(IntEncodedValue property) {
            return property.getInt(!reverse, edgeId, edgeIntAccess);
        }

        @Override
        public EdgeIteratorState setReverse(IntEncodedValue property, int value) {
            property.setInt(!reverse, edgeId, edgeIntAccess, value);
            return this;
        }

        @Override
        public EdgeIteratorState set(IntEncodedValue property, int fwd, int bwd) {
            if (!property.isStoreTwoDirections())
                throw new IllegalArgumentException("EncodedValue " + property.getName() + " supports only one direction");
            property.setInt(reverse, edgeId, edgeIntAccess, fwd);
            property.setInt(!reverse, edgeId, edgeIntAccess, bwd);
            return this;
        }

        @Override
        public double get(DecimalEncodedValue property) {
            return property.getDecimal(reverse, edgeId, edgeIntAccess);
        }

        @Override
        public EdgeIteratorState set(DecimalEncodedValue property, double value) {
            property.setDecimal(reverse, edgeId, edgeIntAccess, value);
            return this;
        }

        @Override
        public double getReverse(DecimalEncodedValue property) {
            return property.getDecimal(!reverse, edgeId, edgeIntAccess);
        }

        @Override
        public EdgeIteratorState setReverse(DecimalEncodedValue property, double value) {
            property.setDecimal(!reverse, edgeId, edgeIntAccess, value);
            return this;
        }

        @Override
        public EdgeIteratorState set(DecimalEncodedValue property, double fwd, double bwd) {
            if (!property.isStoreTwoDirections())
                throw new IllegalArgumentException("EncodedValue " + property.getName() + " supports only one direction");
            property.setDecimal(reverse, edgeId, edgeIntAccess, fwd);
            property.setDecimal(!reverse, edgeId, edgeIntAccess, bwd);
            return this;
        }

        @Override
        public <T extends Enum<?>> T get(EnumEncodedValue<T> property) {
            return property.getEnum(reverse, edgeId, edgeIntAccess);
        }

        @Override
        public <T extends Enum<?>> EdgeIteratorState set(EnumEncodedValue<T> property, T value) {
            property.setEnum(reverse, edgeId, edgeIntAccess, value);
            return this;
        }

        @Override
        public <T extends Enum<?>> T getReverse(EnumEncodedValue<T> property) {
            return property.getEnum(!reverse, edgeId, edgeIntAccess);
        }

        @Override
        public <T extends Enum<?>> EdgeIteratorState setReverse(EnumEncodedValue<T> property, T value) {
            property.setEnum(!reverse, edgeId, edgeIntAccess, value);
            return this;
        }

        @Override
        public <T extends Enum<?>> EdgeIteratorState set(EnumEncodedValue<T> property, T fwd, T bwd) {
            if (!property.isStoreTwoDirections())
                throw new IllegalArgumentException("EncodedValue " + property.getName() + " supports only one direction");
            property.setEnum(reverse, edgeId, edgeIntAccess, fwd);
            property.setEnum(!reverse, edgeId, edgeIntAccess, bwd);
            return this;
        }

        @Override
        public String get(StringEncodedValue property) {
            return property.getString(reverse, edgeId, edgeIntAccess);
        }

        @Override
        public EdgeIteratorState set(StringEncodedValue property, String value) {
            property.setString(reverse, edgeId, edgeIntAccess, value);
            return this;
        }

        @Override
        public String getReverse(StringEncodedValue property) {
            return property.getString(!reverse, edgeId, edgeIntAccess);
        }

        @Override
        public EdgeIteratorState setReverse(StringEncodedValue property, String value) {
            property.setString(!reverse, edgeId, edgeIntAccess, value);
            return this;
        }

        @Override
        public EdgeIteratorState set(StringEncodedValue property, String fwd, String bwd) {
            if (!property.isStoreTwoDirections())
                throw new IllegalArgumentException("EncodedValue " + property.getName() + " supports only one direction");
            property.setString(reverse, edgeId, edgeIntAccess, fwd);
            property.setString(!reverse, edgeId, edgeIntAccess, bwd);
            return this;
        }

        @Override
        public final EdgeIteratorState copyPropertiesFrom(EdgeIteratorState edge) {
            return baseGraph.copyProperties(edge, this);
        }

        @Override
        public EdgeIteratorState setWayGeometry(PointList pillarNodes) {
            baseGraph.setWayGeometry_(pillarNodes, edgePointer, reverse);
            return this;
        }

        @Override
        public PointList fetchWayGeometry(FetchMode mode) {
            return baseGraph.fetchWayGeometry_(edgePointer, reverse, mode, getBaseNode(), getAdjNode());
        }

        @Override
        public int getEdge() {
            return edgeId;
        }

        @Override
        public int getEdgeKey() {
            return GHUtility.createEdgeKey(edgeId, baseNode == adjNode, reverse);
        }

        @Override
        public int getReverseEdgeKey() {
            return baseNode == adjNode ? getEdgeKey() : GHUtility.reverseEdgeKey(getEdgeKey());
        }

        @Override
        public EdgeIteratorState setKeyValues(List<KVStorage.KeyValue> entries) {
            long pointer = baseGraph.edgeKVStorage.add(entries);
            if (pointer > MAX_UNSIGNED_INT)
                throw new IllegalStateException("Too many key value pairs are stored, currently limited to " + MAX_UNSIGNED_INT + " was " + pointer);
            store.setKeyValuesRef(edgePointer, Helper.toSignedInt(pointer));
            return this;
        }

        @Override
        public List<KVStorage.KeyValue> getKeyValues() {
            long kvEntryRef = Helper.toUnsignedLong(store.getKeyValuesRef(edgePointer));
            return baseGraph.edgeKVStorage.getAll(kvEntryRef);
        }

        @Override
        public Object getValue(String key) {
            long kvEntryRef = Helper.toUnsignedLong(store.getKeyValuesRef(edgePointer));
            return baseGraph.edgeKVStorage.get(kvEntryRef, key, reverse);
        }

        @Override
        public String getName() {
            String name = (String) getValue(KVStorage.KeyValue.STREET_NAME);
            // preserve backward compatibility (returns empty string if name tag missing)
            return name == null ? "" : name;
        }

        @Override
        public EdgeIteratorState detach(boolean reverseArg) {
            if (!EdgeIterator.Edge.isValid(edgeId))
                throw new IllegalStateException("call setEdgeId before detaching (edgeId:" + edgeId + ")");
            EdgeIteratorStateImpl edge = new EdgeIteratorStateImpl(baseGraph);
            boolean valid = edge.init(edgeId, reverseArg ? baseNode : adjNode);
            assert valid;
            if (reverseArg) {
                // for #162
                edge.reverse = !reverse;
            }
            return edge;
        }

        @Override
        public final String toString() {
            return getEdge() + " " + getBaseNode() + "-" + getAdjNode();
        }
    }
}
