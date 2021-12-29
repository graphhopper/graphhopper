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

import MyGame.Sample.Edge;
import MyGame.Sample.FeedIdWithTimezone;
import MyGame.Sample.PlatformDescriptor;
import MyGame.Sample.Validity;
import com.google.flatbuffers.FlatBufferBuilder;
import com.graphhopper.storage.DataAccess;
import com.graphhopper.storage.Directory;
import com.graphhopper.util.EdgeIterator;

import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.BitSet;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.StreamSupport;

public class PtGraph implements GtfsReader.PtGraphOut {

    // nodes
    private final DataAccess nodes;
    private final int nodeEntryBytes;
    private int nodeCount;

    // edges
    private final DataAccess edges;
    private final int E_NODEA, E_NODEB, E_LINKA, E_LINKB, E_ATTRS;
    private final int edgeEntryBytes;
    private int edgeCount;

    private final DataAccess attrs;

    public PtGraph(Directory dir, int firstNode) {
        nextNode = firstNode;
        nodes = dir.create("pt_nodes", dir.getDefaultType("pt_nodes", true), -1);
        edges = dir.create("pt_edges", dir.getDefaultType("pt_edges", true), -1);
        attrs = dir.create("pt_edge_attrs", dir.getDefaultType("pt_edge_attrs", false), -1);

        nodeEntryBytes = 8;

        // memory layout for edges
        E_NODEA = 0;
        E_NODEB = 4;
        E_LINKA = 8;
        E_LINKB = 12;
        E_ATTRS = 16;
        edgeEntryBytes = E_ATTRS + 4;
    }

    public void create(long initSize) {
        nodes.create(initSize);
        edges.create(initSize);
        attrs.create(initSize);
    }

    public boolean loadExisting() {
        if (!nodes.loadExisting() || !edges.loadExisting() || !attrs.loadExisting())
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
        attrs.flush();
    }

    public void close() {
        edges.close();
        nodes.close();
        attrs.flush();
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

    public int addEdge(int nodeA, int nodeB, long attrPointer) {
        if (edgeCount == Integer.MAX_VALUE)
            throw new IllegalStateException("Maximum edge count exceeded: " + edgeCount);
        ensureNodeCapacity(Math.max(nodeA, nodeB));
        final int edge = edgeCount;
        final long edgePointer = (long) edgeCount * edgeEntryBytes;
        edgeCount++;
        edges.ensureCapacity((long) edgeCount * edgeEntryBytes);

        setNodeA(edgePointer, nodeA);
        setNodeB(edgePointer, nodeB);
        setAttrPointer(edgePointer, attrPointer);
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

    private void setAttrPointer(long edgePointer, long attrPointer) {
        edges.setInt(edgePointer + E_ATTRS, (int) attrPointer);
    }

    private int getAttrPointer(long edgePointer) {
        return edges.getInt(edgePointer + E_ATTRS);
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

    int nextNode = 0;

    long currentPointer = 0;

    @Override
    public int createEdge(int src, int dest, PtEdgeAttributes attrs) {
        FlatBufferBuilder fbb = new FlatBufferBuilder(1);
        int validity = -1;
        if (attrs.validity != null) {
            int bitSetVector = Validity.createBitSetVector(fbb, attrs.validity.validity.toByteArray());
            int string = fbb.createString(attrs.validity.zoneId.getId());
            Validity.startValidity(fbb);
            Validity.addBitSet(fbb, bitSetVector);
            Validity.addStart(fbb, attrs.validity.start.toEpochDay());
            Validity.addZoneId(fbb, string);
            validity = Validity.endValidity(fbb);
        }
        int tripDescriptorVector = -1;
        if (attrs.tripDescriptor != null) {
            tripDescriptorVector = Edge.createTripDescriptorVector(fbb, attrs.tripDescriptor);
        }
        int platformDescriptor = -1;
        if (attrs.platformDescriptor != null) {
            int stopId = fbb.createString(attrs.platformDescriptor.stop_id);
            int feedId = fbb.createString(attrs.platformDescriptor.feed_id);
            int routeId = -1;
            if (attrs.platformDescriptor instanceof GtfsStorage.RoutePlatform) {
                routeId = fbb.createString(((GtfsStorage.RoutePlatform) attrs.platformDescriptor).route_id);
            }
            PlatformDescriptor.startPlatformDescriptor(fbb);
            PlatformDescriptor.addStopId(fbb, stopId);
            PlatformDescriptor.addFeedId(fbb, feedId);
            if (attrs.platformDescriptor instanceof GtfsStorage.RouteTypePlatform) {
                PlatformDescriptor.addRouteType(fbb, ((GtfsStorage.RouteTypePlatform) attrs.platformDescriptor).route_type);
            }
            if (routeId != -1) {
                PlatformDescriptor.addRouteId(fbb, routeId);
            }
            platformDescriptor = PlatformDescriptor.endPlatformDescriptor(fbb);
        }
        int feedIdWithTimezone = -1;
        if (attrs.feedIdWithTimezone != null) {
            feedIdWithTimezone = FeedIdWithTimezone.createFeedIdWithTimezone(fbb, fbb.createString(attrs.feedIdWithTimezone.feedId), fbb.createString(attrs.feedIdWithTimezone.zoneId.getId()));
        }
        Edge.startEdge(fbb);
        Edge.addType(fbb, (byte) attrs.type.ordinal());
        Edge.addTime(fbb, attrs.time);
        Edge.addRouteType(fbb, attrs.route_type);
        Edge.addTransfers(fbb, attrs.transfers);
        Edge.addStopSequence(fbb, attrs.stop_sequence);
        if (attrs.tripDescriptor != null) {
            Edge.addTripDescriptor(fbb, tripDescriptorVector);
        }
        if (validity != -1) {
            Edge.addValidity(fbb, validity);
        }
        if (platformDescriptor != -1) {
            Edge.addPlatformDescriptor(fbb, platformDescriptor);
        }
        if (feedIdWithTimezone != -1) {
            Edge.addFeedIdWithTimezone(fbb, feedIdWithTimezone);
        }
        int offset = Edge.endEdge(fbb);
        Edge.finishSizePrefixedEdgeBuffer(fbb, offset);
        byte[] bytes = fbb.sizedByteArray();
        this.attrs.ensureCapacity(currentPointer + 10000);
        this.attrs.setBytes(currentPointer, bytes, bytes.length);
        int edge = addEdge(src, dest, currentPointer);
        currentPointer += bytes.length * 4L;
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
                PtEdgeAttributes attrs = pullAttrs(edgeId);
                action.accept(new PtEdge(edgeId, nodeA, nodeB, attrs));
                edgeId = getLinkA(edgePointer);
                return true;
            }

        };
        return () -> StreamSupport.stream(spliterator, false).iterator();
    }

    private PtEdgeAttributes pullAttrs(int edgeId) {
        int attrPointer = getAttrPointer(toEdgePointer(edgeId));
        int size = attrs.getInt(attrPointer);
        byte[] bytes = new byte[size];
        attrs.getBytes(attrPointer + 4, bytes, size);
        Edge edge = Edge.getRootAsEdge(ByteBuffer.wrap(bytes));
        GtfsStorage.PlatformDescriptor pd = null;
        PlatformDescriptor platformDescriptor = edge.platformDescriptor();
        if (platformDescriptor != null) {
            if (platformDescriptor.routeId() != null) {
                pd = GtfsStorage.PlatformDescriptor.route(platformDescriptor.feedId(), platformDescriptor.stopId(), platformDescriptor.routeId());
            } else {
                pd = GtfsStorage.PlatformDescriptor.routeType(platformDescriptor.feedId(), platformDescriptor.stopId(), platformDescriptor.routeType());
            }
        }
        GtfsStorage.FeedIdWithTimezone feedIdWithTimezone = null;
        if (edge.feedIdWithTimezone() != null) {
            feedIdWithTimezone = new GtfsStorage.FeedIdWithTimezone(edge.feedIdWithTimezone().feedId(), ZoneId.of(edge.feedIdWithTimezone().zoneId()));
        }

        Validity validity = edge.validity();
        GtfsStorage.Validity v = null;
        if (validity != null) {
            v = new GtfsStorage.Validity(BitSet.valueOf(validity.bitSetAsByteBuffer()), ZoneId.of(validity.zoneId()), LocalDate.ofEpochDay(validity.start()));
        }
        ByteBuffer x = edge.tripDescriptorAsByteBuffer();
        byte[] arr = null;
        if (x != null) {
            arr = new byte[x.remaining()];
            x.get(arr);
        }
        return new PtEdgeAttributes(GtfsStorage.EdgeType.values()[edge.type()], edge.time(), v, edge.routeType(), feedIdWithTimezone,
                edge.transfers(), edge.stopSequence(), edge.tripDescriptorAsByteBuffer() != null ? arr : null, pd);
    }

    public PtEdge edge(int edgeId) {
        long edgePointer = toEdgePointer(edgeId);
        int nodeA = getNodeA(edgePointer);
        int nodeB = getNodeB(edgePointer);
        return new PtEdge(edgeId, nodeA, nodeB, pullAttrs(edgeId));
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
                action.accept(new PtEdge(edgeId, nodeB, nodeA, pullAttrs(edgeId)));
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
