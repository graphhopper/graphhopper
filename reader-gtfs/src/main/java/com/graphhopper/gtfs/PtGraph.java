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

import com.google.transit.realtime.GtfsRealtime;
import com.graphhopper.storage.DAType;
import com.graphhopper.storage.DataAccess;
import com.graphhopper.storage.Directory;
import com.graphhopper.util.EdgeIterator;

import java.io.*;
import java.util.*;
import java.util.function.Consumer;

import static com.graphhopper.gtfs.GtfsStorage.EdgeType.BOARD;

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
    private final static GtfsStorage.EdgeType[] edgeTypeValues = GtfsStorage.EdgeType.values();

    public PtGraph(Directory dir, int firstNode) {
        this.dir = dir;
        nextNode = firstNode;
//        nodes = dir.create("pt_nodes", dir.getDefaultType("pt_nodes", true), -1);
//        edges = dir.create("pt_edges", dir.getDefaultType("pt_edges", true), -1);
//        attrs = dir.create("pt_edge_attrs", dir.getDefaultType("pt_edge_attrs", true), -1);
        nodes = dir.find("pt_nodes", DAType.getPreferredInt(dir.getDefaultType()));
        edges = dir.find("pt_edges", DAType.getPreferredInt(dir.getDefaultType()));
        attrs = dir.find("pt_edge_attrs", DAType.getPreferredInt(dir.getDefaultType()));

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
        } catch (IOException | ClassNotFoundException e) {
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
        edges.setInt(edgePointer + E_ATTRS, getIntLow(attrPointer));
        edges.setInt(edgePointer + E_ATTRS + 4, getIntHigh(attrPointer));
    }

    private long getAttrPointer(long edgePointer) {
        return combineIntsToLong(
                edges.getInt(edgePointer + E_ATTRS),
                edges.getInt(edgePointer + E_ATTRS + 4));
    }

    final int getIntLow(long longValue) {
        return (int) (longValue & 0xFFFFFFFFL);
    }

    final int getIntHigh(long longValue) {
        return (int) (longValue >> 32);
    }

    final long combineIntsToLong(int intLow, int intHigh) {
        return ((long) intHigh << 32) | (intLow & 0xFFFFFFFFL);
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
        try (ObjectOutputStream os = new ObjectOutputStream(new FileOutputStream(dir.getLocation() + "/pt_extra"))) {
            os.writeObject(validityList);
            os.writeObject(platformDescriptorList);
            os.writeObject(tripDescriptorList);
            os.writeObject(feedIdWithTimezoneList);
        }
    }

    private void deserializeExtraStuff() throws IOException, ClassNotFoundException {
        try (ObjectInputStream is = new ObjectInputStream(new FileInputStream(dir.getLocation() + "/pt_extra"))) {
            validityList = ((List<GtfsStorage.Validity>) is.readObject());
            platformDescriptorList = ((List<GtfsStorage.PlatformDescriptor>) is.readObject());
            tripDescriptorList = ((List<GtfsRealtime.TripDescriptor>) is.readObject());
            feedIdWithTimezoneList = ((List<GtfsStorage.FeedIdWithTimezone>) is.readObject());
        }
    }

    @Override
    public int createEdge(int src, int dest, PtEdgeAttributes attrs) {
        this.attrs.ensureCapacity(currentPointer + 10000);

        int edge = addEdge(src, dest, currentPointer);
        this.attrs.setInt(currentPointer, attrs.type.ordinal());
        currentPointer += 4;
        this.attrs.setInt(currentPointer, attrs.time);
        currentPointer += 4;

        switch (attrs.type) {
            case ENTER_PT:
                this.attrs.setInt(currentPointer, attrs.route_type);
                currentPointer += 4;

                this.attrs.setInt(currentPointer, sharePlatformDescriptor(attrs.platformDescriptor));
                currentPointer += 4;

                break;
            case EXIT_PT:
                this.attrs.setInt(currentPointer, sharePlatformDescriptor(attrs.platformDescriptor));
                currentPointer += 4;

                break;
            case ENTER_TIME_EXPANDED_NETWORK:
                this.attrs.setInt(currentPointer, shareFeedIdWithTimezone(attrs.feedIdWithTimezone));
                currentPointer += 4;

                break;
            case LEAVE_TIME_EXPANDED_NETWORK:
                this.attrs.setInt(currentPointer, shareFeedIdWithTimezone(attrs.feedIdWithTimezone));
                currentPointer += 4;

                break;
            case BOARD:
                this.attrs.setInt(currentPointer, attrs.stop_sequence);
                currentPointer += 4;

                this.attrs.setInt(currentPointer, shareTripDescriptor(attrs.tripDescriptor));
                currentPointer += 4;

                this.attrs.setInt(currentPointer, shareValidity(attrs.validity));
                currentPointer += 4;

                this.attrs.setInt(currentPointer, attrs.transfers);
                currentPointer += 4;

                break;
            case ALIGHT:
                this.attrs.setInt(currentPointer, attrs.stop_sequence);
                currentPointer += 4;

                this.attrs.setInt(currentPointer, shareTripDescriptor(attrs.tripDescriptor));
                currentPointer += 4;

                this.attrs.setInt(currentPointer, shareValidity(attrs.validity));
                currentPointer += 4;

                break;
            case WAIT:
                break;
            case WAIT_ARRIVAL:
                break;
            case OVERNIGHT:
                break;
            case HOP:
                this.attrs.setInt(currentPointer, attrs.stop_sequence);
                currentPointer += 4;

                break;
            case DWELL:
                break;
            case TRANSFER:
                this.attrs.setInt(currentPointer, attrs.route_type);
                currentPointer += 4;

                this.attrs.setInt(currentPointer, sharePlatformDescriptor(attrs.platformDescriptor));
                currentPointer += 4;

                break;
            default:
                throw new RuntimeException();
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
        return () -> Spliterators.iterator(spliterator);
    }


    private PtEdgeAttributes pullAttrs(int edgeId) {
        long attrPointer = getAttrPointer(toEdgePointer(edgeId));
        GtfsStorage.EdgeType type = edgeTypeValues[attrs.getInt(attrPointer)];
        attrPointer += 4;
        int time = attrs.getInt(attrPointer);
        attrPointer += 4;
        switch (type) {
            case BOARD: {
                int stop_sequence = attrs.getInt(attrPointer);
                attrPointer += 4;
                int tripDescriptor = attrs.getInt(attrPointer);
                attrPointer += 4;
                int validity = attrs.getInt(attrPointer);
                attrPointer += 4;
                int transfers = attrs.getInt(attrPointer);
                attrPointer += 4;
                return new PtEdgeAttributes(BOARD, time, validityList.get(validity), -1, null,
                        transfers, stop_sequence, tripDescriptorList.get(tripDescriptor), null);
            }
            case ALIGHT: {
                int stop_sequence = attrs.getInt(attrPointer);
                attrPointer += 4;
                int tripDescriptor = attrs.getInt(attrPointer);
                attrPointer += 4;
                int validity = attrs.getInt(attrPointer);
                attrPointer += 4;
                return new PtEdgeAttributes(GtfsStorage.EdgeType.ALIGHT, time, validityList.get(validity), -1, null,
                        0, stop_sequence, tripDescriptorList.get(tripDescriptor), null);
            }
            case ENTER_PT: {
                int routeType = attrs.getInt(attrPointer);
                attrPointer += 4;
                int platformDescriptor = attrs.getInt(attrPointer);
                attrPointer += 4;
                return new PtEdgeAttributes(GtfsStorage.EdgeType.ENTER_PT, time, null, routeType, null,
                        0, -1, null, platformDescriptorList.get(platformDescriptor));
            }
            case EXIT_PT: {
                int platformDescriptor = attrs.getInt(attrPointer);
                attrPointer += 4;
                return new PtEdgeAttributes(GtfsStorage.EdgeType.EXIT_PT, time, null, -1, null,
                        0, -1, null, platformDescriptorList.get(platformDescriptor));
            }
            case HOP: {
                int stop_sequence = attrs.getInt(attrPointer);
                attrPointer += 4;
                return new PtEdgeAttributes(GtfsStorage.EdgeType.HOP, time, null, -1, null,
                        0, stop_sequence, null, null);
            }
            case DWELL: {
                return new PtEdgeAttributes(GtfsStorage.EdgeType.DWELL, time, null, -1, null,
                        0, -1, null, null);
            }
            case ENTER_TIME_EXPANDED_NETWORK: {
                int feedId = attrs.getInt(attrPointer);
                attrPointer += 4;
                return new PtEdgeAttributes(GtfsStorage.EdgeType.ENTER_TIME_EXPANDED_NETWORK, time, null, -1, feedIdWithTimezoneList.get(feedId),
                        0, -1, null, null);
            }
            case LEAVE_TIME_EXPANDED_NETWORK: {
                int feedId = attrs.getInt(attrPointer);
                attrPointer += 4;
                return new PtEdgeAttributes(GtfsStorage.EdgeType.LEAVE_TIME_EXPANDED_NETWORK, time, null, -1, feedIdWithTimezoneList.get(feedId),
                        0, -1, null, null);
            }
            case WAIT: {
                return new PtEdgeAttributes(GtfsStorage.EdgeType.WAIT, time, null, -1, null,
                        0, -1, null, null);
            }
            case WAIT_ARRIVAL: {
                return new PtEdgeAttributes(GtfsStorage.EdgeType.WAIT_ARRIVAL, time, null, -1, null,
                        0, -1, null, null);
            }
            case OVERNIGHT: {
                return new PtEdgeAttributes(GtfsStorage.EdgeType.OVERNIGHT, time, null, -1, null,
                        0, -1, null, null);
            }
            case TRANSFER: {
                int routeType = attrs.getInt(attrPointer);
                attrPointer += 4;
                int platformDescriptor = attrs.getInt(attrPointer);
                attrPointer += 4;
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
        return () -> Spliterators.iterator(spliterator);
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
