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
import com.graphhopper.coll.GHLongLongBTree;
import com.graphhopper.coll.LongLongMap;
import com.graphhopper.reader.ReaderNode;
import com.graphhopper.search.KVStorage;
import com.graphhopper.storage.Directory;
import com.graphhopper.util.PointAccess;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPoint3D;

import java.util.Collections;
import java.util.Map;
import java.util.function.DoubleSupplier;
import java.util.function.LongUnaryOperator;
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

    // this map stores our internal node id for each OSM node
    private final LongLongMap idsByOsmNodeIds;

    // here we store node coordinates, separated for pillar and tower nodes
    private final PillarInfo pillarNodes;
    private final PointAccess towerNodes;

    // this map stores an index for each OSM node we keep the node tags of. a value of -1 means there is no entry yet.
    private final LongLongMap nodeTagIndicesByOsmNodeIds;

    // stores node tags
    private final KVStorage nodeKVStorage;
    // collect all nodes that should be split and a barrier edge should be created between them.
    private final LongSet nodesToBeSplit;

    private int nextTowerId = 0;
    private long nextPillarId = 0;
    // we use negative ids to create artificial OSM node ids
    private long nextArtificialOSMNodeId = -Long.MAX_VALUE;

    public OSMNodeData(PointAccess nodeAccess, Directory directory) {
        // We use a b-tree that can store as many entries as there are longs. A tree is also more
        // memory efficient, because there is no waste for empty entries, and it also avoids
        // allocating big arrays when growing the size.
        idsByOsmNodeIds = new GHLongLongBTree(200, 5, EMPTY_NODE);
        towerNodes = nodeAccess;
        pillarNodes = new PillarInfo(towerNodes.is3D(), directory);

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

    public void setOrUpdateNodeType(long osmNodeId, long newNodeType, LongUnaryOperator nodeTypeUpdate) {
        long curr = idsByOsmNodeIds.get(osmNodeId);
        if (curr == EMPTY_NODE)
            idsByOsmNodeIds.put(osmNodeId, newNodeType);
        else
            idsByOsmNodeIds.put(osmNodeId, nodeTypeUpdate.applyAsLong(curr));
    }

    /**
     * @return the number of mapped nodes (tower + pillar, but also including pillar nodes that were converted to tower)
     */
    public long getNodeCount() {
        return idsByOsmNodeIds.getSize();
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
     * OSM node ID previously.
     *
     * @return the node type this OSM node was associated with before this method was called
     */
    public long addCoordinatesIfMapped(long osmNodeId, double lat, double lon, DoubleSupplier getEle) {
        long nodeType = idsByOsmNodeIds.get(osmNodeId);
        if (nodeType == EMPTY_NODE)
            return nodeType;
        else if (nodeType == JUNCTION_NODE || nodeType == CONNECTION_NODE)
            addTowerNode(osmNodeId, lat, lon, getEle.getAsDouble());
        else if (nodeType == INTERMEDIATE_NODE || nodeType == END_NODE)
            addPillarNode(osmNodeId, lat, lon, getEle.getAsDouble());
        else
            throw new IllegalStateException("Unknown node type: " + nodeType + ", or coordinates already set. Possibly duplicate OSM node ID: " + osmNodeId);
        return nodeType;
    }

    private long addTowerNode(long osmId, double lat, double lon, double ele) {
        towerNodes.setNode(nextTowerId, lat, lon, ele);
        long id = towerNodeToId(nextTowerId);
        idsByOsmNodeIds.put(osmId, id);
        nextTowerId++;
        if (nextTowerId == Integer.MAX_VALUE)
            throw new IllegalStateException("Tower node id overflow, too many tower nodes");
        return id;
    }

    private long addPillarNode(long osmId, double lat, double lon, double ele) {
        long id = pillarNodeToId(nextPillarId);
        if (id > idsByOsmNodeIds.getMaxValue())
            throw new IllegalStateException("id for pillar node cannot be bigger than " + idsByOsmNodeIds.getMaxValue());

        pillarNodes.setNode(nextPillarId, lat, lon, ele);
        idsByOsmNodeIds.put(osmId, id);
        nextPillarId++;
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
        if (idsByOsmNodeIds.put(newOsmId, INTERMEDIATE_NODE) != EMPTY_NODE)
            throw new IllegalStateException("Artificial osm node id already exists: " + newOsmId);
        long id = addPillarNode(newOsmId, point.getLat(), point.getLon(), point.getEle());
        return new SegmentNode(newOsmId, id, node.tags);
    }

    long convertPillarToTowerNode(long id, long osmNodeId) {
        if (!isPillarNode(id))
            throw new IllegalArgumentException("Not a pillar node: " + id);
        long pillar = idToPillarNode(id);
        double lat = pillarNodes.getLat(pillar);
        double lon = pillarNodes.getLon(pillar);
        double ele = pillarNodes.getEle(pillar);
        if (lat == Double.MAX_VALUE || lon == Double.MAX_VALUE)
            throw new IllegalStateException("Pillar node was already converted to tower node: " + id);

        pillarNodes.setNode(pillar, Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE);
        return addTowerNode(osmNodeId, lat, lon, ele);
    }

    public GHPoint3D getCoordinates(long id) {
        if (isTowerNode(id)) {
            int tower = idToTowerNode(id);
            return towerNodes.is3D()
                    ? new GHPoint3D(towerNodes.getLat(tower), towerNodes.getLon(tower), towerNodes.getEle(tower))
                    : new GHPoint3D(towerNodes.getLat(tower), towerNodes.getLon(tower), Double.NaN);
        } else if (isPillarNode(id)) {
            long pillar = idToPillarNode(id);
            return pillarNodes.is3D()
                    ? new GHPoint3D(pillarNodes.getLat(pillar), pillarNodes.getLon(pillar), pillarNodes.getEle(pillar))
                    : new GHPoint3D(pillarNodes.getLat(pillar), pillarNodes.getLon(pillar), Double.NaN);
        } else
            return null;
    }

    public void addCoordinatesToPointList(long id, PointList pointList) {
        double lat, lon;
        double ele = Double.NaN;
        if (isTowerNode(id)) {
            int tower = idToTowerNode(id);
            lat = towerNodes.getLat(tower);
            lon = towerNodes.getLon(tower);
            if (towerNodes.is3D())
                ele = towerNodes.getEle(tower);
        } else if (isPillarNode(id)) {
            long pillar = idToPillarNode(id);
            lat = pillarNodes.getLat(pillar);
            lon = pillarNodes.getLon(pillar);
            if (pillarNodes.is3D())
                ele = pillarNodes.getEle(pillar);
        } else
            throw new IllegalArgumentException();
        pointList.add(lat, lon, ele);
    }

    public void setTags(ReaderNode node) {
        int tagIndex = Math.toIntExact(nodeTagIndicesByOsmNodeIds.get(node.getId()));
        if (tagIndex == -1) {
            long pointer = nodeKVStorage.add(node.getTags().entrySet().stream().collect(
                    Collectors.toMap(Map.Entry::getKey, // same key
                            e -> new KVStorage.KValue(e.getValue() instanceof String ? KVStorage.cutString((String) e.getValue()) : e.getValue()))));
            if (pointer > Integer.MAX_VALUE)
                throw new IllegalStateException("Too many key value pairs are stored in node tags, was " + pointer);
            nodeTagIndicesByOsmNodeIds.put(node.getId(), (int) pointer);
        } else {
            throw new IllegalStateException("Cannot add tags twice, duplicate node OSM ID: " + node.getId());
        }
    }

    public Map<String, Object> getTags(long osmNodeId) {
        int tagIndex = Math.toIntExact(nodeTagIndicesByOsmNodeIds.get(osmNodeId));
        if (tagIndex < 0)
            return Collections.emptyMap();
        return nodeKVStorage.getMap(tagIndex);
    }

    public void release() {
        idsByOsmNodeIds.clear();
        pillarNodes.clear();
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

    public long pillarNodeToId(long pillarId) {
        return pillarId + 3;
    }

    public long idToPillarNode(long id) {
        return id - 3;
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
