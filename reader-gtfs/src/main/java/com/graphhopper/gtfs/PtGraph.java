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
import java.util.function.Consumer;
import java.util.stream.StreamSupport;

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

        nodeEntryBytes = 8;

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
        int edgeRefA = getEdgeRefOut(nodePointerA);
        setLinkA(edgePointer, edgeRefA >= 0 ? edgeRefA : -1);
        setEdgeRefOut(nodePointerA, edge);

        if (nodeA != nodeB) {
            long nodePointerB = toNodePointer(nodeB);
            int edgeRefB = getEdgeRefIn(nodePointerB);
            setLinkB(edgePointer, EdgeIterator.Edge.isValid(edgeRefB) ? edgeRefB : -1);
            setEdgeRefIn(nodePointerB, edge);
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
            setEdgeRefOut(toNodePointer(n), -1);
            setEdgeRefIn(toNodePointer(n), -1);
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

    public void setEdgeRefOut(long nodePointer, int edgeRef) {
        nodes.setInt(nodePointer, edgeRef);
    }

    public void setEdgeRefIn(long nodePointer, int edgeRef) {
        nodes.setInt(nodePointer + 4, edgeRef);
    }

    public int getEdgeRefOut(long nodePointer) {
        return nodes.getInt(nodePointer);
    }

    public int getEdgeRefIn(long nodePointer) {
        return nodes.getInt(nodePointer + 4);
    }

    Map<Integer, GtfsStorageI.PlatformDescriptor> enterPlatforms = new HashMap<>();
    Map<Integer, GtfsStorageI.PlatformDescriptor> exitPlatforms = new HashMap<>();

    public Map<Integer, GtfsStorageI.PlatformDescriptor> getEnterPlatforms(GtfsStorage.FeedIdWithStopId feedIdWithStopId) {
        HashMap<Integer, GtfsStorageI.PlatformDescriptor> result = new HashMap<>();
        enterPlatforms.forEach((node, platformDescriptor) -> {
            if (platformDescriptor.feed_id.equals(feedIdWithStopId.feedId) && platformDescriptor.stop_id.equals(feedIdWithStopId.stopId))
                result.put(node, platformDescriptor);
        });
        return result;
    }

    public Map<Integer, GtfsStorageI.PlatformDescriptor> getExitPlatforms(GtfsStorage.FeedIdWithStopId feedIdWithStopId) {
        HashMap<Integer, GtfsStorageI.PlatformDescriptor> result = new HashMap<>();
        exitPlatforms.forEach((node, platformDescriptor) -> {
            if (platformDescriptor.feed_id.equals(feedIdWithStopId.feedId) && platformDescriptor.stop_id.equals(feedIdWithStopId.stopId))
                result.put(node, platformDescriptor);
        });
        return result;
    }

    int nextNode = 0;
    Map<Integer, PtEdgeAttributes> edgeAttributesMap = new HashMap<>();

    public void putPlatformEnterNode(int platformEnterNode, GtfsStorageI.PlatformDescriptor platformDescriptor) {
        enterPlatforms.put(platformEnterNode, platformDescriptor);
    }

    public void putPlatformExitNode(int platformExitNode, GtfsStorageI.PlatformDescriptor platformDescriptor) {
        exitPlatforms.put(platformExitNode, platformDescriptor);
    }


    @Override
    public int createEdge(int src, int dest, PtEdgeAttributes attrs) {
        int edge = addEdge(src, dest);
        edgeAttributesMap.put(edge, attrs);
        return edge;
    }

    public int createNode() {
        return nextNode++;
    }

    public Iterable<PtEdge> edgesAround(int baseNode) {
        Spliterators.AbstractSpliterator<PtEdge> spliterator = new Spliterators.AbstractSpliterator<PtEdge>(0, 0) {
            int edgeId = getEdgeRefOut(toNodePointer(baseNode));

            @Override
            public boolean tryAdvance(Consumer<? super PtEdge> action) {
                if (edgeId < 0)
                    return false;

                long edgePointer = toEdgePointer(edgeId);

                int nodeA = getNodeA(edgePointer);
                int nodeB = getNodeB(edgePointer);
                action.accept(new PtEdge(edgeId, nodeA, nodeB, edgeAttributesMap.get(edgeId)));
                edgeId = getLinkA(edgePointer);

                return true;
            }
        };
        return () -> StreamSupport.stream(spliterator, false).iterator();
    }

    public PtEdge edge(int edgeId) {
        long edgePointer = toEdgePointer(edgeId);
        int nodeA = getNodeA(edgePointer);
        int nodeB = getNodeB(edgePointer);
        return new PtEdge(edgeId, nodeA, nodeB, edgeAttributesMap.get(edgeId));
    }

    public Iterable<PtEdge> backEdgesAround(int adjNode) {
        Spliterators.AbstractSpliterator<PtEdge> spliterator = new Spliterators.AbstractSpliterator<PtEdge>(0, 0) {
            int edgeId = getEdgeRefIn(toNodePointer(adjNode));

            @Override
            public boolean tryAdvance(Consumer<? super PtEdge> action) {
                if (edgeId < 0)
                    return false;

                long edgePointer = toEdgePointer(edgeId);

                int nodeA = getNodeA(edgePointer);
                int nodeB = getNodeB(edgePointer);
                action.accept(new PtEdge(edgeId, nodeB, nodeA, edgeAttributesMap.get(edgeId)));
                edgeId = getLinkB(edgePointer);

                return true;
            }
        };
        return () -> StreamSupport.stream(spliterator, false).iterator();
    }

    public static class PtEdge {
        private final int edgeId;
        private final int baseNode;

        @Override
        public String toString() {
            return "PtEdge{" +
                    "edgeId=" + edgeId +
                    ", baseNode=" + baseNode +
                    ", adjNode=" + adjNode +
                    ", attrs=" + attrs +
                    '}';
        }

        private final int adjNode;
        private final PtEdgeAttributes attrs;

        public PtEdge(int edgeId, int baseNode, int adjNode, PtEdgeAttributes attrs) {
            this.edgeId = edgeId;
            this.baseNode = baseNode;
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



        public int getRouteType() {
            GtfsStorage.EdgeType edgeType = getType();
            if ((edgeType == GtfsStorage.EdgeType.ENTER_PT || edgeType == GtfsStorage.EdgeType.EXIT_PT || edgeType == GtfsStorage.EdgeType.TRANSFER)) {
                return getAttrs().route_type;
            }
            throw new RuntimeException("Edge type "+edgeType+" doesn't encode route type.");
        }

        public int getBaseNode() {
            return baseNode;
        }
    }
}
