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

import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.IntEncodedValue;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.search.StringIndex;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.BBox;

import java.util.Collections;
import java.util.Locale;

import static com.graphhopper.util.Helper.nf;

/**
 * The base graph handles nodes and edges file format. It can be used with different Directory
 * implementations like RAMDirectory for fast access or via MMapDirectory for virtual-memory and not
 * thread safe usage.
 * <p>
 * Note: A RAM DataAccess Object is thread-safe in itself but if used in this Graph implementation
 * it is not write thread safe.
 * <p>
 * Life cycle: (1) object creation, (2) configuration via setters & getters, (3) create or
 * loadExisting, (4) usage, (5) flush, (6) close
 */
class BaseGraph implements Graph {
    // Currently distances are stored as 4 byte integers. using a conversion factor of 1000 the minimum distance
    // that is not considered zero is 0.0005m (=0.5mm) and the maximum distance per edge is about 2.147.483m=2147km.
    // See OSMReader.addEdge and #1871.
    private static final double INT_DIST_FACTOR = 1000d;
    static double MAX_DIST = Integer.MAX_VALUE / INT_DIST_FACTOR;

    final DataAccess edges;
    final DataAccess nodes;
    final BBox bounds;
    final NodeAccess nodeAccess;
    private final static String STRING_IDX_NAME_KEY = "name";
    final StringIndex stringIndex;
    // can be null if turn costs are not supported
    final TurnCostStorage turnCostStorage;
    final BitUtil bitUtil;
    final EncodingManager encodingManager;
    final EdgeAccess edgeAccess;
    private final int intsForFlags;
    // length | nodeA | nextNode | ... | nodeB
    // as we use integer index in 'egdes' area => 'geometry' area is limited to 4GB (we use pos&neg values!)
    private final DataAccess wayGeometry;
    private final Directory dir;
    private final InternalGraphEventListener listener;
    /**
     * interval [0,n)
     */
    protected int edgeCount;
    // node memory layout:
    protected int N_EDGE_REF, N_LAT, N_LON, N_ELE, N_TC;
    // edge memory layout not found in EdgeAccess:
    int E_DIST, E_GEO, E_NAME;
    /**
     * Specifies how many entries (integers) are used per edge.
     */
    int edgeEntryBytes;
    /**
     * Specifies how many entries (integers) are used per node
     */
    int nodeEntryBytes;
    private boolean initialized = false;
    /**
     * interval [0,n)
     */
    private int nodeCount;
    private int edgeEntryIndex, nodeEntryIndex;
    private long maxGeoRef;
    private boolean frozen = false;

    public BaseGraph(Directory dir, final EncodingManager encodingManager, boolean withElevation,
                     InternalGraphEventListener listener, boolean withTurnCosts, int segmentSize) {
        this.dir = dir;
        this.encodingManager = encodingManager;
        this.intsForFlags = encodingManager.getIntsForFlags();
        this.bitUtil = BitUtil.get(dir.getByteOrder());
        this.wayGeometry = dir.find("geometry");
        this.stringIndex = new StringIndex(dir);
        this.nodes = dir.find("nodes", DAType.getPreferredInt(dir.getDefaultType()));
        this.edges = dir.find("edges", DAType.getPreferredInt(dir.getDefaultType()));
        this.listener = listener;
        this.edgeAccess = new EdgeAccess(edges) {
            @Override
            final int getEdgeRef(int nodeId) {
                return nodes.getInt((long) nodeId * nodeEntryBytes + N_EDGE_REF);
            }

            @Override
            final void setEdgeRef(int nodeId, int edgeId) {
                nodes.setInt((long) nodeId * nodeEntryBytes + N_EDGE_REF, edgeId);
            }

            @Override
            final int getEntryBytes() {
                return edgeEntryBytes;
            }

            @Override
            final long toPointer(int edgeId) {
                assert isInBounds(edgeId) : "edgeId " + edgeId + " not in bounds [0," + edgeCount + ")";
                return (long) edgeId * edgeEntryBytes;
            }

            @Override
            final boolean isInBounds(int edgeId) {
                return edgeId < edgeCount && edgeId >= 0;
            }

            @Override
            public String toString() {
                return "base edge access";
            }
        };
        this.bounds = BBox.createInverse(withElevation);
        this.nodeAccess = new GHNodeAccess(this, withElevation);
        if (withTurnCosts) {
            turnCostStorage = new TurnCostStorage(this, dir.find("turn_costs"));
        } else {
            turnCostStorage = null;
        }
        if (segmentSize >= 0) {
            setSegmentSize(segmentSize);
        }
    }

    private static boolean isTestingEnabled() {
        boolean enableIfAssert = false;
        assert (enableIfAssert = true) : true;
        return enableIfAssert;
    }

    @Override
    public Graph getBaseGraph() {
        return this;
    }

    void checkNotInitialized() {
        if (initialized)
            throw new IllegalStateException("You cannot configure this GraphStorage "
                    + "after calling create or loadExisting. Calling one of the methods twice is also not allowed.");
    }

    void checkInitialized() {
        if (!initialized)
            throw new IllegalStateException("The graph has not yet been initialized.");
    }

    protected int loadNodesHeader() {
        nodeEntryBytes = nodes.getHeader(1 * 4);
        nodeCount = nodes.getHeader(2 * 4);
        bounds.minLon = Helper.intToDegree(nodes.getHeader(3 * 4));
        bounds.maxLon = Helper.intToDegree(nodes.getHeader(4 * 4));
        bounds.minLat = Helper.intToDegree(nodes.getHeader(5 * 4));
        bounds.maxLat = Helper.intToDegree(nodes.getHeader(6 * 4));

        if (bounds.hasElevation()) {
            bounds.minEle = Helper.intToEle(nodes.getHeader(7 * 4));
            bounds.maxEle = Helper.intToEle(nodes.getHeader(8 * 4));
        }

        frozen = nodes.getHeader(9 * 4) == 1;
        return 10;
    }

    protected int setNodesHeader() {
        nodes.setHeader(1 * 4, nodeEntryBytes);
        nodes.setHeader(2 * 4, nodeCount);
        nodes.setHeader(3 * 4, Helper.degreeToInt(bounds.minLon));
        nodes.setHeader(4 * 4, Helper.degreeToInt(bounds.maxLon));
        nodes.setHeader(5 * 4, Helper.degreeToInt(bounds.minLat));
        nodes.setHeader(6 * 4, Helper.degreeToInt(bounds.maxLat));
        if (bounds.hasElevation()) {
            nodes.setHeader(7 * 4, Helper.eleToInt(bounds.minEle));
            nodes.setHeader(8 * 4, Helper.eleToInt(bounds.maxEle));
        }

        nodes.setHeader(9 * 4, isFrozen() ? 1 : 0);
        return 10;
    }

    protected int loadEdgesHeader() {
        edgeEntryBytes = edges.getHeader(0 * 4);
        edgeCount = edges.getHeader(1 * 4);
        return 5;
    }

    protected int setEdgesHeader() {
        edges.setHeader(0, edgeEntryBytes);
        edges.setHeader(1 * 4, edgeCount);
        edges.setHeader(2 * 4, encodingManager.hashCode());
        edges.setHeader(3 * 4, supportsTurnCosts() ? turnCostStorage.hashCode() : -1);
        return 5;
    }

    protected int loadWayGeometryHeader() {
        maxGeoRef = bitUtil.combineIntsToLong(wayGeometry.getHeader(0), wayGeometry.getHeader(4));
        return 1;
    }

    protected int setWayGeometryHeader() {
        wayGeometry.setHeader(0, bitUtil.getIntLow(maxGeoRef));
        wayGeometry.setHeader(4, bitUtil.getIntHigh(maxGeoRef));
        return 1;
    }

    void initStorage() {
        edgeEntryIndex = 0;
        nodeEntryIndex = 0;
        edgeAccess.init(nextEdgeEntryIndex(4),
                nextEdgeEntryIndex(4),
                nextEdgeEntryIndex(4),
                nextEdgeEntryIndex(4),
                nextEdgeEntryIndex(encodingManager.getIntsForFlags() * 4));

        E_DIST = nextEdgeEntryIndex(4);
        E_GEO = nextEdgeEntryIndex(4);
        E_NAME = nextEdgeEntryIndex(4);

        N_EDGE_REF = nextNodeEntryIndex(4);
        N_LAT = nextNodeEntryIndex(4);
        N_LON = nextNodeEntryIndex(4);
        if (nodeAccess.is3D())
            N_ELE = nextNodeEntryIndex(4);
        else
            N_ELE = -1;

        if (supportsTurnCosts())
            N_TC = nextNodeEntryIndex(4);
        else
            N_TC = -1;

        initNodeAndEdgeEntrySize();
        listener.initStorage();
        initialized = true;
    }

    boolean supportsTurnCosts() {
        return turnCostStorage != null;
    }

    /**
     * Initializes the node storage such that each node has no edge and no turn cost entry
     */
    void initNodeRefs(long oldCapacity, long newCapacity) {
        for (long pointer = oldCapacity + N_EDGE_REF; pointer < newCapacity; pointer += nodeEntryBytes) {
            nodes.setInt(pointer, EdgeIterator.NO_EDGE);
        }
        if (supportsTurnCosts()) {
            for (long pointer = oldCapacity + N_TC; pointer < newCapacity; pointer += nodeEntryBytes) {
                nodes.setInt(pointer, TurnCostStorage.NO_TURN_ENTRY);
            }
        }
    }

    protected final int nextEdgeEntryIndex(int sizeInBytes) {
        int tmp = edgeEntryIndex;
        edgeEntryIndex += sizeInBytes;
        return tmp;
    }

    protected final int nextNodeEntryIndex(int sizeInBytes) {
        int tmp = nodeEntryIndex;
        nodeEntryIndex += sizeInBytes;
        return tmp;
    }

    protected final void initNodeAndEdgeEntrySize() {
        nodeEntryBytes = nodeEntryIndex;
        edgeEntryBytes = edgeEntryIndex;
    }

    /**
     * Check if byte capacity of DataAcess nodes object is sufficient to include node index, else
     * extend byte capacity
     */
    final void ensureNodeIndex(int nodeIndex) {
        checkInitialized();

        if (nodeIndex < nodeCount)
            return;

        long oldNodes = nodeCount;
        nodeCount = nodeIndex + 1;
        boolean capacityIncreased = nodes.ensureCapacity((long) nodeCount * nodeEntryBytes);
        if (capacityIncreased) {
            long newBytesCapacity = nodes.getCapacity();
            initNodeRefs(oldNodes * nodeEntryBytes, newBytesCapacity);
        }
    }

    @Override
    public int getNodes() {
        return nodeCount;
    }

    @Override
    public int getEdges() {
        return edgeCount;
    }

    @Override
    public NodeAccess getNodeAccess() {
        return nodeAccess;
    }

    @Override
    public BBox getBounds() {
        return bounds;
    }

    @Override
    public EdgeIteratorState edge(int a, int b, double distance, boolean bothDirection) {
        return edge(a, b).setDistance(distance).setFlags(encodingManager.flagsDefault(true, bothDirection));
    }

    private void setSegmentSize(int bytes) {
        checkNotInitialized();
        nodes.setSegmentSize(bytes);
        edges.setSegmentSize(bytes);
        wayGeometry.setSegmentSize(bytes);
        stringIndex.setSegmentSize(bytes);
        if (supportsTurnCosts()) {
            turnCostStorage.setSegmentSize(bytes);
        }
    }

    synchronized void freeze() {
        if (isFrozen())
            throw new IllegalStateException("base graph already frozen");

        frozen = true;
        listener.freeze();
    }

    synchronized boolean isFrozen() {
        return frozen;
    }

    public void checkFreeze() {
        if (isFrozen())
            throw new IllegalStateException("Cannot add edge or node after baseGraph.freeze was called");
    }

    void create(long initSize) {
        nodes.create(initSize);
        edges.create(initSize);

        initSize = Math.min(initSize, 2000);
        wayGeometry.create(initSize);
        stringIndex.create(initSize);
        if (supportsTurnCosts()) {
            turnCostStorage.create(initSize);
        }
        initStorage();
        // 0 stands for no separate geoRef
        maxGeoRef = 4;

        initNodeRefs(0, nodes.getCapacity());
    }

    String toDetailsString() {
        return "edges:" + nf(edgeCount) + "(" + edges.getCapacity() / Helper.MB + "MB), "
                + "nodes:" + nf(getNodes()) + "(" + nodes.getCapacity() / Helper.MB + "MB), "
                + "name:(" + stringIndex.getCapacity() / Helper.MB + "MB), "
                + "geo:" + nf(maxGeoRef) + "(" + wayGeometry.getCapacity() / Helper.MB + "MB), "
                + "bounds:" + bounds;
    }

    public void debugPrint() {
        final int printMax = 100;
        System.out.println("nodes:");
        String formatNodes = "%12s | %12s | %12s | %12s \n";
        System.out.format(Locale.ROOT, formatNodes, "#", "N_EDGE_REF", "N_LAT", "N_LON");
        NodeAccess nodeAccess = getNodeAccess();
        for (int i = 0; i < Math.min(nodeCount, printMax); ++i) {
            System.out.format(Locale.ROOT, formatNodes, i, edgeAccess.getEdgeRef(i), nodeAccess.getLat(i), nodeAccess.getLon(i));
        }
        if (nodeCount > printMax) {
            System.out.format(Locale.ROOT, " ... %d more nodes\n", nodeCount - printMax);
        }
        System.out.println("edges:");
        String formatEdges = "%12s | %12s | %12s | %12s | %12s | %12s | %12s \n";
        System.out.format(Locale.ROOT, formatEdges, "#", "E_NODEA", "E_NODEB", "E_LINKA", "E_LINKB", "E_FLAGS", "E_DIST");
        IntsRef intsRef = new IntsRef(intsForFlags);
        for (int i = 0; i < Math.min(edgeCount, printMax); ++i) {
            long edgePointer = edgeAccess.toPointer(i);
            edgeAccess.readFlags(edgePointer, intsRef);
            System.out.format(Locale.ROOT, formatEdges, i,
                    edgeAccess.getNodeA(edgePointer),
                    edgeAccess.getNodeB(edgePointer),
                    edgeAccess.getLinkA(edgePointer),
                    edgeAccess.getLinkB(edgePointer),
                    intsRef,
                    getDist(edgePointer));
        }
        if (edgeCount > printMax) {
            System.out.printf(Locale.ROOT, " ... %d more edges", edgeCount - printMax);
        }
    }

    /**
     * Flush and free resources that are not needed for post-processing (way geometries and name index).
     */
    void flushAndCloseGeometryAndNameStorage() {
        setWayGeometryHeader();

        wayGeometry.flush();
        wayGeometry.close();

        stringIndex.flush();
        stringIndex.close();
    }

    public void flush() {
        if (!wayGeometry.isClosed()) {
            setWayGeometryHeader();
            wayGeometry.flush();
        }

        if (!stringIndex.isClosed())
            stringIndex.flush();

        setNodesHeader();
        setEdgesHeader();
        edges.flush();
        nodes.flush();
        if (supportsTurnCosts()) {
            turnCostStorage.flush();
        }
    }

    public void close() {
        if (!wayGeometry.isClosed())
            wayGeometry.close();
        if (!stringIndex.isClosed())
            stringIndex.close();
        edges.close();
        nodes.close();
        if (supportsTurnCosts()) {
            turnCostStorage.close();
        }
    }

    long getCapacity() {
        return edges.getCapacity() + nodes.getCapacity() + stringIndex.getCapacity()
                + wayGeometry.getCapacity() + (supportsTurnCosts() ? turnCostStorage.getCapacity() : 0);
    }

    long getMaxGeoRef() {
        return maxGeoRef;
    }

    void loadExisting(String dim) {
        if (!nodes.loadExisting())
            throw new IllegalStateException("Cannot load nodes. corrupt file or directory? " + dir);

        if (!dim.equalsIgnoreCase("" + nodeAccess.getDimension()))
            throw new IllegalStateException("Configured dimension (" + nodeAccess.getDimension() + ") is not equal "
                    + "to dimension of loaded graph (" + dim + ")");

        if (!edges.loadExisting())
            throw new IllegalStateException("Cannot load edges. corrupt file or directory? " + dir);

        if (!wayGeometry.loadExisting())
            throw new IllegalStateException("Cannot load geometry. corrupt file or directory? " + dir);

        if (!stringIndex.loadExisting())
            throw new IllegalStateException("Cannot load name index. corrupt file or directory? " + dir);

        if (supportsTurnCosts() && !turnCostStorage.loadExisting())
            throw new IllegalStateException("Cannot load turn cost storage. corrupt file or directory? " + dir);

        // first define header indices of this storage
        initStorage();

        // now load some properties from stored data
        loadNodesHeader();
        loadEdgesHeader();
        loadWayGeometryHeader();
    }

    /**
     * This method copies the properties of one {@link EdgeIteratorState} to another.
     *
     * @return the updated iterator the properties where copied to.
     */
    EdgeIteratorState copyProperties(EdgeIteratorState from, EdgeIteratorStateImpl to) {
        long edgePointer = edgeAccess.toPointer(to.getEdge());
        edgeAccess.writeFlags(edgePointer, from.getFlags());

        // copy the rest with higher level API
        to.setDistance(from.getDistance()).
                setName(from.getName()).
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

        ensureNodeIndex(Math.max(nodeA, nodeB));
        int edgeId = edgeAccess.internalEdgeAdd(nextEdgeId(), nodeA, nodeB, true);
        EdgeIteratorStateImpl edge = new EdgeIteratorStateImpl(edgeAccess, this);
        boolean valid = edge.init(edgeId, nodeB);
        assert valid;
        return edge;
    }

    // for test only
    void setEdgeCount(int cnt) {
        edgeCount = cnt;
    }

    /**
     * Determine next free edgeId and ensure byte capacity to store edge
     *
     * @return next free edgeId
     */
    protected int nextEdgeId() {
        int nextEdge = edgeCount;
        edgeCount++;
        if (edgeCount < 0)
            throw new IllegalStateException("too many edges. new edge id would be negative. " + toString());

        edges.ensureCapacity(((long) edgeCount + 1) * edgeEntryBytes);
        return nextEdge;
    }

    @Override
    public EdgeIteratorState getEdgeIteratorState(int edgeId, int adjNode) {
        if (!edgeAccess.isInBounds(edgeId))
            throw new IllegalStateException("edgeId " + edgeId + " out of bounds");
        checkAdjNodeBounds(adjNode);
        EdgeIteratorStateImpl edge = new EdgeIteratorStateImpl(edgeAccess, this);
        if (edge.init(edgeId, adjNode))
            return edge;
        // if edgeId exists but adjacent nodes do not match
        return null;
    }

    final void checkAdjNodeBounds(int adjNode) {
        if (adjNode < 0 && adjNode != Integer.MIN_VALUE || adjNode >= nodeCount)
            throw new IllegalStateException("adjNode " + adjNode + " out of bounds [0," + nf(nodeCount) + ")");
    }

    @Override
    public EdgeExplorer createEdgeExplorer(EdgeFilter filter) {
        return new EdgeIteratorImpl(this, edgeAccess, filter);
    }

    @Override
    public EdgeExplorer createEdgeExplorer() {
        return createEdgeExplorer(EdgeFilter.ALL_EDGES);
    }

    @Override
    public AllEdgesIterator getAllEdges() {
        return new AllEdgeIterator(this, edgeAccess);
    }

    @Override
    public Graph copyTo(Graph g) {
        initialized = true;
        if (g.getClass().equals(getClass())) {
            _copyTo((BaseGraph) g);
            return g;
        } else {
            return GHUtility.copyTo(this, g);
        }
    }

    void _copyTo(BaseGraph clonedG) {
        if (clonedG.edgeEntryBytes != edgeEntryBytes)
            throw new IllegalStateException("edgeEntryBytes cannot be different for cloned graph. "
                    + "Cloned: " + clonedG.edgeEntryBytes + " vs " + edgeEntryBytes);

        if (clonedG.nodeEntryBytes != nodeEntryBytes)
            throw new IllegalStateException("nodeEntryBytes cannot be different for cloned graph. "
                    + "Cloned: " + clonedG.nodeEntryBytes + " vs " + nodeEntryBytes);

        if (clonedG.nodeAccess.getDimension() != nodeAccess.getDimension())
            throw new IllegalStateException("dimension cannot be different for cloned graph. "
                    + "Cloned: " + clonedG.nodeAccess.getDimension() + " vs " + nodeAccess.getDimension());

        // nodes
        setNodesHeader();
        nodes.copyTo(clonedG.nodes);
        clonedG.loadNodesHeader();

        // edges
        setEdgesHeader();
        edges.copyTo(clonedG.edges);
        clonedG.loadEdgesHeader();

        // name
        stringIndex.copyTo(clonedG.stringIndex);

        // geometry
        setWayGeometryHeader();
        wayGeometry.copyTo(clonedG.wayGeometry);
        clonedG.loadWayGeometryHeader();

        // turn cost storage
        if (supportsTurnCosts()) {
            turnCostStorage.copyTo(clonedG.turnCostStorage);
        }
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
        long edgePointer = edgeAccess.toPointer(edge);
        return edgeAccess.getOtherNode(node, edgePointer);
    }

    @Override
    public boolean isAdjacentToNode(int edge, int node) {
        long edgePointer = edgeAccess.toPointer(edge);
        return edgeAccess.isAdjacentToNode(node, edgePointer);
    }


    private void setDist(long edgePointer, double distance) {
        edges.setInt(edgePointer + E_DIST, distToInt(distance));
    }

    /**
     * Translates double distance to integer in order to save it in a DataAccess object
     */
    private int distToInt(double distance) {
        if (distance < 0)
            throw new IllegalArgumentException("Distance cannot be negative: " + distance);
        if (distance > MAX_DIST) {
            distance = MAX_DIST;
        }
        int integ = (int) Math.round(distance * INT_DIST_FACTOR);
        assert integ >= 0 : "distance out of range";
        return integ;
    }

    /**
     * returns distance (already translated from integer to double)
     */
    private double getDist(long pointer) {
        int val = edges.getInt(pointer + E_DIST);
        // do never return infinity even if INT MAX, see #435
        return val / INT_DIST_FACTOR;
    }

    private void setWayGeometry_(PointList pillarNodes, long edgePointer, boolean reverse) {
        if (pillarNodes != null && !pillarNodes.isEmpty()) {
            if (pillarNodes.getDimension() != nodeAccess.getDimension())
                throw new IllegalArgumentException("Cannot use pointlist which is " + pillarNodes.getDimension()
                        + "D for graph which is " + nodeAccess.getDimension() + "D");

            long existingGeoRef = Helper.toUnsignedLong(edges.getInt(edgePointer + E_GEO));

            int len = pillarNodes.getSize();
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
            edges.setInt(edgePointer + E_GEO, 0);
        }
    }

    private void setWayGeometryAtGeoRef(PointList pillarNodes, long edgePointer, boolean reverse, long geoRef) {
        int len = pillarNodes.getSize();
        int dim = nodeAccess.getDimension();
        long geoRefPosition = geoRef * 4;
        int totalLen = len * dim * 4 + 4;
        ensureGeometry(geoRefPosition, totalLen);
        byte[] wayGeometryBytes = createWayGeometryBytes(pillarNodes, reverse);
        wayGeometry.setBytes(geoRefPosition, wayGeometryBytes, wayGeometryBytes.length);
        edges.setInt(edgePointer + E_GEO, Helper.toSignedInt(geoRef));
    }

    private byte[] createWayGeometryBytes(PointList pillarNodes, boolean reverse) {
        int len = pillarNodes.getSize();
        int dim = nodeAccess.getDimension();
        int totalLen = len * dim * 4 + 4;
        byte[] bytes = new byte[totalLen];
        bitUtil.fromInt(bytes, len, 0);
        if (reverse)
            pillarNodes.reverse();

        int tmpOffset = 4;
        boolean is3D = nodeAccess.is3D();
        for (int i = 0; i < len; i++) {
            double lat = pillarNodes.getLatitude(i);
            bitUtil.fromInt(bytes, Helper.degreeToInt(lat), tmpOffset);
            tmpOffset += 4;
            bitUtil.fromInt(bytes, Helper.degreeToInt(pillarNodes.getLongitude(i)), tmpOffset);
            tmpOffset += 4;

            if (is3D) {
                bitUtil.fromInt(bytes, Helper.eleToInt(pillarNodes.getElevation(i)), tmpOffset);
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
        long geoRef = Helper.toUnsignedLong(edges.getInt(edgePointer + E_GEO));
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

    private void setName(long edgePointer, String name) {
        int stringIndexRef = (int) stringIndex.add(Collections.singletonMap(STRING_IDX_NAME_KEY, name));
        if (stringIndexRef < 0)
            throw new IllegalStateException("Too many names are stored, currently limited to int pointer");

        edges.setInt(edgePointer + E_NAME, stringIndexRef);
    }

    private void ensureGeometry(long bytePos, int byteLength) {
        wayGeometry.ensureCapacity(bytePos + byteLength);
    }

    private long nextGeoRef(int arrayLength) {
        long tmp = maxGeoRef;
        maxGeoRef += arrayLength + 1L;
        if (maxGeoRef >= 0xFFFFffffL)
            throw new IllegalStateException("Geometry too large, does not fit in 32 bits " + maxGeoRef);

        return tmp;
    }

    protected static class EdgeIteratorImpl extends EdgeIteratorStateImpl implements EdgeExplorer, EdgeIterator {
        final EdgeFilter filter;
        int nextEdgeId;

        public EdgeIteratorImpl(BaseGraph baseGraph, EdgeAccess edgeAccess, EdgeFilter filter) {
            super(edgeAccess, baseGraph);

            if (filter == null)
                throw new IllegalArgumentException("Instead null filter use EdgeFilter.ALL_EDGES");
            this.filter = filter;
        }

        final void setEdgeId(int edgeId) {
            this.nextEdgeId = this.edgeId = edgeId;
        }

        final void _setBaseNode(int baseNode) {
            this.baseNode = baseNode;
        }

        @Override
        public EdgeIterator setBaseNode(int baseNode) {
            // always use base graph edge access
            setEdgeId(baseGraph.edgeAccess.getEdgeRef(baseNode));
            _setBaseNode(baseNode);
            return this;
        }

        @Override
        public final boolean next() {
            while (true) {
                if (!EdgeIterator.Edge.isValid(nextEdgeId))
                    return false;
                goToNext();
                if (filter.accept(this)) {
                    return true;
                }
            }
        }

        void goToNext() {
            edgePointer = edgeAccess.toPointer(nextEdgeId);
            edgeId = nextEdgeId;
            int nodeA = edgeAccess.getNodeA(edgePointer);
            boolean baseNodeIsNodeA = baseNode == nodeA;
            adjNode = baseNodeIsNodeA ? edgeAccess.getNodeB(edgePointer) : nodeA;
            reverse = !baseNodeIsNodeA;
            freshFlags = false;

            // position to next edge
            nextEdgeId = baseNodeIsNodeA ? edgeAccess.getLinkA(edgePointer) : edgeAccess.getLinkB(edgePointer);
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
            this(baseGraph, baseGraph.edgeAccess);
        }

        private AllEdgeIterator(BaseGraph baseGraph, EdgeAccess edgeAccess) {
            super(edgeAccess, baseGraph);
        }

        @Override
        public int length() {
            return baseGraph.edgeCount;
        }

        @Override
        public boolean next() {
            while (true) {
                edgeId++;
                if (edgeId >= baseGraph.edgeCount)
                    return false;

                edgePointer = edgeAccess.toPointer(edgeId);
                adjNode = edgeAccess.getNodeB(edgePointer);

                baseNode = edgeAccess.getNodeA(edgePointer);
                freshFlags = false;
                reverse = false;
                return true;
            }
        }

        @Override
        public final EdgeIteratorState detach(boolean reverseArg) {
            if (edgePointer < 0)
                throw new IllegalStateException("call next before detaching");

            AllEdgeIterator iter = new AllEdgeIterator(baseGraph, edgeAccess);
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
        long edgePointer = -1;
        int baseNode;
        int adjNode;
        EdgeAccess edgeAccess;
        // we need reverse if detach is called
        boolean reverse = false;
        boolean freshFlags;
        int edgeId = -1;
        private final IntsRef edgeFlags;

        public EdgeIteratorStateImpl(EdgeAccess edgeAccess, BaseGraph baseGraph) {
            this.edgeAccess = edgeAccess;
            this.baseGraph = baseGraph;
            this.edgeFlags = new IntsRef(baseGraph.intsForFlags);
        }

        /**
         * @return false if the edge has not a node equal to expectedAdjNode
         */
        final boolean init(int edgeId, int expectedAdjNode) {
            if (!EdgeIterator.Edge.isValid(edgeId))
                throw new IllegalArgumentException("fetching the edge requires a valid edgeId but was " + edgeId);
            this.edgeId = edgeId;
            edgePointer = edgeAccess.toPointer(edgeId);
            baseNode = edgeAccess.getNodeA(edgePointer);
            adjNode = edgeAccess.getNodeB(edgePointer);
            freshFlags = false;

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
            return baseGraph.getDist(edgePointer);
        }

        @Override
        public EdgeIteratorState setDistance(double dist) {
            baseGraph.setDist(edgePointer, dist);
            return this;
        }

        @Override
        public IntsRef getFlags() {
            if (!freshFlags) {
                edgeAccess.readFlags(edgePointer, edgeFlags);
                freshFlags = true;
            }
            return edgeFlags;
        }

        @Override
        public final EdgeIteratorState setFlags(IntsRef edgeFlags) {
            assert edgeId < baseGraph.edgeCount : "must be edge but was shortcut: " + edgeId + " >= " + baseGraph.edgeCount + ". Use setFlagsAndWeight";
            edgeAccess.writeFlags(edgePointer, edgeFlags);
            for (int i = 0; i < edgeFlags.ints.length; i++) {
                this.edgeFlags.ints[i] = edgeFlags.ints[i];
            }
            freshFlags = true;
            return this;
        }

        @Override
        public boolean get(BooleanEncodedValue property) {
            return property.getBool(reverse, getFlags());
        }

        @Override
        public EdgeIteratorState set(BooleanEncodedValue property, boolean value) {
            property.setBool(reverse, getFlags(), value);
            edgeAccess.writeFlags(edgePointer, getFlags());
            return this;
        }

        @Override
        public boolean getReverse(BooleanEncodedValue property) {
            return property.getBool(!reverse, getFlags());
        }

        @Override
        public EdgeIteratorState setReverse(BooleanEncodedValue property, boolean value) {
            property.setBool(!reverse, getFlags(), value);
            edgeAccess.writeFlags(edgePointer, getFlags());
            return this;
        }

        @Override
        public int get(IntEncodedValue property) {
            return property.getInt(reverse, getFlags());
        }

        @Override
        public EdgeIteratorState set(IntEncodedValue property, int value) {
            property.setInt(reverse, getFlags(), value);
            edgeAccess.writeFlags(edgePointer, getFlags());
            return this;
        }

        @Override
        public int getReverse(IntEncodedValue property) {
            return property.getInt(!reverse, getFlags());
        }

        @Override
        public EdgeIteratorState setReverse(IntEncodedValue property, int value) {
            property.setInt(!reverse, getFlags(), value);
            edgeAccess.writeFlags(edgePointer, getFlags());
            return this;
        }

        @Override
        public double get(DecimalEncodedValue property) {
            return property.getDecimal(reverse, getFlags());
        }

        @Override
        public EdgeIteratorState set(DecimalEncodedValue property, double value) {
            property.setDecimal(reverse, getFlags(), value);
            edgeAccess.writeFlags(edgePointer, getFlags());
            return this;
        }

        @Override
        public double getReverse(DecimalEncodedValue property) {
            return property.getDecimal(!reverse, getFlags());
        }

        @Override
        public EdgeIteratorState setReverse(DecimalEncodedValue property, double value) {
            property.setDecimal(!reverse, getFlags(), value);
            edgeAccess.writeFlags(edgePointer, getFlags());
            return this;
        }

        @Override
        public <T extends Enum> T get(EnumEncodedValue<T> property) {
            return property.getEnum(reverse, getFlags());
        }

        @Override
        public <T extends Enum> EdgeIteratorState set(EnumEncodedValue<T> property, T value) {
            property.setEnum(reverse, getFlags(), value);
            edgeAccess.writeFlags(edgePointer, getFlags());
            return this;
        }

        @Override
        public <T extends Enum> T getReverse(EnumEncodedValue<T> property) {
            return property.getEnum(!reverse, getFlags());
        }

        @Override
        public <T extends Enum> EdgeIteratorState setReverse(EnumEncodedValue<T> property, T value) {
            property.setEnum(!reverse, getFlags(), value);
            edgeAccess.writeFlags(edgePointer, getFlags());
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
        public int getOrigEdgeFirst() {
            return getEdge();
        }

        @Override
        public int getOrigEdgeLast() {
            return getEdge();
        }

        @Override
        public String getName() {
            int stringIndexRef = baseGraph.edges.getInt(edgePointer + baseGraph.E_NAME);
            String name = baseGraph.stringIndex.get(stringIndexRef, STRING_IDX_NAME_KEY);
            // preserve backward compatibility (returns null if not explicitly set)
            return name == null ? "" : name;
        }

        @Override
        public EdgeIteratorState setName(String name) {
            baseGraph.setName(edgePointer, name);
            return this;
        }

        @Override
        public EdgeIteratorState detach(boolean reverseArg) {
            if (!EdgeIterator.Edge.isValid(edgeId))
                throw new IllegalStateException("call setEdgeId before detaching (edgeId:" + edgeId + ")");
            EdgeIteratorStateImpl edge = new EdgeIteratorStateImpl(edgeAccess, baseGraph);
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
