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

package com.graphhopper.reader.osm

import com.carrotsearch.hppc.LongScatterSet
import com.carrotsearch.hppc.LongSet
import com.graphhopper.coll.GHLongLongBTree
import com.graphhopper.coll.LongLongMap
import com.graphhopper.reader.ReaderNode
import com.graphhopper.search.KVStorage
import com.graphhopper.storage.Directory
import com.graphhopper.util.Helper
import com.graphhopper.util.PointAccess
import com.graphhopper.util.PointList
import com.graphhopper.util.shapes.GHPoint3D
import java.util.function.LongUnaryOperator

/**
 * This class stores OSM node data while reading an OSM file in [WaySegmentParser]. It is not trivial to do this
 * in a memory-efficient way. We use the following approach:
 * <pre>
 * - For each OSM node we store an id that points to the nodes coordinates. We separate nodes into
 *   (potential) tower nodes and pillar nodes. We use the negative ids for tower nodes and positive
 *   ids for pillar nodes. The tower nodes are limited to ~2 billion nodes as we later use the ID as positive integer.
 * - We reserve a few special ids like [JUNCTION_NODE] to distinguish the different node types when we read the
 *   OSM file for the first time (pass1) in [WaySegmentParser]. We then assign actual ids in the second pass.
 * - We store the node coordinates for tower and pillar nodes in different places. The pillar node storage is only
 *   temporary, because at the time we store the coordinates it is unknown to which edge each pillar node will belong.
 *   The tower node storage, however, can be re-used for the final graph created by [OSMReader] so we store the
 *   tower coordinates there already to save memory during import.
 * - We store an additional mapping between OSM node Ids and tag indices that point into a list of node tags. We use
 *   a different mapping, because we store node tags for only a small fraction of all OSM nodes.
 * </pre>
 */
internal class OSMNodeData(nodeAccess: PointAccess, directory: Directory) {

    // this map stores our internal node id for each OSM node.
    // For tower nodes, the value is a negative id (see towerNodeToId).
    // For pillar nodes, the value is a packed lat/lon long (see packLatLon).
    private val idsByOsmNodeIds: LongLongMap

    private val towerNodes: PointAccess

    // this map stores an index for each OSM node we keep the node tags of. a value of -1 means there is no entry yet.
    private val nodeTagIndicesByOsmNodeIds: LongLongMap

    // stores node tags
    private val nodeKVStorage: KVStorage

    // collect all nodes that should be split and a barrier edge should be created between them.
    private val nodesToBeSplit: LongSet

    private var nextTowerId = 0

    // we use negative ids to create artificial OSM node ids
    private var nextArtificialOSMNodeId = -Long.MAX_VALUE

    init {
        // We use a b-tree that can store as many entries as there are longs. A tree is also more
        // memory efficient, because there is no waste for empty entries, and it also avoids
        // allocating big arrays when growing the size.
        // 8 bytes per value to hold packed lat/lon for pillar nodes (and negative tower IDs)
        idsByOsmNodeIds = GHLongLongBTree(200, 8, EMPTY_NODE)
        towerNodes = nodeAccess

        nodeTagIndicesByOsmNodeIds = GHLongLongBTree(200, 4, -1)
        nodesToBeSplit = LongScatterSet()
        nodeKVStorage = KVStorage(directory, false).create(100)
    }

    fun is3D(): Boolean = towerNodes.is3D

    /**
     * @return the internal id stored for the given OSM node id. use [isTowerNode] etc. to find out what this
     * id means
     */
    fun getId(osmNodeId: Long): Long = idsByOsmNodeIds.get(osmNodeId)

    fun setOrUpdateNodeType(osmNodeId: Long, newNodeType: Long, nodeTypeUpdate: LongUnaryOperator) {
        idsByOsmNodeIds.putOrCompute(osmNodeId, newNodeType, nodeTypeUpdate)
    }

    /**
     * @return the number of mapped nodes (tower + pillar, but also including pillar nodes that were converted to tower)
     */
    fun getNodeCount(): Long = idsByOsmNodeIds.size

    fun getTaggedNodeCount(): Long = nodeTagIndicesByOsmNodeIds.size

    /**
     * @return the number of nodes for which we store tags
     */
    fun getNodeTagCapacity(): Long = nodeKVStorage.getCapacity()

    /**
     * Stores the given coordinates for the given OSM node ID, but only if a non-empty node type was set for this
     * OSM node ID previously. Elevation is not stored here — it is looked up later during edge creation.
     *
     * @return the node type this OSM node was associated with before this method was called
     */
    fun addCoordinatesIfMapped(osmNodeId: Long, lat: Double, lon: Double): Long {
        val nodeType = idsByOsmNodeIds.get(osmNodeId)
        if (nodeType == EMPTY_NODE)
            return nodeType
        else if (nodeType == JUNCTION_NODE || nodeType == CONNECTION_NODE)
            addTowerNode(osmNodeId, lat, lon)
        else if (nodeType == INTERMEDIATE_NODE || nodeType == END_NODE)
            addPillarNode(osmNodeId, lat, lon)
        else
            throw IllegalStateException("Unknown node type: $nodeType, or coordinates already set. Possibly duplicate OSM node ID: $osmNodeId")
        return nodeType
    }

    private fun addTowerNode(osmId: Long, lat: Double, lon: Double): Long {
        towerNodes.setNode(nextTowerId, lat, lon, Helper.ELE_UNKNOWN)
        val id = towerNodeToId(nextTowerId.toLong())
        idsByOsmNodeIds.put(osmId, id)
        nextTowerId++
        if (nextTowerId == Int.MAX_VALUE)
            throw IllegalStateException("Tower node id overflow, too many tower nodes")
        return id
    }

    private fun addPillarNode(osmId: Long, lat: Double, lon: Double): Long {
        val id = packLatLon(lat, lon)
        idsByOsmNodeIds.put(osmId, id)
        return id
    }

    /**
     * Creates a copy of the coordinates stored for the given node ID
     *
     * @return the (artificial) OSM node ID created for the copied node and the associated ID
     */
    fun addCopyOfNode(node: SegmentNode): SegmentNode {
        val point = getCoordinates(node.id)
            ?: throw IllegalStateException("Cannot copy node : " + node.osmNodeId + ", because it is missing")
        val newOsmId = nextArtificialOSMNodeId++
        val id = packLatLon(point.getLat(), point.getLon())
        if (idsByOsmNodeIds.put(newOsmId, id) != EMPTY_NODE)
            throw IllegalStateException("Artificial osm node id already exists: $newOsmId")
        return SegmentNode(newOsmId, id, node.tags)
    }

    fun convertPillarToTowerNode(id: Long, osmNodeId: Long): Long {
        if (!isPillarNode(id))
            throw IllegalArgumentException("Not a pillar node: $id")
        // Check if already converted: look up current value in BTree
        val current = idsByOsmNodeIds.get(osmNodeId)
        if (isTowerNode(current))
            throw IllegalStateException("Pillar node was already converted to tower node: $id")
        val lat = unpackLat(id)
        val lon = unpackLon(id)
        return addTowerNode(osmNodeId, lat, lon)
    }

    fun getCoordinates(id: Long): GHPoint3D? {
        return if (isTowerNode(id)) {
            val tower = idToTowerNode(id)
            GHPoint3D(towerNodes.getLat(tower), towerNodes.getLon(tower), Double.NaN)
        } else if (isPillarNode(id)) {
            GHPoint3D(unpackLat(id), unpackLon(id), Double.NaN)
        } else
            null
    }

    fun addCoordinatesToPointList(id: Long, pointList: PointList) {
        val lat: Double
        val lon: Double
        if (isTowerNode(id)) {
            val tower = idToTowerNode(id)
            lat = towerNodes.getLat(tower)
            lon = towerNodes.getLon(tower)
        } else if (isPillarNode(id)) {
            lat = unpackLat(id)
            lon = unpackLon(id)
        } else
            throw IllegalArgumentException()
        // elevation is NaN — filled in later during edge creation
        pointList.add(lat, lon, Double.NaN)
    }

    fun setTags(node: ReaderNode) {
        val tagIndex = Math.toIntExact(nodeTagIndicesByOsmNodeIds.get(node.id))
        if (tagIndex == -1) {
            // use a HashMap like Java's Collectors.toMap did, so the KV serialization order is unchanged
            val entries = HashMap<String, KVStorage.KValue>()
            for ((key, value) in node.getTags())
                entries[key] = KVStorage.KValue(if (value is String) KVStorage.cutString(value) else value)
            val pointer = nodeKVStorage.add(entries)
            // Shift right to use 4x more address space (pointers are 4-byte aligned)
            val shiftedPointer = pointer shr KVStorage.ALIGNMENT_SHIFT
            if (shiftedPointer > Int.MAX_VALUE)
                throw IllegalStateException("Too many key value pairs are stored in node tags, was $pointer")
            nodeTagIndicesByOsmNodeIds.put(node.id, shiftedPointer.toInt().toLong())
        } else {
            throw IllegalStateException("Cannot add tags twice, duplicate node OSM ID: " + node.id)
        }
    }

    fun getTags(osmNodeId: Long): Map<String, Any> {
        val shiftedIndex = Math.toIntExact(nodeTagIndicesByOsmNodeIds.get(osmNodeId))
        if (shiftedIndex < 0)
            return emptyMap()
        // Shift left to restore the actual byte offset
        val tagIndex = shiftedIndex.toLong() shl KVStorage.ALIGNMENT_SHIFT
        return nodeKVStorage.getMap(tagIndex)
    }

    fun release() {
        idsByOsmNodeIds.clear()
        nodeTagIndicesByOsmNodeIds.clear()
        nodeKVStorage.clear()
        nodesToBeSplit.clear()
    }

    fun towerNodeToId(towerId: Long): Long = -towerId - 3

    fun idToTowerNode(id: Long): Int {
        if (-id - 3L > Int.MAX_VALUE)
            throw IllegalStateException("Invalid tower node id: $id, limit exceeded")
        return Math.toIntExact(-id - 3)
    }

    fun setSplitNode(osmNodeId: Long): Boolean = nodesToBeSplit.add(osmNodeId)

    fun unsetSplitNode(osmNodeId: Long) {
        val removed = nodesToBeSplit.removeAll(osmNodeId)
        if (removed == 0)
            throw IllegalStateException("Node $osmNodeId was not a split node")
    }

    fun isSplitNode(osmNodeId: Long): Boolean = nodesToBeSplit.contains(osmNodeId)

    companion object {
        const val JUNCTION_NODE = -2L
        const val EMPTY_NODE = -1L
        const val END_NODE = 0L
        const val INTERMEDIATE_NODE = 1L
        const val CONNECTION_NODE = 2L

        @JvmStatic
        fun isTowerNode(id: Long): Boolean =
            // tower nodes are indexed -3, -4, -5, ...
            id < JUNCTION_NODE

        @JvmStatic
        fun isPillarNode(id: Long): Boolean =
            // pillar nodes are indexed 3, 4, 5, ..
            id > CONNECTION_NODE

        @JvmStatic
        fun isNodeId(id: Long): Boolean =
            id > CONNECTION_NODE || id < JUNCTION_NODE

        /**
         * Packs lat/lon into a single positive long. The offsets ensure the packed value is always
         * positive and always > 2^32, which avoids collision with tower IDs (negative) and special
         * markers (-2 to 2).
         */
        @JvmStatic
        fun packLatLon(lat: Double, lon: Double): Long {
            // degreeToInt(lat) is in [-900_000_000, 900_000_000], offset to [1, 1_800_000_001] (fits 31 bits)
            val latUnsigned = Helper.degreeToInt(lat) + 900_000_001L
            // degreeToInt(lon) is in [-1_800_000_000, 1_800_000_000], offset to [1, 3_600_000_001] (fits 32 bits)
            // +1 not really necessary but is there for symmetry
            val lonUnsigned = Helper.degreeToInt(lon) + 1_800_000_001L
            return (latUnsigned shl 32) or lonUnsigned
        }

        @JvmStatic
        fun unpackLat(packed: Long): Double {
            // latUnsigned fits in 31 bits, so (int) cast is safe
            val latInt = (packed ushr 32).toInt() - 900_000_001
            return Helper.intToDegree(latInt)
        }

        @JvmStatic
        fun unpackLon(packed: Long): Double {
            // lonUnsigned can exceed Integer.MAX_VALUE, so subtract in long space first
            val lonInt = ((packed and 0xFFFFFFFFL) - 1_800_000_001L).toInt()
            return Helper.intToDegree(lonInt)
        }
    }
}
