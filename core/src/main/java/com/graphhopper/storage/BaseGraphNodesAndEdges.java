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

import com.graphhopper.util.*;
import com.graphhopper.util.shapes.BBox;
import org.slf4j.LoggerFactory;

import java.util.Locale;

import static com.graphhopper.util.EdgeIterator.NO_EDGE;
import static com.graphhopper.util.Helper.nf;

/**
 * Underlying storage for nodes and edges of {@link BaseGraph}. Nodes and edges are stored using two {@link DataAccess}
 * instances. Nodes and edges are simply stored sequentially, see the memory layout in the constructor.
 */
class BaseGraphNodesAndEdges {
    // Currently distances are stored as 4 byte integers. using a conversion factor of 1000 the minimum distance
    // that is not considered zero is 0.0005m (=0.5mm) and the maximum distance per edge is about 2.147.483m=2147km.
    // See OSMReader.addEdge and #1871.
    private static final double INT_DIST_FACTOR = 1000d;
    static double MAX_DIST = Integer.MAX_VALUE / INT_DIST_FACTOR;

    // nodes
    private final DataAccess nodes;
    private final int N_EDGE_REF, N_LAT, N_LON, N_ELE, N_TC;
    private int nodeEntryBytes;
    private int nodeCount;

    // edges
    private final DataAccess edges;
    private final int E_NODEA, E_NODEB, E_LINKA, E_LINKB, E_DIST, E_KV, E_FLAGS, E_GEO;
    private final int bytesForFlags;
    private int edgeEntryBytes;
    private int edgeCount;

    private final boolean withTurnCosts;
    private final boolean withElevation;

    // we do not write the bounding box directly to storage, but rather to this bbox object. we only write to storage
    // when flushing. why? just because we did it like this in the past, and otherwise we run into rounding errors,
    // because of: #2393
    public final BBox bounds;
    private boolean frozen;

    public BaseGraphNodesAndEdges(Directory dir, boolean withElevation, boolean withTurnCosts, int segmentSize, int bytesForFlags) {
        nodes = dir.create("nodes", dir.getDefaultType("nodes", true), segmentSize);
        edges = dir.create("edges", dir.getDefaultType("edges", false), segmentSize);
        this.bytesForFlags = bytesForFlags;
        this.withTurnCosts = withTurnCosts;
        this.withElevation = withElevation;
        bounds = BBox.createInverse(withElevation);

        // memory layout for nodes
        N_EDGE_REF = 0;
        N_LAT = 4;
        N_LON = 8;
        N_ELE = N_LON + (withElevation ? 4 : 0);
        N_TC = N_ELE + (withTurnCosts ? 4 : 0);
        nodeEntryBytes = N_TC + 4;

        // memory layout for edges
        E_NODEA = 0;
        E_NODEB = 4;
        E_LINKA = 8;
        E_LINKB = 12;
        E_DIST = 16;
        E_KV = 20;
        E_FLAGS = 24;
        E_GEO = E_FLAGS + bytesForFlags + 4;
        edgeEntryBytes = E_GEO + 5;

        // TODO make padding unnecessary via the DataAccess implementations set/getInt/Short methods
        // padding must be correct for next line
        int padding = (4 - edgeEntryBytes % 4) % 4;
        if (padding > 0)
            LoggerFactory.getLogger(BaseGraphNodesAndEdges.class).warn("base graph padding of " + padding + " bytes increases storage size");

        edgeEntryBytes += padding;
    }

    public void create(long initSize) {
        nodes.create(initSize);
        edges.create(initSize);
    }

    public boolean loadExisting() {
        if (!nodes.loadExisting() || !edges.loadExisting())
            return false;

        // now load some properties from stored data
        final int nodesVersion = nodes.getHeader(0 * 4);
        GHUtility.checkDAVersion("nodes", Constants.VERSION_NODE, nodesVersion);
        nodeEntryBytes = nodes.getHeader(1 * 4);
        nodeCount = nodes.getHeader(2 * 4);
        bounds.minLon = Helper.intToDegree(nodes.getHeader(3 * 4));
        bounds.maxLon = Helper.intToDegree(nodes.getHeader(4 * 4));
        bounds.minLat = Helper.intToDegree(nodes.getHeader(5 * 4));
        bounds.maxLat = Helper.intToDegree(nodes.getHeader(6 * 4));
        boolean hasElevation = nodes.getHeader(7 * 4) == 1;
        if (hasElevation != withElevation)
            // :( we should load data from disk to create objects, not the other way around!
            throw new IllegalStateException("Configured dimension elevation=" + withElevation + " is not equal "
                    + "to dimension of loaded graph elevation =" + hasElevation);
        if (withElevation) {
            bounds.minEle = Helper.intToEle(nodes.getHeader(8 * 4));
            bounds.maxEle = Helper.intToEle(nodes.getHeader(9 * 4));
        }
        frozen = nodes.getHeader(10 * 4) == 1;

        final int edgesVersion = edges.getHeader(0 * 4);
        GHUtility.checkDAVersion("edges", Constants.VERSION_EDGE, edgesVersion);
        edgeEntryBytes = edges.getHeader(1 * 4);
        edgeCount = edges.getHeader(2 * 4);
        return true;
    }

    public void flush() {
        nodes.setHeader(0 * 4, Constants.VERSION_NODE);
        nodes.setHeader(1 * 4, nodeEntryBytes);
        nodes.setHeader(2 * 4, nodeCount);
        nodes.setHeader(3 * 4, Helper.degreeToInt(bounds.minLon));
        nodes.setHeader(4 * 4, Helper.degreeToInt(bounds.maxLon));
        nodes.setHeader(5 * 4, Helper.degreeToInt(bounds.minLat));
        nodes.setHeader(6 * 4, Helper.degreeToInt(bounds.maxLat));
        nodes.setHeader(7 * 4, withElevation ? 1 : 0);
        if (withElevation) {
            nodes.setHeader(8 * 4, Helper.eleToInt(bounds.minEle));
            nodes.setHeader(9 * 4, Helper.eleToInt(bounds.maxEle));
        }
        nodes.setHeader(10 * 4, frozen ? 1 : 0);

        edges.setHeader(0 * 4, Constants.VERSION_EDGE);
        edges.setHeader(1 * 4, edgeEntryBytes);
        edges.setHeader(2 * 4, edgeCount);

        edges.flush();
        nodes.flush();
    }

    public void close() {
        edges.close();
        nodes.close();
    }

    public int getNodes() {
        return nodeCount;
    }

    public int getEdges() {
        return edgeCount;
    }

    public int getBytesForFlags() {
        return bytesForFlags;
    }

    public boolean withElevation() {
        return withElevation;
    }

    public boolean withTurnCosts() {
        return withTurnCosts;
    }

    public BBox getBounds() {
        return bounds;
    }

    public long getCapacity() {
        return nodes.getCapacity() + edges.getCapacity();
    }

    public boolean isClosed() {
        assert nodes.isClosed() == edges.isClosed();
        return nodes.isClosed();
    }

    public int edge(int nodeA, int nodeB) {
        if (edgeCount == Integer.MAX_VALUE)
            throw new IllegalStateException("Maximum edge count exceeded: " + edgeCount);
        if (nodeA == nodeB)
            throw new IllegalArgumentException("Loop edges are not supported, got: " + nodeA + " - " + nodeB);
        ensureNodeCapacity(Math.max(nodeA, nodeB));
        final int edge = edgeCount;
        final long edgePointer = (long) edgeCount * edgeEntryBytes;
        edgeCount++;
        edges.ensureCapacity((long) edgeCount * edgeEntryBytes);

        setNodeA(edgePointer, nodeA);
        setNodeB(edgePointer, nodeB);
        // we keep a linked list of edges at each node. here we prepend the new edge at the already existing linked
        // list of edges.
        long nodePointerA = toNodePointer(nodeA);
        int edgeRefA = getEdgeRef(nodePointerA);
        setLinkA(edgePointer, EdgeIterator.Edge.isValid(edgeRefA) ? edgeRefA : NO_EDGE);
        setEdgeRef(nodePointerA, edge);

        if (nodeA != nodeB) {
            long nodePointerB = toNodePointer(nodeB);
            int edgeRefB = getEdgeRef(nodePointerB);
            setLinkB(edgePointer, EdgeIterator.Edge.isValid(edgeRefB) ? edgeRefB : NO_EDGE);
            setEdgeRef(nodePointerB, edge);
        }
        return edge;
    }

    public void ensureNodeCapacity(int node) {
        if (node < nodeCount)
            return;

        int oldNodes = nodeCount;
        nodeCount = node + 1;
        nodes.ensureCapacity((long) nodeCount * nodeEntryBytes);
        for (int n = oldNodes; n < nodeCount; ++n) {
            setEdgeRef(toNodePointer(n), NO_EDGE);
            if (withTurnCosts)
                setTurnCostRef(toNodePointer(n), TurnCostStorage.NO_TURN_ENTRY);
        }
    }

    public long toNodePointer(int node) {
        if (node < 0 || node >= nodeCount)
            throw new IllegalArgumentException("node: " + node + " out of bounds [0," + nodeCount + "[");
        return (long) node * nodeEntryBytes;
    }

    public long toEdgePointer(int edge) {
        if (edge < 0 || edge >= edgeCount)
            throw new IllegalArgumentException("edge: " + edge + " out of bounds [0," + edgeCount + "[");
        return (long) edge * edgeEntryBytes;
    }

    public void readFlags(long edgePointer, BytesRef edgeFlags) {
        edges.getBytes(edgePointer + E_FLAGS, edgeFlags.bytes, edgeFlags.length);
    }

    public void writeFlags(long edgePointer, BytesRef edgeFlags) {
        edges.setBytes(edgePointer + E_FLAGS, edgeFlags.bytes, edgeFlags.length);
    }

    public int getFlagInt(long edgePointer, int index) {
        return edges.getInt(edgePointer + E_FLAGS + index * 4);
    }

    public void setFlagInt(long edgePointer, int index, int value) {
        edges.setInt(edgePointer + E_FLAGS + index * 4, value);
    }

    public void setNodeA(long edgePointer, int nodeA) {
        edges.setInt(edgePointer + E_NODEA, nodeA);
    }

    public void setNodeB(long edgePointer, int nodeB) {
        edges.setInt(edgePointer + E_NODEB, nodeB);
    }

    public void setLinkA(long edgePointer, int linkA) {
        edges.setInt(edgePointer + E_LINKA, linkA);
    }

    public void setLinkB(long edgePointer, int linkB) {
        edges.setInt(edgePointer + E_LINKB, linkB);
    }

    public void setDist(long edgePointer, double distance) {
        edges.setInt(edgePointer + E_DIST, distToInt(distance));
    }

    public void setGeoRef(long edgePointer, long geoRef) {
        byte[] bytes = new byte[5];
        BitUtil.LITTLE.fromULong5(bytes, geoRef, 0);
        edges.setBytes(edgePointer + E_GEO, bytes, bytes.length);
    }

    public void setKeyValuesRef(long edgePointer, int nameRef) {
        edges.setInt(edgePointer + E_KV, nameRef);
    }

    public int getNodeA(long edgePointer) {
        return edges.getInt(edgePointer + E_NODEA);
    }

    public int getNodeB(long edgePointer) {
        return edges.getInt(edgePointer + E_NODEB);
    }

    public int getLinkA(long edgePointer) {
        return edges.getInt(edgePointer + E_LINKA);
    }

    public int getLinkB(long edgePointer) {
        return edges.getInt(edgePointer + E_LINKB);
    }

    public double getDist(long pointer) {
        int val = edges.getInt(pointer + E_DIST);
        // do never return infinity even if INT MAX, see #435
        return val / INT_DIST_FACTOR;
    }

    public long getGeoRef(long edgePointer) {
        byte[] bytes = new byte[5];
        edges.getBytes(edgePointer + E_GEO, bytes, bytes.length);
        return BitUtil.LITTLE.toULong5(bytes, 0);
    }

    public int getKeyValuesRef(long edgePointer) {
        return edges.getInt(edgePointer + E_KV);
    }

    public void setEdgeRef(long nodePointer, int edgeRef) {
        nodes.setInt(nodePointer + N_EDGE_REF, edgeRef);
    }

    public void setLat(long nodePointer, double lat) {
        nodes.setInt(nodePointer + N_LAT, Helper.degreeToInt(lat));
    }

    public void setLon(long nodePointer, double lon) {
        nodes.setInt(nodePointer + N_LON, Helper.degreeToInt(lon));
    }

    public void setEle(long elePointer, double ele) {
        nodes.setInt(elePointer + N_ELE, Helper.eleToInt(ele));
    }

    public void setTurnCostRef(long nodePointer, int tcRef) {
        nodes.setInt(nodePointer + N_TC, tcRef);
    }

    public int getEdgeRef(long nodePointer) {
        return nodes.getInt(nodePointer + N_EDGE_REF);
    }

    public double getLat(long nodePointer) {
        return Helper.intToDegree(nodes.getInt(nodePointer + N_LAT));
    }

    public double getLon(long nodePointer) {
        return Helper.intToDegree(nodes.getInt(nodePointer + N_LON));
    }

    public double getEle(long nodePointer) {
        return Helper.intToEle(nodes.getInt(nodePointer + N_ELE));
    }

    public int getTurnCostRef(long nodePointer) {
        return nodes.getInt(nodePointer + N_TC);
    }

    public void setFrozen(boolean frozen) {
        this.frozen = frozen;
    }

    public boolean getFrozen() {
        return frozen;
    }

    public void debugPrint() {
        final int printMax = 100;
        System.out.println("nodes:");
        String formatNodes = "%12s | %12s | %12s | %12s \n";
        System.out.format(Locale.ROOT, formatNodes, "#", "N_EDGE_REF", "N_LAT", "N_LON");
        for (int i = 0; i < Math.min(nodeCount, printMax); ++i) {
            long nodePointer = toNodePointer(i);
            System.out.format(Locale.ROOT, formatNodes, i, getEdgeRef(nodePointer), getLat(nodePointer), getLon(nodePointer));
        }
        if (nodeCount > printMax) {
            System.out.format(Locale.ROOT, " ... %d more nodes\n", nodeCount - printMax);
        }
        System.out.println("edges:");
        String formatEdges = "%12s | %12s | %12s | %12s | %12s | %12s | %12s \n";
        System.out.format(Locale.ROOT, formatEdges, "#", "E_NODEA", "E_NODEB", "E_LINKA", "E_LINKB", "E_FLAGS", "E_DIST");
        BytesRef bytesRef = new BytesRef(bytesForFlags);
        for (int i = 0; i < Math.min(edgeCount, printMax); ++i) {
            long edgePointer = toEdgePointer(i);
            readFlags(edgePointer, bytesRef);
            System.out.format(Locale.ROOT, formatEdges, i,
                    getNodeA(edgePointer),
                    getNodeB(edgePointer),
                    getLinkA(edgePointer),
                    getLinkB(edgePointer),
                    bytesRef,
                    getDist(edgePointer));
        }
        if (edgeCount > printMax) {
            System.out.printf(Locale.ROOT, " ... %d more edges", edgeCount - printMax);
        }
    }

    private int distToInt(double distance) {
        if (distance < 0)
            throw new IllegalArgumentException("Distance cannot be negative: " + distance);
        if (distance > MAX_DIST) {
            distance = MAX_DIST;
        }
        int intDist = (int) Math.round(distance * INT_DIST_FACTOR);
        assert intDist >= 0 : "distance out of range";
        return intDist;
    }

    public String toDetailsString() {
        return "edges: " + nf(edgeCount) + "(" + edges.getCapacity() / Helper.MB + "MB), "
                + "nodes: " + nf(nodeCount) + "(" + nodes.getCapacity() / Helper.MB + "MB), "
                + "bounds: " + bounds;
    }
}
