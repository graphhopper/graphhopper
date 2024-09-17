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
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import static com.graphhopper.util.Helper.nf;
import static com.graphhopper.util.Parameters.Details.STREET_NAME;

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
    private final DataAccess wayGeometry;
    private final Directory dir;
    private final int segmentSize;
    private boolean initialized = false;
    private long minGeoRef;
    private long maxGeoRef;
    private final int eleBytesPerCoord;

    public BaseGraph(Directory dir, boolean withElevation, boolean withTurnCosts, int segmentSize, int bytesForFlags) {
        this.dir = dir;
        this.bitUtil = BitUtil.LITTLE;
        this.wayGeometry = dir.create("geometry", segmentSize);
        this.edgeKVStorage = new KVStorage(dir, true);
        this.store = new BaseGraphNodesAndEdges(dir, withElevation, withTurnCosts, segmentSize, bytesForFlags);
        this.nodeAccess = new GHNodeAccess(store);
        this.segmentSize = segmentSize;
        this.turnCostStorage = withTurnCosts ? new TurnCostStorage(this, dir.create("turn_costs", dir.getDefaultType("turn_costs", true), segmentSize)) : null;
        this.eleBytesPerCoord = (nodeAccess.getDimension() == 3 ? 3 : 0);
    }

    BaseGraphNodesAndEdges getStore() {
        return store;
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
        minGeoRef = bitUtil.toLong(
                wayGeometry.getHeader(4),
                wayGeometry.getHeader(8)
        );
        maxGeoRef = bitUtil.toLong(
                wayGeometry.getHeader(12),
                wayGeometry.getHeader(16)
        );
    }

    private void setWayGeometryHeader() {
        wayGeometry.setHeader(0, Constants.VERSION_GEOMETRY);
        wayGeometry.setHeader(4, bitUtil.getIntLow(minGeoRef));
        wayGeometry.setHeader(8, bitUtil.getIntHigh(minGeoRef));
        wayGeometry.setHeader(12, bitUtil.getIntLow(maxGeoRef));
        wayGeometry.setHeader(16, bitUtil.getIntHigh(maxGeoRef));
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
        // 0 stands for no separate geoRef, <0 stands for no separate geoRef but existing edge copies
        minGeoRef = -1;
        maxGeoRef = 1;
        return this;
    }

    public String toDetailsString() {
        return store.toDetailsString() + ", "
                + "name:(" + edgeKVStorage.getCapacity() / Helper.MB + "MB), "
                + "geo:" + nf(maxGeoRef) + "/" + nf(minGeoRef) + "(" + wayGeometry.getCapacity() / Helper.MB + "MB)";
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
        if (nodeA == nodeB)
            // Loop edges would only make sense if their attributes were the same for both 'directions',
            // because for routing algorithms (which ignore the way geometry) loop edges do not even
            // have a well-defined 'direction'. So we either need to make sure the attributes
            // are the same for both directions, or reject loop edges altogether. Since we currently
            // don't know any use-case for loop edges in road networks (there is one for PT),
            // we reject them here.
            throw new IllegalArgumentException("Loop edges are not supported, got: " + nodeA + " - " + nodeB);
        int edgeId = store.edge(nodeA, nodeB);
        EdgeIteratorStateImpl edge = new EdgeIteratorStateImpl(this);
        boolean valid = edge.init(edgeId, nodeB);
        assert valid;
        return edge;
    }

    /**
     * Creates a copy of a given edge with the same properties.
     *
     * @param reuseGeometry If true the copy uses the same pointer to the geometry,
     *                      so changing the geometry would alter the geometry for both edges!
     */
    public EdgeIteratorState copyEdge(int edge, boolean reuseGeometry) {
        EdgeIteratorStateImpl edgeState = (EdgeIteratorStateImpl) getEdgeIteratorState(edge, Integer.MIN_VALUE);
        EdgeIteratorStateImpl newEdge = (EdgeIteratorStateImpl) edge(edgeState.getBaseNode(), edgeState.getAdjNode())
                .setFlags(edgeState.getFlags())
                .setDistance(edgeState.getDistance())
                .setKeyValues(edgeState.getKeyValues());
        if (reuseGeometry) {
            // We use the same geo ref for the copied edge. This saves memory because we are not duplicating
            // the geometry, and it allows to identify the copies of a given edge.
            long edgePointer = edgeState.edgePointer;
            long geoRef = store.getGeoRef(edgePointer);
            if (geoRef == 0) {
                // No geometry for this edge, but we need to be able to identify the copied edges later, so
                // we use a dedicated negative value for the geo ref.
                geoRef = minGeoRef;
                store.setGeoRef(edgePointer, geoRef);
                minGeoRef--;
            }
            store.setGeoRef(newEdge.edgePointer, geoRef);
        } else {
            newEdge.setWayGeometry(edgeState.fetchWayGeometry(FetchMode.PILLAR_ONLY));
        }
        return newEdge;
    }

    /**
     * Runs the given action on the given edge and all its copies that were created with 'reuseGeometry=true'.
     */
    public void forEdgeAndCopiesOfEdge(EdgeExplorer explorer, EdgeIteratorState edge, Consumer<EdgeIteratorState> consumer) {
        final long geoRef = store.getGeoRef(((EdgeIteratorStateImpl) edge).edgePointer);
        if (geoRef == 0) {
            // 0 means there is no geometry (and no copy of this edge), but of course not all edges
            // without geometry are copies of each other, so we need to return early
            consumer.accept(edge);
            return;
        }
        EdgeIterator iter = explorer.setBaseNode(edge.getBaseNode());
        while (iter.next()) {
            long geoRefBefore = store.getGeoRef(((EdgeIteratorStateImpl) iter).edgePointer);
            if (geoRefBefore == geoRef)
                consumer.accept(iter);
            if (store.getGeoRef(((EdgeIteratorStateImpl) iter).edgePointer) != geoRefBefore)
                throw new IllegalStateException("The consumer must not change the geo ref");
        }
    }

    public void forEdgeAndCopiesOfEdge(EdgeExplorer explorer, int node, int edge, IntConsumer consumer) {
        final long geoRef = store.getGeoRef(store.toEdgePointer(edge));
        if (geoRef == 0) {
            // 0 means there is no geometry (and no copy of this edge), but of course not all edges
            // without geometry are copies of each other, so we need to return early
            consumer.accept(edge);
            return;
        }
        EdgeIterator iter = explorer.setBaseNode(node);
        while (iter.next()) {
            long geoRefBefore = store.getGeoRef(((EdgeIteratorStateImpl) iter).edgePointer);
            if (geoRefBefore == geoRef)
                consumer.accept(iter.getEdge());
        }
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

    /**
     * @return true if the specified node is the adjacent node of the specified edge
     * (relative to the direction in which the edge is stored).
     */
    public boolean isAdjNode(int edge, int node) {
        long edgePointer = store.toEdgePointer(edge);
        return node == store.getNodeB(edgePointer);
    }

    private void setWayGeometry_(PointList pillarNodes, long edgePointer, boolean reverse) {
        if (pillarNodes != null && !pillarNodes.isEmpty()) {
            if (pillarNodes.getDimension() != nodeAccess.getDimension())
                throw new IllegalArgumentException("Cannot use pointlist which is " + pillarNodes.getDimension()
                        + "D for graph which is " + nodeAccess.getDimension() + "D");

            long existingGeoRef = store.getGeoRef(edgePointer);
            if (existingGeoRef < 0)
                // users of this method might not be aware that after changing the geo ref it is no
                // longer possible to find the copies corresponding to an edge, so we deny this
                throw new IllegalStateException("This edge has already been copied so we can no longer change the geometry, pointer=" + edgePointer);

            int len = pillarNodes.size();
            if (existingGeoRef > 0) {
                final int count = getPillarCount(existingGeoRef);
                if (len <= count) {
                    setWayGeometryAtGeoRef(pillarNodes, edgePointer, reverse, existingGeoRef);
                    return;
                } else {
                    throw new IllegalStateException("This edge already has a way geometry so it cannot be changed to a bigger geometry, pointer=" + edgePointer);
                }
            }
            long nextGeoRef = nextGeoRef(3 + len * (8 + eleBytesPerCoord));
            setWayGeometryAtGeoRef(pillarNodes, edgePointer, reverse, nextGeoRef);
        } else {
            store.setGeoRef(edgePointer, 0L);
        }
    }

    public EdgeIntAccess getEdgeAccess() {
        return store;
    }

    private void setWayGeometryAtGeoRef(PointList pillarNodes, long edgePointer, boolean reverse, long geoRef) {
        byte[] wayGeometryBytes = createWayGeometryBytes(pillarNodes, reverse);
        wayGeometry.ensureCapacity(geoRef + wayGeometryBytes.length);
        wayGeometry.setBytes(geoRef, wayGeometryBytes, wayGeometryBytes.length);
        store.setGeoRef(edgePointer, geoRef);
    }

    private byte[] createWayGeometryBytes(PointList pillarNodes, boolean reverse) {
        int len = pillarNodes.size();
        int totalLen = 3 + len * (8 + eleBytesPerCoord);
        if ((totalLen & 0xFF00_0000) != 0)
            throw new IllegalArgumentException("too long way geometry " + totalLen + ", " + len);

        byte[] bytes = new byte[totalLen];
        bitUtil.fromUInt3(bytes, len, 0);
        if (reverse)
            pillarNodes.reverse();

        int tmpOffset = 3;
        boolean is3D = nodeAccess.is3D();
        for (int i = 0; i < len; i++) {
            double lat = pillarNodes.getLat(i);
            bitUtil.fromInt(bytes, Helper.degreeToInt(lat), tmpOffset);
            tmpOffset += 4;
            bitUtil.fromInt(bytes, Helper.degreeToInt(pillarNodes.getLon(i)), tmpOffset);
            tmpOffset += 4;

            if (is3D) {
                bitUtil.fromUInt3(bytes, Helper.eleToUInt(pillarNodes.getEle(i)), tmpOffset);
                tmpOffset += 3;
            }
        }
        return bytes;
    }

    private int getPillarCount(long geoRef) {
        return (wayGeometry.getByte(geoRef + 2) & 0xFF << 16) | wayGeometry.getShort(geoRef);
    }

    private PointList fetchWayGeometry_(long edgePointer, boolean reverse, FetchMode mode, int baseNode, int adjNode) {
        if (mode == FetchMode.TOWER_ONLY) {
            // no reverse handling required as adjNode and baseNode is already properly switched
            PointList pillarNodes = new PointList(2, nodeAccess.is3D());
            pillarNodes.add(nodeAccess, baseNode);
            pillarNodes.add(nodeAccess, adjNode);
            return pillarNodes;
        }
        long geoRef = store.getGeoRef(edgePointer);
        int count = 0;
        byte[] bytes = null;
        if (geoRef > 0) {
            count = getPillarCount(geoRef);
            geoRef += 3L;
            bytes = new byte[count * (8 + eleBytesPerCoord)];
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
                pillarNodes.add(lat, lon, Helper.uIntToEle(bitUtil.toUInt3(bytes, index)));
                index += 3;
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

    private long nextGeoRef(int bytes) {
        long tmp = maxGeoRef;
        maxGeoRef += bytes;
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
        private final int bytesForFlags;
        private Directory directory = new RAMDirectory();
        private boolean withElevation = false;
        private boolean withTurnCosts = false;
        private long bytes = 100;
        private int segmentSize = -1;

        public Builder(EncodingManager em) {
            this(em.getBytesForFlags());
            withTurnCosts(em.needsTurnCostsSupport());
        }

        public Builder(int bytesForFlags) {
            this.bytesForFlags = bytesForFlags;
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
            return new BaseGraph(directory, withElevation, withTurnCosts, segmentSize, bytesForFlags);
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
            edgeIntAccess = baseGraph.getEdgeAccess();
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

            if (edgeKey % 2 == 0) {
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
            IntsRef edgeFlags = store.createEdgeFlags();
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
            return GHUtility.createEdgeKey(edgeId, reverse);
        }

        @Override
        public int getReverseEdgeKey() {
            return GHUtility.reverseEdgeKey(getEdgeKey());
        }

        @Override
        public EdgeIteratorState setKeyValues(Map<String, KVStorage.KValue> entries) {
            long pointer = baseGraph.edgeKVStorage.add(entries);
            if (pointer > MAX_UNSIGNED_INT)
                throw new IllegalStateException("Too many key value pairs are stored, currently limited to " + MAX_UNSIGNED_INT + " was " + pointer);
            store.setKeyValuesRef(edgePointer, BitUtil.toSignedInt(pointer));
            return this;
        }

        @Override
        public Map<String, KVStorage.KValue> getKeyValues() {
            long kvEntryRef = Integer.toUnsignedLong(store.getKeyValuesRef(edgePointer));
            return baseGraph.edgeKVStorage.getAll(kvEntryRef);
        }

        @Override
        public Object getValue(String key) {
            long kvEntryRef = Integer.toUnsignedLong(store.getKeyValuesRef(edgePointer));
            return baseGraph.edgeKVStorage.get(kvEntryRef, key, reverse);
        }

        @Override
        public String getName() {
            String name = (String) getValue(STREET_NAME);
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
