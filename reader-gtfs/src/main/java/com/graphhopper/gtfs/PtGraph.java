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

import com.graphhopper.storage.DataAccess;
import com.graphhopper.storage.Directory;
import com.graphhopper.util.EdgeIterator;

import java.util.*;

public class PtGraph implements GtfsReader.PtGraphOut {

    // nodes
    private final DataAccess nodes;
    private final int nodeEntryBytes;
    private int nodeCount;

    // edges
    private final DataAccess edges;
    private final int E_NODEA, E_NODEB, E_LINKA, E_LINKB;
    private final int edgeEntryBytes;
    private int edgeCount;

    public PtGraph(Directory dir, int firstNode) {
        nextNode = firstNode;
        nodes = dir.create("pt_nodes", dir.getDefaultType("pt_nodes", true), 100);
        edges = dir.create("pt_edges", dir.getDefaultType("pt_edges", true), 100);

        nodeEntryBytes = 4;

        // memory layout for edges
        E_NODEA = 0;
        E_NODEB = 4;
        E_LINKA = 8;
        E_LINKB = 12;
        edgeEntryBytes = E_LINKB + 4;
    }

    public void create(long initSize) {
        nodes.create(initSize);
        edges.create(initSize);
    }

    public boolean loadExisting() {
        if (!nodes.loadExisting() || !edges.loadExisting())
            return false;

        nodeCount = nodes.getHeader(2 * 4);
        edgeCount = edges.getHeader(2 * 4);
        return true;
    }

    public void flush() {
        nodes.setHeader(2 * 4, nodeCount);
        edges.setHeader(2 * 4, edgeCount);

        edges.flush();
        nodes.flush();
    }

    public void close() {
        edges.close();
        nodes.close();
    }

    public int getNodeCount() {
        return nodeCount;
    }

    public int getEdgeCount() {
        return edgeCount;
    }

    public boolean isClosed() {
        assert nodes.isClosed() == edges.isClosed();
        return nodes.isClosed();
    }

    public int addEdge(int nodeA, int nodeB) {
        if (edgeCount == Integer.MAX_VALUE)
            throw new IllegalStateException("Maximum edge count exceeded: " + edgeCount);
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
        setLinkA(edgePointer, edgeRefA >= 0 ? edgeRefA : -1);
        setEdgeRef(nodePointerA, edge);

        if (nodeA != nodeB) {
            long nodePointerB = toNodePointer(nodeB);
            int edgeRefB = getEdgeRef(nodePointerB);
            setLinkB(edgePointer, EdgeIterator.Edge.isValid(edgeRefB) ? edgeRefB : -1);
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
            setEdgeRef(toNodePointer(n), -1);
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

    public void setEdgeRef(long nodePointer, int edgeRef) {
        nodes.setInt(nodePointer, edgeRef);
    }

    public int getEdgeRef(long nodePointer) {
        return nodes.getInt(nodePointer);
    }

    Map<Integer, GtfsStorageI.PlatformDescriptor> platforms = new HashMap<>();

    public Map<Integer, GtfsStorageI.PlatformDescriptor> getPlatforms(GtfsStorage.FeedIdWithStopId feedIdWithStopId) {
        HashMap<Integer, GtfsStorageI.PlatformDescriptor> result = new HashMap<>();
        platforms.forEach((node, platformDescriptor) -> {
            if (platformDescriptor.feed_id.equals(feedIdWithStopId.feedId) && platformDescriptor.stop_id.equals(feedIdWithStopId.stopId))
                result.put(node, platformDescriptor);
        });
        return result;
    }

    int nextEdge = 0;
    int nextNode = 0;
    Map<Integer, PtEdgeAttributes> edgeAttributesMap = new HashMap<>();
    Map<Integer, Integer> edgeSourcesMap = new HashMap<>();
    Map<Integer, Integer> edgeDestinationsMap = new HashMap<>();
    Map<Integer, List<Integer>> nodeToOutEdges = new HashMap<>();
    Map<Integer, List<Integer>> nodeToInEdges = new HashMap<>();

    @Override
    public void putPlatformNode(int platformEnterNode, GtfsStorageI.PlatformDescriptor platformDescriptor) {
        platforms.put(platformEnterNode, platformDescriptor);
    }

    @Override
    public int createEdge(int src, int dest, PtEdgeAttributes attrs) {
        int edge = nextEdge++;
        edgeAttributesMap.put(edge, attrs);
        edgeSourcesMap.put(edge, src);
        edgeDestinationsMap.put(edge, dest);
        nodeToOutEdges.putIfAbsent(src, new ArrayList<>());
        nodeToInEdges.putIfAbsent(dest, new ArrayList<>());
        List<Integer> outEdges = nodeToOutEdges.get(src);
        outEdges.add(edge);
        List<Integer> inEdges = nodeToInEdges.get(dest);
        inEdges.add(edge);
        return edge;
    }

    public int createNode() {
        return nextNode++;
    }

    public Iterable<PtEdge> edgesAround(int node) {
        return () -> {
            List<Integer> edgeIds = new ArrayList<>(nodeToOutEdges.getOrDefault(node, Collections.emptyList()));
            edgeIds.sort(Comparator.<Integer>naturalOrder().reversed());
            return edgeIds.stream().map(e -> new PtEdge(e, edgeDestinationsMap.get(e), edgeAttributesMap.get(e))).iterator();
        };
    }

    public Iterable<PtEdge> backEdgesAround(int node) {
        return () -> {
            List<Integer> edgeIds = new ArrayList<>(nodeToInEdges.getOrDefault(node, Collections.emptyList()));
            edgeIds.sort(Comparator.<Integer>naturalOrder().reversed());
            return edgeIds.stream().map(e -> new PtEdge(e, edgeSourcesMap.get(e), edgeAttributesMap.get(e))).iterator();
        };
    }

    public PtEdgeAttributes getEdgeAttributes(int edge) {
        return edgeAttributesMap.get(edge);
    }

    public static class PtEdge {
        private final int edgeId;
        private final int adjNode;
        private final PtEdgeAttributes attrs;

        public PtEdge(int edgeId, int adjNode, PtEdgeAttributes attrs) {
            this.edgeId = edgeId;
            this.adjNode = adjNode;
            this.attrs = attrs;
        }

        public GtfsStorage.EdgeType getType() {
            return attrs.type;
        }

        public int getTime() {
            return attrs.time;
        }

        public int getAdjNode() {
            return adjNode;
        }

        public PtEdgeAttributes getAttrs() {
            return attrs;
        }

        public int getId() {
            return edgeId;
        }

        @Override
        public String toString() {
            return "PtEdge{" +
                    "edgeId=" + edgeId +
                    ", adjNode=" + adjNode +
                    ", attrs=" + attrs +
                    '}';
        }
    }
}
