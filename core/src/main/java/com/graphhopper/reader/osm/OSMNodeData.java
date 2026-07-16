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

package com.graphhopper.reader.osm;

import com.carrotsearch.hppc.LongScatterSet;
import com.carrotsearch.hppc.LongSet;
import com.graphhopper.coll.BlockedBTreeLongLongMap;
import com.graphhopper.coll.GHLongLongBTree;
import com.graphhopper.coll.LongLongMap;
import com.graphhopper.coll.PagedLongArray;
import com.graphhopper.reader.ReaderNode;
import com.graphhopper.search.KVStorage;
import com.graphhopper.storage.Directory;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PointAccess;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPoint3D;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * This class stores OSM node data while reading an OSM file in {@link WaySegmentParser}. It is not trivial to do this
 * in a memory-efficient way. We use the following approach:
 * <pre>
 * - For each OSM node we store an id that points to the nodes coordinates. We separate nodes into
 *   (potential) tower nodes and pillar nodes. We use the negative ids for tower nodes and positive
 *   ids for pillar nodes. The tower nodes are limited to ~2 billion nodes as we later use the ID as positive integer.
 * - We reserve a few special ids like {@link #JUNCTION_NODE} to distinguish the different node types when we read the
 *   OSM file for the first time (pass1) in {@link WaySegmentParser}. We then assign actual ids in the second pass.
 * - We store the node coordinates for tower and pillar nodes in different places. The pillar node storage is only
 *   temporary, because at the time we store the coordinates it is unknown to which edge each pillar node will belong.
 *   The tower node storage, however, can be re-used for the final graph created by {@link OSMReader} so we store the
 *   tower coordinates there already to save memory during import.
 * - We store an additional mapping between OSM node Ids and tag indices that point into a list of node tags. We use
 *   a different mapping, because we store node tags for only a small fraction of all OSM nodes.
 * </pre>
 */
class OSMNodeData {
    static final long JUNCTION_NODE = -2;
    static final long EMPTY_NODE = -1;
    static final long END_NODE = 0;
    static final long INTERMEDIATE_NODE = 1;
    static final long CONNECTION_NODE = 2;

    // this map stores our internal node id for each OSM node.
    // For tower nodes, the value is a negative id (see towerNodeToId).
    // For pillar nodes, the value is a packed lat/lon long (see packLatLon).
    // null until freeze(): during pass1 we only append raw observations to nodeObs.
    private LongLongMap idsByOsmNodeIds;

    // pass1 append buffer: one packed long per way-node occurrence, (osmNodeId << 1) | isEnd.
    // No dedup/merge during pass1 - that is deferred to freeze() (sort + merge runs), which is the
    // only place the node type of a shared node can be decided. Appending is O(1) and cache-friendly,
    // unlike the per-occurrence B-tree putOrCompute it replaces. Paged, so it is planet-scale.
    private PagedLongArray nodeObs;

    private final PointAccess towerNodes;

    // this map stores an index for each OSM node we keep the node tags of. a value of -1 means there is no entry yet.
    private final LongLongMap nodeTagIndicesByOsmNodeIds;

    // stores node tags
    private final KVStorage nodeKVStorage;
    // collect all nodes that should be split and a barrier edge should be created between them.
    private final LongSet nodesToBeSplit;

    private int nextTowerId = 0;
    // we use negative ids to create artificial OSM node ids
    private long nextArtificialOSMNodeId = -Long.MAX_VALUE;

    public OSMNodeData(PointAccess nodeAccess, Directory directory) {
        // pass1 just appends one long per way-node occurrence; freeze() turns it into the read map.
        nodeObs = new PagedLongArray();
        towerNodes = nodeAccess;

        nodeTagIndicesByOsmNodeIds = new GHLongLongBTree(200, 4, -1);
        nodesToBeSplit = new LongScatterSet();
        nodeKVStorage = new KVStorage(directory, false).create(100);
    }

    public boolean is3D() {
        return towerNodes.is3D();
    }

    /**
     * @return the internal id stored for the given OSM node id. use {@link #isTowerNode} etc. to find out what this
     * id means
     */
    public long getId(long osmNodeId) {
        return idsByOsmNodeIds.get(osmNodeId);
    }

    /**
     * Called once between pass1 and pass2, when the OSM-node-id key set is fixed. Turns the appended
     * pass1 occurrences ({@link #nodeObs}) into the read map: sort by id, merge each run of equal ids
     * into its final node type ({@link #mergeNodeType}), and build a read-optimal static blocked B-tree
     * ({@link BlockedBTreeLongLongMap}, the "B-tree layout" from Khuong &amp; Morin, arXiv:1509.05053)
     * from the resulting sorted (key,value) arrays. No B-tree is built during pass1 at all. pass2's few
     * artificial split nodes go to a small overflow B-tree.
     */
    void freeze() {
        if (idsByOsmNodeIds != null)
            return;
        // sort the appended occurrences so equal osm node ids become adjacent runs (radix scratch is
        // released before the map is built, so peak memory is ~ 8*occurrences + 16*uniqueKeys bytes)
        final long m = nodeObs.size();
        final PagedLongArray obs = PagedLongArray.radixSort(nodeObs, m);
        nodeObs = null;

        // count unique keys (needed to size the blocked B-tree)
        long u = 0;
        for (long i = 0; i < m; ) {
            long key = obs.get(i) >>> 1;
            u++;
            do i++; while (i < m && (obs.get(i) >>> 1) == key);
        }

        // stream the sorted occurrences, merging each run of equal ids into its final node type, and
        // feed them straight into the blocked B-tree - no intermediate sorted key/value arrays
        BlockedBTreeLongLongMap.SortedSource src = new BlockedBTreeLongLongMap.SortedSource() {
            long i = 0, curKey, curValue;

            @Override
            public boolean next() {
                if (i >= m)
                    return false;
                long key = obs.get(i) >>> 1;
                long v = EMPTY_NODE;
                do {
                    long nt = ((obs.get(i) & 1L) == 1L) ? END_NODE : INTERMEDIATE_NODE;
                    v = (v == EMPTY_NODE) ? nt : mergeNodeType(v, nt);
                    i++;
                } while (i < m && (obs.get(i) >>> 1) == key);
                curKey = key;
                curValue = v;
                return true;
            }

            @Override
            public long key() {
                return curKey;
            }

            @Override
            public long value() {
                return curValue;
            }
        };
        LongLongMap overflow = new GHLongLongBTree(200, 8, EMPTY_NODE);
        idsByOsmNodeIds = new BlockedBTreeLongLongMap(u, src, EMPTY_NODE, overflow);
        obs.release();
    }

    public static boolean isTowerNode(long id) {
        // tower nodes are indexed -3, -4, -5, ...
        return id < JUNCTION_NODE;
    }

    public static boolean isPillarNode(long id) {
        // pillar nodes are indexed 3, 4, 5, ..
        return id > CONNECTION_NODE;
    }

    public static boolean isNodeId(long id) {
        return id > CONNECTION_NODE || id < JUNCTION_NODE;
    }

    /**
     * Records one occurrence of an OSM node in a way. {@code newNodeType} is {@link #END_NODE} for the
     * first/last node of the way and {@link #INTERMEDIATE_NODE} otherwise. During pass1 we only append
     * the observation; the actual node type (which needs to see all occurrences of the node) is decided
     * in {@link #freeze()}.
     */
    public void setOrUpdateNodeType(long osmNodeId, long newNodeType) {
        // pack the single isEnd bit next to the id; sorting by this groups a node's occurrences together
        nodeObs.add((osmNodeId << 1) | (newNodeType == END_NODE ? 1L : 0L));
    }

    // Merge rule for a node seen more than once, reconstructing the pass1 putOrCompute in
    // WaySegmentParser: two ways sharing only their END node -> connection node, otherwise junction.
    // isEnd of an occurrence is encoded as newNodeType == END_NODE, so this is a pure function of the
    // previous value and the new occurrence's type -> the merge is order-independent and deferrable.
    private static long mergeNodeType(long prev, long newNodeType) {
        return (prev == END_NODE && newNodeType == END_NODE) ? CONNECTION_NODE : JUNCTION_NODE;
    }

    /**
     * @return the number of mapped nodes (tower + pillar, but also including pillar nodes that were converted to tower)
     */
    public long getNodeCount() {
        // before freeze() this is the raw occurrence count (with duplicates); after, the unique count
        return idsByOsmNodeIds != null ? idsByOsmNodeIds.getSize() : nodeObs.size();
    }

    public long getTaggedNodeCount() {
        return nodeTagIndicesByOsmNodeIds.getSize();
    }

    /**
     * @return the number of nodes for which we store tags
     */
    public long getNodeTagCapacity() {
        return nodeKVStorage.getCapacity();
    }

    /**
     * Stores the given coordinates for the given OSM node ID, but only if a non-empty node type was set for this
     * OSM node ID previously. Elevation is not stored here — it is looked up later during edge creation.
     *
     * @return the node type this OSM node was associated with before this method was called
     */
    public long addCoordinatesIfMapped(long osmNodeId, double lat, double lon) {
        long nodeType = idsByOsmNodeIds.get(osmNodeId);
        if (nodeType == EMPTY_NODE)
            return nodeType;
        else if (nodeType == JUNCTION_NODE || nodeType == CONNECTION_NODE)
            addTowerNode(osmNodeId, lat, lon);
        else if (nodeType == INTERMEDIATE_NODE || nodeType == END_NODE)
            addPillarNode(osmNodeId, lat, lon);
        else
            throw new IllegalStateException("Unknown node type: " + nodeType + ", or coordinates already set. Possibly duplicate OSM node ID: " + osmNodeId);
        return nodeType;
    }

    private long addTowerNode(long osmId, double lat, double lon) {
        towerNodes.setNode(nextTowerId, lat, lon, Helper.ELE_UNKNOWN);
        long id = towerNodeToId(nextTowerId);
        idsByOsmNodeIds.put(osmId, id);
        nextTowerId++;
        if (nextTowerId == Integer.MAX_VALUE)
            throw new IllegalStateException("Tower node id overflow, too many tower nodes");
        return id;
    }

    /**
     * Packs lat/lon into a single positive long. The offsets ensure the packed value is always
     * positive and always > 2^32, which avoids collision with tower IDs (negative) and special
     * markers (-2 to 2).
     */
    static long packLatLon(double lat, double lon) {
        // degreeToInt(lat) is in [-900_000_000, 900_000_000], offset to [1, 1_800_000_001] (fits 31 bits)
        long latUnsigned = Helper.degreeToInt(lat) + 900_000_001L;
        // degreeToInt(lon) is in [-1_800_000_000, 1_800_000_000], offset to [1, 3_600_000_001] (fits 32 bits)
        // +1 not really necessary but is there for symmetry
        long lonUnsigned = Helper.degreeToInt(lon) + 1_800_000_001L;
        return (latUnsigned << 32) | lonUnsigned;
    }

    static double unpackLat(long packed) {
        // latUnsigned fits in 31 bits, so (int) cast is safe
        int latInt = (int) (packed >>> 32) - 900_000_001;
        return Helper.intToDegree(latInt);
    }

    static double unpackLon(long packed) {
        // lonUnsigned can exceed Integer.MAX_VALUE, so subtract in long space first
        int lonInt = (int) ((packed & 0xFFFFFFFFL) - 1_800_000_001L);
        return Helper.intToDegree(lonInt);
    }

    private long addPillarNode(long osmId, double lat, double lon) {
        long id = packLatLon(lat, lon);
        idsByOsmNodeIds.put(osmId, id);
        return id;
    }

    /**
     * Creates a copy of the coordinates stored for the given node ID
     *
     * @return the (artificial) OSM node ID created for the copied node and the associated ID
     */
    SegmentNode addCopyOfNode(SegmentNode node) {
        GHPoint3D point = getCoordinates(node.id);
        if (point == null)
            throw new IllegalStateException("Cannot copy node : " + node.osmNodeId + ", because it is missing");
        final long newOsmId = nextArtificialOSMNodeId++;
        long id = packLatLon(point.getLat(), point.getLon());
        if (idsByOsmNodeIds.put(newOsmId, id) != EMPTY_NODE)
            throw new IllegalStateException("Artificial osm node id already exists: " + newOsmId);
        return new SegmentNode(newOsmId, id, node.tags);
    }

    long convertPillarToTowerNode(long id, long osmNodeId) {
        if (!isPillarNode(id))
            throw new IllegalArgumentException("Not a pillar node: " + id);
        // Check if already converted: look up current value in BTree
        long current = idsByOsmNodeIds.get(osmNodeId);
        if (isTowerNode(current))
            throw new IllegalStateException("Pillar node was already converted to tower node: " + id);
        double lat = unpackLat(id);
        double lon = unpackLon(id);
        return addTowerNode(osmNodeId, lat, lon);
    }

    public GHPoint3D getCoordinates(long id) {
        if (isTowerNode(id)) {
            int tower = idToTowerNode(id);
            return new GHPoint3D(towerNodes.getLat(tower), towerNodes.getLon(tower), Double.NaN);
        } else if (isPillarNode(id)) {
            return new GHPoint3D(unpackLat(id), unpackLon(id), Double.NaN);
        } else
            return null;
    }

    public void addCoordinatesToPointList(long id, PointList pointList) {
        double lat, lon;
        if (isTowerNode(id)) {
            int tower = idToTowerNode(id);
            lat = towerNodes.getLat(tower);
            lon = towerNodes.getLon(tower);
        } else if (isPillarNode(id)) {
            lat = unpackLat(id);
            lon = unpackLon(id);
        } else
            throw new IllegalArgumentException();
        // elevation is NaN — filled in later during edge creation
        pointList.add(lat, lon, Double.NaN);
    }

    public void setTags(ReaderNode node) {
        int tagIndex = Math.toIntExact(nodeTagIndicesByOsmNodeIds.get(node.getId()));
        if (tagIndex == -1) {
            long pointer = nodeKVStorage.add(node.getTags().entrySet().stream().collect(
                    Collectors.toMap(Map.Entry::getKey, // same key
                            e -> new KVStorage.KValue(e.getValue() instanceof String ? KVStorage.cutString((String) e.getValue()) : e.getValue()))));
            // Shift right to use 4x more address space (pointers are 4-byte aligned)
            long shiftedPointer = pointer >> KVStorage.ALIGNMENT_SHIFT;
            if (shiftedPointer > Integer.MAX_VALUE)
                throw new IllegalStateException("Too many key value pairs are stored in node tags, was " + pointer);
            nodeTagIndicesByOsmNodeIds.put(node.getId(), (int) shiftedPointer);
        } else {
            throw new IllegalStateException("Cannot add tags twice, duplicate node OSM ID: " + node.getId());
        }
    }

    public Map<String, Object> getTags(long osmNodeId) {
        int shiftedIndex = Math.toIntExact(nodeTagIndicesByOsmNodeIds.get(osmNodeId));
        if (shiftedIndex < 0)
            return Collections.emptyMap();
        // Shift left to restore the actual byte offset
        long tagIndex = (long) shiftedIndex << KVStorage.ALIGNMENT_SHIFT;
        return nodeKVStorage.getMap(tagIndex);
    }

    public void release() {
        if (idsByOsmNodeIds != null)
            idsByOsmNodeIds.clear();
        nodeTagIndicesByOsmNodeIds.clear();
        nodeKVStorage.clear();
        nodesToBeSplit.clear();
    }

    public long towerNodeToId(long towerId) {
        return -towerId - 3;
    }

    public int idToTowerNode(long id) {
        if (-id - 3L > Integer.MAX_VALUE)
            throw new IllegalStateException("Invalid tower node id: " + id + ", limit exceeded");
        return Math.toIntExact(-id - 3);
    }

    public boolean setSplitNode(long osmNodeId) {
        return nodesToBeSplit.add(osmNodeId);
    }

    public void unsetSplitNode(long osmNodeId) {
        int removed = nodesToBeSplit.removeAll(osmNodeId);
        if (removed == 0)
            throw new IllegalStateException("Node " + osmNodeId + " was not a split node");
    }

    public boolean isSplitNode(long osmNodeId) {
        return nodesToBeSplit.contains(osmNodeId);
    }
}
