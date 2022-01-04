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

import MyGame.Sample.*;
import com.carrotsearch.hppc.IntArrayList;
import com.google.common.primitives.Longs;
import com.google.flatbuffers.FlatBufferBuilder;
import com.google.transit.realtime.GtfsRealtime;
import com.graphhopper.storage.DataAccess;
import com.graphhopper.storage.Directory;
import com.graphhopper.util.EdgeIterator;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.StreamSupport;

public class PtGraph implements GtfsReader.PtGraphOut {

    // nodes
    private final DataAccess nodes;
    private final int nodeEntryBytes;
    private final Directory dir;
    private int nodeCount;

    // edges
    private final DataAccess edges;
    private final int E_NODEA, E_NODEB, E_LINKA, E_LINKB, E_ATTRS;
    private final int edgeEntryBytes;
    private int edgeCount;

    private final DataAccess attrs;

    public PtGraph(Directory dir, int firstNode) {
        this.dir = dir;
        nextNode = firstNode;
        nodes = dir.create("pt_nodes", dir.getDefaultType("pt_nodes", true), -1);
        edges = dir.create("pt_edges", dir.getDefaultType("pt_edges", false), -1);
        attrs = dir.create("pt_edge_attrs", dir.getDefaultType("pt_edge_attrs", false), -1);

        nodeEntryBytes = 8;

        // memory layout for edges
        E_NODEA = 0;
        E_NODEB = 4;
        E_LINKA = 8;
        E_LINKB = 12;
        E_ATTRS = 16;
        edgeEntryBytes = E_ATTRS + 8;
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
        try {
            deserializeExtraStuff();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return true;
    }

    public void flush() {
        nodes.setHeader(2 * 4, nodeCount);
        edges.setHeader(2 * 4, edgeCount);

        edges.flush();
        nodes.flush();
        attrs.flush();
        try {
            serializeExtraStuff();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
        byte[] bytes = Longs.toByteArray(attrPointer);
        edges.setBytes(edgePointer + E_ATTRS, bytes, bytes.length);
    }

    private long getAttrPointer(long edgePointer) {
        byte[] bytes = new byte[8];
        edges.getBytes(edgePointer + E_ATTRS, bytes, 8);
        return Longs.fromByteArray(bytes);
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

    Map<GtfsStorage.Validity, Integer> validities = new HashMap<>();
    List<GtfsStorage.Validity> validityList = new ArrayList<>();

    Map<GtfsStorage.PlatformDescriptor, Integer> platformDescriptors = new HashMap<>();
    List<GtfsStorage.PlatformDescriptor> platformDescriptorList = new ArrayList<>();

    Map<GtfsRealtime.TripDescriptor, Integer> tripDescriptors = new HashMap<>();
    List<GtfsRealtime.TripDescriptor> tripDescriptorList = new ArrayList<>();

    Map<GtfsStorage.FeedIdWithTimezone, Integer> feedIdWithTimezones = new HashMap<>();
    List<GtfsStorage.FeedIdWithTimezone> feedIdWithTimezoneList = new ArrayList<>();

    private void serializeExtraStuff() throws IOException {
        FlatBufferBuilder fbb = new FlatBufferBuilder();
        int validitesOffset = Extra.createValiditiesVector(fbb, serializeValidities(fbb));
        int platformDescriptorsOffset = Extra.createPlatformDescriptorsVector(fbb, serializePlatformDescriptors(fbb));
        int feedIdsOffset = Extra.createFeedIdWithTimezonesVector(fbb, serializeFeedIds(fbb));
        int tripDescriptorsOffset = Extra.createTripDescriptorsVector(fbb, serializeTripDescriptors(fbb));
        int extra = Extra.createExtra(fbb, validitesOffset, feedIdsOffset, platformDescriptorsOffset, tripDescriptorsOffset);
        fbb.finish(extra);
        try (FileChannel fc = new FileOutputStream(dir.getLocation() + "/wurst").getChannel()) {
            fc.write(fbb.dataBuffer());
        }
    }

    private void deserializeExtraStuff() throws IOException {
        try (FileChannel fc = new FileInputStream(dir.getLocation() + "/wurst").getChannel()) {
            ByteBuffer buffer = ByteBuffer.allocate((int) fc.size());
            fc.read(buffer);
            buffer.flip();
            Extra extra = Extra.getRootAsExtra(buffer);
            for (int i = 0; i < extra.validitiesLength(); i++) {
                Validity validity = extra.validities(i);
                ByteBuffer bb = validity.bitSetAsByteBuffer();
                byte[] arr = new byte[bb.remaining()];
                bb.get(arr);
                validityList.add(new GtfsStorage.Validity(BitSet.valueOf(arr), ZoneId.of(validity.zoneId()), LocalDate.ofEpochDay(validity.start())));
            }
            for (int i = 0; i < extra.feedIdWithTimezonesLength(); i++) {
                feedIdWithTimezoneList.add(new GtfsStorage.FeedIdWithTimezone(extra.feedIdWithTimezones(i).feedId(), ZoneId.of(extra.feedIdWithTimezones(i).zoneId())));
            }
            for (int i = 0; i < extra.platformDescriptorsLength(); i++) {
                PlatformDescriptor platformDescriptor = extra.platformDescriptors(i);
                GtfsStorage.PlatformDescriptor pd;
                if (platformDescriptor.routeId() != null) {
                    pd = GtfsStorage.PlatformDescriptor.route(platformDescriptor.feedId(), platformDescriptor.stopId(), platformDescriptor.routeId());
                } else {
                    pd = GtfsStorage.PlatformDescriptor.routeType(platformDescriptor.feedId(), platformDescriptor.stopId(), platformDescriptor.routeType());
                }
                platformDescriptorList.add(pd);
            }
            for (int i = 0; i < extra.tripDescriptorsLength(); i++) {
                tripDescriptorList.add(GtfsRealtime.TripDescriptor.parseFrom(extra.tripDescriptors(i).bytesAsByteBuffer()));
            }
        }
    }

    private int[] serializeValidities(FlatBufferBuilder fbb) {
        IntArrayList offsets = new IntArrayList();
        for (GtfsStorage.Validity validity : validityList) {
            int bitSetVector = Validity.createBitSetVector(fbb, validity.validity.toByteArray());
            int string = fbb.createString(validity.zoneId.getId());
            Validity.startValidity(fbb);
            Validity.addBitSet(fbb, bitSetVector);
            Validity.addStart(fbb, validity.start.toEpochDay());
            Validity.addZoneId(fbb, string);
            int offset = Validity.endValidity(fbb);
            offsets.add(offset);
        }
        return offsets.toArray();
    }

    private int[] serializeFeedIds(FlatBufferBuilder fbb) {
        IntArrayList offsets = new IntArrayList();
        for (GtfsStorage.FeedIdWithTimezone feedIdWithTimezone : feedIdWithTimezoneList) {
            int offset = FeedIdWithTimezone.createFeedIdWithTimezone(fbb, fbb.createString(feedIdWithTimezone.feedId), fbb.createString(feedIdWithTimezone.zoneId.getId()));
            offsets.add(offset);
        }
        return offsets.toArray();
    }

    private int[] serializeTripDescriptors(FlatBufferBuilder fbb) {
        IntArrayList offsets = new IntArrayList();
        for (GtfsRealtime.TripDescriptor tripDescriptor : tripDescriptorList) {
            offsets.add(TripDescriptor.createTripDescriptor(fbb, TripDescriptor.createBytesVector(fbb, tripDescriptor.toByteArray())));
        }
        return offsets.toArray();
    }

    private int[] serializePlatformDescriptors(FlatBufferBuilder fbb) {
        IntArrayList offsets = new IntArrayList();
        for (GtfsStorage.PlatformDescriptor platformDescriptor : platformDescriptorList) {
            int stopId = fbb.createString(platformDescriptor.stop_id);
            int feedId = fbb.createString(platformDescriptor.feed_id);
            int routeId = -1;
            if (platformDescriptor instanceof GtfsStorage.RoutePlatform) {
                routeId = fbb.createString(((GtfsStorage.RoutePlatform) platformDescriptor).route_id);
            }
            PlatformDescriptor.startPlatformDescriptor(fbb);
            PlatformDescriptor.addStopId(fbb, stopId);
            PlatformDescriptor.addFeedId(fbb, feedId);
            if (platformDescriptor instanceof GtfsStorage.RouteTypePlatform) {
                PlatformDescriptor.addRouteType(fbb, ((GtfsStorage.RouteTypePlatform) platformDescriptor).route_type);
            }
            if (routeId != -1) {
                PlatformDescriptor.addRouteId(fbb, routeId);
            }
            int offset = PlatformDescriptor.endPlatformDescriptor(fbb);
            offsets.add(offset);
        }
        return offsets.toArray();
    }

    @Override
    public int createEdge(int src, int dest, PtEdgeAttributes attrs) {
        ByteBuffer bb = ByteBuffer.allocate(50);
        bb.putInt(attrs.type.ordinal());
        bb.putInt(attrs.time);
        switch (attrs.type) {
            case ENTER_PT:
                bb.putInt(attrs.route_type);
                bb.putInt(sharePlatformDescriptor(attrs.platformDescriptor));
                break;
            case EXIT_PT:
                bb.putInt(sharePlatformDescriptor(attrs.platformDescriptor));
                break;
            case ENTER_TIME_EXPANDED_NETWORK:
                bb.putInt(shareFeedIdWithTimezone(attrs.feedIdWithTimezone));
                break;
            case LEAVE_TIME_EXPANDED_NETWORK:
                bb.putInt(shareFeedIdWithTimezone(attrs.feedIdWithTimezone));
                break;
            case BOARD:
                bb.putInt(attrs.stop_sequence);
                bb.putInt(shareTripDescriptor(attrs.tripDescriptor));
                bb.putInt(shareValidity(attrs.validity));
                bb.putInt(attrs.transfers);
                break;
            case ALIGHT:
                bb.putInt(attrs.stop_sequence);
                bb.putInt(shareTripDescriptor(attrs.tripDescriptor));
                bb.putInt(shareValidity(attrs.validity));
                break;
            case WAIT:
                break;
            case WAIT_ARRIVAL:
                break;
            case OVERNIGHT:
                break;
            case HOP:
                bb.putInt(attrs.stop_sequence);
                break;
            case DWELL:
                break;
            case TRANSFER:
                bb.putInt(attrs.route_type);
                bb.putInt(sharePlatformDescriptor(attrs.platformDescriptor));
                break;
            default:
                throw new RuntimeException();
        }
        bb.flip();
        int edge = addEdge(src, dest, currentPointer);
        this.attrs.ensureCapacity(currentPointer + 10000);
        while(bb.hasRemaining()) {
            this.attrs.setByte(currentPointer, bb.get());
            currentPointer++;
        }
        return edge;
    }

    private int shareValidity(GtfsStorage.Validity validity) {
        Integer validityId = validities.get(validity);
        if (validityId == null) {
            validityId = validityList.size();
            validities.put(validity, validityId);
            validityList.add(validity);
        }
        return validityId;
    }

    private int shareTripDescriptor(GtfsRealtime.TripDescriptor tripDescriptor) {
        Integer tripDescriptorId = tripDescriptors.get(tripDescriptor);
        if (tripDescriptorId == null) {
            tripDescriptorId = tripDescriptorList.size();
            tripDescriptors.put(tripDescriptor, tripDescriptorId);
            tripDescriptorList.add(tripDescriptor);
        }
        return tripDescriptorId;
    }

    private Integer shareFeedIdWithTimezone(GtfsStorage.FeedIdWithTimezone feedIdWithTimezone1) {
        Integer feedIdWithTimezone = feedIdWithTimezones.get(feedIdWithTimezone1);
        if (feedIdWithTimezone == null) {
            feedIdWithTimezone = feedIdWithTimezoneList.size();
            feedIdWithTimezones.put(feedIdWithTimezone1, feedIdWithTimezone);
            feedIdWithTimezoneList.add(feedIdWithTimezone1);
        }
        return feedIdWithTimezone;
    }

    private Integer sharePlatformDescriptor(GtfsStorage.PlatformDescriptor platformDescriptor) {
        Integer platformDescriptorId = platformDescriptors.get(platformDescriptor);
        if (platformDescriptorId == null) {
            platformDescriptorId = platformDescriptorList.size();
            platformDescriptors.put(platformDescriptor, platformDescriptorId);
            platformDescriptorList.add(platformDescriptor);
        }
        return platformDescriptorId;
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
        long attrPointer = getAttrPointer(toEdgePointer(edgeId));
        int size = 50;
        byte[] bytes = new byte[size];
        attrs.getBytes(attrPointer, bytes, size);
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        int type = bb.getInt();
        int time = bb.getInt();
        switch (type) {
            case EdgeType.BOARD: {
                int stop_sequence = bb.getInt();
                int tripDescriptor = bb.getInt();
                int validity = bb.getInt();
                int transfers = bb.getInt();
                return new PtEdgeAttributes(GtfsStorage.EdgeType.BOARD, time, validityList.get(validity), -1, null,
                        transfers, stop_sequence, tripDescriptorList.get(tripDescriptor), null);
            }
            case EdgeType.ALIGHT: {
                int stop_sequence = bb.getInt();
                int tripDescriptor = bb.getInt();
                int validity = bb.getInt();
                return new PtEdgeAttributes(GtfsStorage.EdgeType.ALIGHT, time, validityList.get(validity), -1, null,
                        0, stop_sequence, tripDescriptorList.get(tripDescriptor), null);
            }
            case EdgeType.ENTER_PT: {
                int routeType = bb.getInt();
                int platformDescriptor = bb.getInt();
                return new PtEdgeAttributes(GtfsStorage.EdgeType.ENTER_PT, time, null, routeType, null,
                        0, -1, null, platformDescriptorList.get(platformDescriptor));
            }
            case EdgeType.EXIT_PT: {
                int platformDescriptor = bb.getInt();
                return new PtEdgeAttributes(GtfsStorage.EdgeType.EXIT_PT, time, null, -1, null,
                        0, -1, null, platformDescriptorList.get(platformDescriptor));
            }
            case EdgeType.HOP: {
                int stop_sequence = bb.getInt();
                return new PtEdgeAttributes(GtfsStorage.EdgeType.HOP, time, null, -1, null,
                        0, stop_sequence, null, null);
            }
            case EdgeType.DWELL: {
                return new PtEdgeAttributes(GtfsStorage.EdgeType.DWELL, time, null, -1, null,
                        0, -1, null, null);
            }
            case EdgeType.ENTER_TIME_EXPANDED_NETWORK: {
                int feedId = bb.getInt();
                return new PtEdgeAttributes(GtfsStorage.EdgeType.ENTER_TIME_EXPANDED_NETWORK, time, null, -1, feedIdWithTimezoneList.get(feedId),
                        0, -1, null, null);
            }
            case EdgeType.LEAVE_TIME_EXPANDED_NETWORK: {
                int feedId = bb.getInt();
                return new PtEdgeAttributes(GtfsStorage.EdgeType.LEAVE_TIME_EXPANDED_NETWORK, time, null, -1, feedIdWithTimezoneList.get(feedId),
                        0, -1, null, null);
            }
            case EdgeType.WAIT: {
                return new PtEdgeAttributes(GtfsStorage.EdgeType.WAIT, time, null, -1, null,
                        0, -1, null, null);
            }
            case EdgeType.WAIT_ARRIVAL: {
                return new PtEdgeAttributes(GtfsStorage.EdgeType.WAIT_ARRIVAL, time, null, -1, null,
                        0, -1, null, null);
            }
            case EdgeType.OVERNIGHT: {
                return new PtEdgeAttributes(GtfsStorage.EdgeType.OVERNIGHT, time, null, -1, null,
                        0, -1, null, null);
            }
            case EdgeType.TRANSFER: {
                int routeType = bb.getInt();
                int platformDescriptor = bb.getInt();
                return new PtEdgeAttributes(GtfsStorage.EdgeType.TRANSFER, time, null, routeType, null,
                        0, -1, null, platformDescriptorList.get(platformDescriptor));
            }
            default:
                throw new RuntimeException();
        }
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
