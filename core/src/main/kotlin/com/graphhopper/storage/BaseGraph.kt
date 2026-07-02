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
package com.graphhopper.storage

import com.graphhopper.routing.ev.BooleanEncodedValue
import com.graphhopper.routing.ev.DecimalEncodedValue
import com.graphhopper.routing.ev.EdgeIntAccess
import com.graphhopper.routing.ev.EnumEncodedValue
import com.graphhopper.routing.ev.IntEncodedValue
import com.graphhopper.routing.ev.StringEncodedValue
import com.graphhopper.routing.util.AllEdgesIterator
import com.graphhopper.routing.util.EdgeFilter
import com.graphhopper.routing.util.EncodingManager
import com.graphhopper.routing.weighting.Weighting
import com.graphhopper.search.KVStorage
import com.graphhopper.util.BitUtil
import com.graphhopper.util.Constants
import com.graphhopper.util.EdgeExplorer
import com.graphhopper.util.EdgeIterator
import com.graphhopper.util.EdgeIteratorState
import com.graphhopper.util.FetchMode
import com.graphhopper.util.GHUtility
import com.graphhopper.util.Helper
import com.graphhopper.util.Helper.nf
import com.graphhopper.util.Parameters.Details.STREET_NAME
import com.graphhopper.util.PointList
import com.graphhopper.util.shapes.BBox
import java.io.Closeable
import java.util.function.Consumer
import java.util.function.IntConsumer
import java.util.function.IntUnaryOperator

/**
 * The base graph handles nodes and edges file format. It can be used with different Directory
 * implementations like RAMDirectory for fast access or via MMapDirectory for virtual-memory and not
 * thread safe usage.
 *
 * Note: A RAM DataAccess Object is thread-safe in itself but if used in this Graph implementation
 * it is not write thread safe.
 *
 * Life cycle: (1) object creation, (2) configuration via setters &amp; getters, (3) create or
 * loadExisting, (4) usage, (5) flush, (6) close
 */
class BaseGraph(dir: Directory, withElevation: Boolean, withTurnCosts: Boolean, bytesForFlags: Int) : Graph, Closeable {

    @JvmField
    internal val bitUtil: BitUtil = BitUtil.LITTLE

    // length | nodeA | nextNode | ... | nodeB
    private val wayGeometry: DataAccess = dir.create("geometry", if (dir.defaultType.isStoring) DAType.MMAP else dir.defaultType)

    @JvmField
    internal val edgeKVStorage: KVStorage = KVStorage(dir, true)

    @JvmField
    internal val store: BaseGraphNodesAndEdges = BaseGraphNodesAndEdges(dir, withElevation, withTurnCosts, bytesForFlags)

    override val nodeAccess: NodeAccess = GHNodeAccess(store)

    // can be null if turn costs are not supported
    override val turnCostStorage: TurnCostStorage? =
        if (withTurnCosts) TurnCostStorage(this, dir.create("turn_costs", dir.getDefaultType("turn_costs", true))) else null

    private val dir: Directory = dir
    private var initialized = false
    private var minGeoRef: Long = 0
    private var maxGeoRef: Long = 0

    @JvmName("getStore")
    internal fun getStore(): BaseGraphNodesAndEdges = store

    private fun getOtherNode(nodeThis: Int, edgePointer: Long): Int {
        val nodeA = store.getNodeA(edgePointer)
        return if (nodeThis == nodeA) store.getNodeB(edgePointer) else nodeA
    }

    private fun isAdjacentToNode(node: Int, edgePointer: Long): Boolean =
        store.getNodeA(edgePointer) == node || store.getNodeB(edgePointer) == node

    fun debugPrint() {
        store.debugPrint()
    }

    override val baseGraph: BaseGraph
        get() = this

    val isInitialized: Boolean
        get() = initialized

    private fun checkNotInitialized() {
        if (initialized)
            throw IllegalStateException("You cannot configure this BaseGraph "
                    + "after calling create or loadExisting. Calling one of the methods twice is also not allowed.")
    }

    private fun loadWayGeometryHeader() {
        val geometryVersion = wayGeometry.getHeader(0)
        GHUtility.checkDAVersion(wayGeometry.name, Constants.VERSION_GEOMETRY, geometryVersion)
        minGeoRef = bitUtil.toLong(
            wayGeometry.getHeader(4),
            wayGeometry.getHeader(8)
        )
        maxGeoRef = bitUtil.toLong(
            wayGeometry.getHeader(12),
            wayGeometry.getHeader(16)
        )
    }

    private fun setWayGeometryHeader() {
        wayGeometry.setHeader(0, Constants.VERSION_GEOMETRY)
        wayGeometry.setHeader(4, bitUtil.getIntLow(minGeoRef))
        wayGeometry.setHeader(8, bitUtil.getIntHigh(minGeoRef))
        wayGeometry.setHeader(12, bitUtil.getIntLow(maxGeoRef))
        wayGeometry.setHeader(16, bitUtil.getIntHigh(maxGeoRef))
    }

    private fun setInitialized() {
        initialized = true
    }

    private fun supportsTurnCosts(): Boolean = turnCostStorage != null

    override val nodes: Int
        get() = store.getNodes()

    override val edges: Int
        get() = store.getEdges()

    val edgeKeys: Int
        get() = 2 * store.getEdges()

    override val bounds: BBox
        get() = store.getBounds()

    @Synchronized
    fun freeze() {
        if (isFrozen)
            throw IllegalStateException("base graph already frozen")
        store.frozen = true
    }

    val isFrozen: Boolean
        @Synchronized get() = store.frozen

    fun create(initSize: Long): BaseGraph {
        checkNotInitialized()
        dir.create()
        store.create(initSize)

        val smallInitSize = Math.min(initSize, 2000)
        wayGeometry.create(smallInitSize)
        edgeKVStorage.create(smallInitSize)
        if (supportsTurnCosts()) {
            turnCostStorage!!.create(smallInitSize)
        }
        setInitialized()
        // 0 stands for no separate geoRef, <0 stands for no separate geoRef but existing edge copies
        minGeoRef = -1
        maxGeoRef = 1
        return this
    }

    fun toDetailsString(): String =
        store.toDetailsString() + ", " +
                "name:(" + edgeKVStorage.getCapacity() / Helper.MB + "MB), " +
                "geo:" + nf(maxGeoRef) + "/" + nf(minGeoRef) + "(" + wayGeometry.capacity / Helper.MB + "MB)"

    /**
     * Flush and free resources that are not needed for post-processing (way geometries and KVStorage for edges).
     */
    fun flushAndCloseGeometryAndNameStorage() {
        setWayGeometryHeader()

        wayGeometry.flush()
        wayGeometry.close()

        edgeKVStorage.flush()
        edgeKVStorage.close()
    }

    fun flush() {
        if (!wayGeometry.isClosed) {
            setWayGeometryHeader()
            wayGeometry.flush()
        }

        if (!edgeKVStorage.isClosed)
            edgeKVStorage.flush()

        store.flush()
        if (supportsTurnCosts()) {
            turnCostStorage!!.flush()
        }
    }

    override fun close() {
        if (!wayGeometry.isClosed)
            wayGeometry.close()
        if (!edgeKVStorage.isClosed)
            edgeKVStorage.close()
        store.close()
        if (supportsTurnCosts()) {
            turnCostStorage!!.close()
        }
    }

    val capacity: Long
        get() = store.getCapacity() + edgeKVStorage.getCapacity() +
                wayGeometry.capacity + (if (supportsTurnCosts()) turnCostStorage!!.capacity else 0)

    @JvmName("getMaxGeoRef")
    internal fun getMaxGeoRef(): Long = maxGeoRef

    fun loadExisting(): Boolean {
        checkNotInitialized()

        if (!store.loadExisting())
            return false

        if (!wayGeometry.loadExisting())
            return false

        if (!edgeKVStorage.loadExisting())
            return false

        if (supportsTurnCosts() && !turnCostStorage!!.loadExisting())
            return false

        setInitialized()
        loadWayGeometryHeader()
        return true
    }

    /**
     * This method copies the properties of one [EdgeIteratorState] to another.
     *
     * @return the updated iterator the properties where copied to.
     */
    private fun copyProperties(from: EdgeIteratorState, to: EdgeIteratorStateImpl): EdgeIteratorState {
        val edgePointer = store.toEdgePointer(to.edge)
        store.writeFlags(edgePointer, from.flags)

        // copy the rest with higher level API
        to.setDistance_mm(from.distance_mm).setKeyValues(from.keyValues).setWayGeometry(from.fetchWayGeometry(FetchMode.PILLAR_ONLY))

        return to
    }

    /**
     * Create edge between nodes a and b
     *
     * @return EdgeIteratorState of newly created edge
     */
    override fun edge(a: Int, b: Int): EdgeIteratorState {
        if (isFrozen)
            throw IllegalStateException("Cannot create edge if graph is already frozen")
        if (a == b)
            // Loop edges would only make sense if their attributes were the same for both 'directions',
            // because for routing algorithms (which ignore the way geometry) loop edges do not even
            // have a well-defined 'direction'. So we either need to make sure the attributes
            // are the same for both directions, or reject loop edges altogether. Since we currently
            // don't know any use-case for loop edges in road networks (there is one for PT),
            // we reject them here.
            throw IllegalArgumentException("Loop edges are not supported, got: $a - $b")
        val edgeId = store.edge(a, b)
        val edge = EdgeIteratorStateImpl(this)
        val valid = edge.init(edgeId, b)
        assert(valid)
        return edge
    }

    /**
     * Creates a copy of a given edge with the same properties.
     *
     * @param reuseGeometry If true the copy uses the same pointer to the geometry,
     *                      so changing the geometry would alter the geometry for both edges!
     */
    fun copyEdge(edge: Int, reuseGeometry: Boolean): EdgeIteratorState {
        val edgeState = getEdgeIteratorState(edge, Int.MIN_VALUE) as EdgeIteratorStateImpl
        val newEdge = edge(edgeState.baseNode, edgeState.adjNode)
            .setFlags(edgeState.flags)
            .setDistance_mm(edgeState.distance_mm)
            .setKeyValues(edgeState.keyValues) as EdgeIteratorStateImpl
        if (reuseGeometry) {
            // We use the same geo ref for the copied edge. This saves memory because we are not duplicating
            // the geometry, and it allows to identify the copies of a given edge.
            val edgePointer = edgeState.edgePointer
            var geoRef = store.getGeoRef(edgePointer)
            if (geoRef == 0L) {
                // No geometry for this edge, but we need to be able to identify the copied edges later, so
                // we use a dedicated negative value for the geo ref.
                geoRef = minGeoRef
                store.setGeoRef(edgePointer, geoRef)
                minGeoRef--
            }
            store.setGeoRef(newEdge.edgePointer, geoRef)
        } else {
            newEdge.setWayGeometry(edgeState.fetchWayGeometry(FetchMode.PILLAR_ONLY))
        }
        return newEdge
    }

    /**
     * Runs the given action on the given edge and all its copies that were created with 'reuseGeometry=true'.
     */
    fun forEdgeAndCopiesOfEdge(explorer: EdgeExplorer, edge: EdgeIteratorState, consumer: Consumer<EdgeIteratorState>) {
        val geoRef = store.getGeoRef((edge as EdgeIteratorStateImpl).edgePointer)
        if (geoRef == 0L) {
            // 0 means there is no geometry (and no copy of this edge), but of course not all edges
            // without geometry are copies of each other, so we need to return early
            consumer.accept(edge)
            return
        }
        val iter = explorer.setBaseNode(edge.baseNode)
        while (iter.next()) {
            val geoRefBefore = store.getGeoRef((iter as EdgeIteratorStateImpl).edgePointer)
            if (geoRefBefore == geoRef)
                consumer.accept(iter)
            if (store.getGeoRef(iter.edgePointer) != geoRefBefore)
                throw IllegalStateException("The consumer must not change the geo ref")
        }
    }

    fun forEdgeAndCopiesOfEdge(explorer: EdgeExplorer, node: Int, edge: Int, consumer: IntConsumer) {
        val geoRef = store.getGeoRef(store.toEdgePointer(edge))
        if (geoRef == 0L) {
            // 0 means there is no geometry (and no copy of this edge), but of course not all edges
            // without geometry are copies of each other, so we need to return early
            consumer.accept(edge)
            return
        }
        val iter = explorer.setBaseNode(node)
        while (iter.next()) {
            val geoRefBefore = store.getGeoRef((iter as EdgeIteratorStateImpl).edgePointer)
            if (geoRefBefore == geoRef)
                consumer.accept(iter.edge)
        }
    }

    fun sortEdges(getNewEdgeForOldEdge: IntUnaryOperator) {
        if (isFrozen)
            throw IllegalStateException("Cannot sort edges if graph is already frozen")
        store.sortEdges(getNewEdgeForOldEdge)
        if (supportsTurnCosts())
            turnCostStorage!!.sortEdges(getNewEdgeForOldEdge)
    }

    fun relabelNodes(getNewNodeForOldNode: IntUnaryOperator) {
        if (isFrozen)
            throw IllegalStateException("Cannot relabel nodes if graph is already frozen")
        store.relabelNodes(getNewNodeForOldNode)
        if (supportsTurnCosts())
            turnCostStorage!!.sortNodes()
    }

    override fun getEdgeIteratorState(edgeId: Int, adjNode: Int): EdgeIteratorState? {
        val edge = EdgeIteratorStateImpl(this)
        if (edge.init(edgeId, adjNode))
            return edge
        // if edgeId exists but adjacent nodes do not match
        return null
    }

    override fun getEdgeIteratorStateForKey(edgeKey: Int): EdgeIteratorState {
        val edge = EdgeIteratorStateImpl(this)
        edge.init(edgeKey)
        return edge
    }

    override fun createEdgeExplorer(filter: EdgeFilter): EdgeExplorer = EdgeIteratorImpl(this, filter)

    override val allEdges: AllEdgesIterator
        get() = AllEdgeIterator(this)

    /**
     * Like [allEdges] but restricted to edge ids in `[from, toExclusive)`.
     * Enables splitting an all-edges traversal across threads without each thread fast-forwarding
     * from edge 0.
     */
    fun getAllEdges(from: Int, toExclusive: Int): AllEdgesIterator = AllEdgeIterator(this, from, toExclusive)

    override fun wrapWeighting(weighting: Weighting): Weighting = weighting

    override fun getOtherNode(edge: Int, node: Int): Int {
        val edgePointer = store.toEdgePointer(edge)
        return getOtherNode(node, edgePointer)
    }

    override fun isAdjacentToNode(edge: Int, node: Int): Boolean {
        val edgePointer = store.toEdgePointer(edge)
        return isAdjacentToNode(node, edgePointer)
    }

    /**
     * @return true if the specified node is the adjacent node of the specified edge
     * (relative to the direction in which the edge is stored).
     */
    fun isAdjNode(edge: Int, node: Int): Boolean {
        val edgePointer = store.toEdgePointer(edge)
        return node == store.getNodeB(edgePointer)
    }

    private fun setWayGeometry_(pillarNodes: PointList?, edgePointer: Long, reverse: Boolean) {
        if (pillarNodes != null && !pillarNodes.isEmpty) {
            if (pillarNodes.dimension != nodeAccess.getDimension())
                throw IllegalArgumentException("Cannot use pointlist which is " + pillarNodes.dimension
                        + "D for graph which is " + nodeAccess.getDimension() + "D")

            val existingGeoRef = store.getGeoRef(edgePointer)
            if (existingGeoRef < 0)
                // users of this method might not be aware that after changing the geo ref it is no
                // longer possible to find the copies corresponding to an edge, so we deny this
                throw IllegalStateException("This edge has already been copied so we can no longer change the geometry, pointer=$edgePointer")

            val encoded = createWayGeometryBytes(pillarNodes, reverse)

            if (existingGeoRef > 0) {
                val existingByteSize = readEntryByteSize(existingGeoRef)
                if (encoded.length <= existingByteSize) {
                    // Fits in the existing slot. The new entry's length header tells the
                    // reader when to stop, so any leftover bytes are harmless.
                    wayGeometry.setBytes(existingGeoRef, encoded.bytes, encoded.length)
                    return
                }
                // Abandon the slot and allocate a fresh one. The old bytes become unreachable.
                // As we just need this for EdgeElevationInterpolator this is currently rare (<0.03%).
                // It is a bit of work but we could avoid it: do edge.setWayGeometry in OSMReader after post-import edge interpolation.
            }
            val geoRef = nextGeoRef(encoded.length)
            wayGeometry.ensureCapacity(geoRef + encoded.length)
            wayGeometry.setBytes(geoRef, encoded.bytes, encoded.length)
            store.setGeoRef(edgePointer, geoRef)
        } else {
            store.setGeoRef(edgePointer, 0L)
        }
    }

    val edgeAccess: EdgeIntAccess
        get() = store

    /**
     * Creates bytes from the geometry for storage. First comes 1 byte for the bytes length L if L < 0xFF.
     * If more bytes are required then L == 0xFF and 4 more bytes are required.
     * Then come the pillar bytes: for each coordinate the zigzag-varint Δlat, Δlon, (Δele if 3D) is calculated.
     * @return The bytes in EncodedBytes may be over-allocated, i.e. use EncodedBytes.length and not EncodedBytes.bytes.length.
     */
    private fun createWayGeometryBytes(pillarNodes: PointList, reverse: Boolean): EncodedBytes {
        val len = pillarNodes.size()
        if (len > MAX_PILLAR_NODES) throw IllegalArgumentException("Too many pillar nodes: $len")
        if (reverse) pillarNodes.reverse()

        val perPillarMax = if (nodeAccess.is3D()) 3 * MAX_VARINT_BYTES else 2 * MAX_VARINT_BYTES
        // Reserve worst case = byte count header (5 bytes) + pillar bytes. We write pillar bytes
        // starting at offset 1 (the optimistic 1-byte-header position); if they fit in 1 byte then
        // no shift is needed. Otherwise we shift to make room for the 5 bytes header.
        val bytes = ByteArray(5 + len * perPillarMax)

        var offset = 1
        // Use long to avoid int overflow when computing deltas near the anti-meridian (e.g. 179.9 -> -179.9).
        // The varint encoder/decoder receives the lower 32 bits (int cast), so the round-trip is exact.
        var prevLat = 0L
        var prevLon = 0L
        var prevEle = 0
        val is3D = nodeAccess.is3D()
        for (i in 0 until len) {
            val lat = Helper.degreeToInt(pillarNodes.getLat(i)).toLong()
            val lon = Helper.degreeToInt(pillarNodes.getLon(i)).toLong()
            offset = writeZigZagVarInt(bytes, offset, (lat - prevLat).toInt())
            offset = writeZigZagVarInt(bytes, offset, (lon - prevLon).toInt())
            prevLat = lat
            prevLon = lon
            if (is3D) {
                val ele = Helper.eleToUInt(pillarNodes.getEle(i))
                offset = writeZigZagVarInt(bytes, offset, ele - prevEle)
                prevEle = ele
            }
        }
        val pillarBytesLen = offset - 1
        if (pillarBytesLen < 0xFF) {
            bytes[0] = pillarBytesLen.toByte()
            return EncodedBytes(bytes, 1 + pillarBytesLen)
        }
        // Rare fallback for long PointLists (0.1%): shift pillar bytes 4 positions to make room for [0xFF | int len].
        System.arraycopy(bytes, 1, bytes, 5, pillarBytesLen)
        bytes[0] = 0xFF.toByte()
        bitUtil.fromInt(bytes, pillarBytesLen, 1)
        return EncodedBytes(bytes, 5 + pillarBytesLen)
    }

    // Total byte size of the entry stored at geoRef (header + pillar bytes).
    private fun readEntryByteSize(geoRef: Long): Int {
        val header = wayGeometry.getByte(geoRef).toInt() and 0xFF
        return if (header < 0xFF) 1 + header else 5 + wayGeometry.getInt(geoRef + 1)
    }

    private fun fetchWayGeometry_(edgePointer: Long, reverse: Boolean, mode: FetchMode, baseNode: Int, adjNode: Int): PointList {
        if (mode == FetchMode.TOWER_ONLY) {
            // no reverse handling required as adjNode and baseNode is already properly switched
            val towerNodes = PointList(2, nodeAccess.is3D())
            towerNodes.add(nodeAccess, baseNode)
            towerNodes.add(nodeAccess, adjNode)
            return towerNodes
        }
        val geoRef = store.getGeoRef(edgePointer)
        var bytes: ByteArray? = null
        var pillarBytesLen = 0
        val is3D = nodeAccess.is3D()
        if (geoRef > 0) {
            val header = wayGeometry.getByte(geoRef).toInt() and 0xFF
            val pillarBytesStart: Long
            if (header < 0xFF) {
                pillarBytesLen = header
                pillarBytesStart = geoRef + 1
            } else {
                pillarBytesLen = wayGeometry.getInt(geoRef + 1)
                pillarBytesStart = geoRef + 5
            }
            val maxPillarBytesLen = MAX_PILLAR_NODES * (if (is3D) 3 else 2) * MAX_VARINT_BYTES
            if (pillarBytesLen < 0 || pillarBytesLen > maxPillarBytesLen)
                throw IllegalStateException("Invalid pillar bytes length " + pillarBytesLen + " for edge at geoRef " + geoRef
                        + ". Expected [0, " + maxPillarBytesLen + "].")
            bytes = ByteArray(pillarBytesLen)
            wayGeometry.getBytes(pillarBytesStart, bytes, pillarBytesLen)
        } else if (mode == FetchMode.PILLAR_ONLY)
            return PointList.EMPTY

        // Estimate pillar node count from pillar-bytes size. PointList grows on demand.
        val countEstimate = if (is3D) pillarBytesLen / 3 else pillarBytesLen / 2
        val pillarNodes = PointList(getPointListLength(countEstimate, mode), is3D)
        if (reverse) {
            if (mode == FetchMode.ALL || mode == FetchMode.PILLAR_AND_ADJ)
                pillarNodes.add(nodeAccess, adjNode)
        } else if (mode == FetchMode.ALL || mode == FetchMode.BASE_AND_PILLAR)
            pillarNodes.add(nodeAccess, baseNode)

        // Use long accumulators to mirror the encode path and avoid implicit int overflow near the anti-meridian.
        var curLat = 0L
        var curLon = 0L
        var curEle = 0
        val pos = intArrayOf(0)
        while (pos[0] < pillarBytesLen) {
            curLat = (curLat + readZigZagVarInt(bytes!!, pos)).toInt().toLong()
            curLon = (curLon + readZigZagVarInt(bytes, pos)).toInt().toLong()
            val lat = Helper.intToDegree(curLat.toInt())
            val lon = Helper.intToDegree(curLon.toInt())
            if (is3D) {
                curEle += readZigZagVarInt(bytes, pos)
                pillarNodes.add(lat, lon, Helper.uIntToEle(curEle))
            } else {
                pillarNodes.add(lat, lon)
            }
        }

        if (reverse) {
            if (mode == FetchMode.ALL || mode == FetchMode.BASE_AND_PILLAR)
                pillarNodes.add(nodeAccess, baseNode)

            pillarNodes.reverse()
        } else if (mode == FetchMode.ALL || mode == FetchMode.PILLAR_AND_ADJ)
            pillarNodes.add(nodeAccess, adjNode)

        return pillarNodes
    }

    private fun nextGeoRef(bytes: Int): Long {
        val tmp = maxGeoRef
        maxGeoRef += bytes
        return tmp
    }

    val isClosed: Boolean
        get() = store.isClosed

    val directory: Directory
        get() = dir

    class Builder(private val bytesForFlags: Int) {
        private var directory: Directory = GHDirectory("", DAType.RAM)
        private var withElevation = false
        private var withTurnCosts = false
        private var bytes: Long = 100

        constructor(em: EncodingManager) : this(em.bytesForFlags) {
            withTurnCosts(em.needsTurnCostsSupport())
        }

        // todo: maybe rename later, but for now this makes it easier to replace GraphBuilder
        fun setDir(directory: Directory): Builder {
            this.directory = directory
            return this
        }

        // todo: maybe rename later, but for now this makes it easier to replace GraphBuilder
        fun set3D(withElevation: Boolean): Builder {
            this.withElevation = withElevation
            return this
        }

        // todo: maybe rename later, but for now this makes it easier to replace GraphBuilder
        fun withTurnCosts(withTurnCosts: Boolean): Builder {
            this.withTurnCosts = withTurnCosts
            return this
        }

        fun setBytes(bytes: Long): Builder {
            this.bytes = bytes
            return this
        }

        fun build(): BaseGraph = BaseGraph(directory, withElevation, withTurnCosts, bytesForFlags)

        fun create(): BaseGraph {
            val baseGraph = build()
            baseGraph.create(bytes)
            return baseGraph
        }
    }

    internal open class EdgeIteratorImpl(baseGraph: BaseGraph, filter: EdgeFilter?) : EdgeIteratorStateImpl(baseGraph), EdgeExplorer, EdgeIterator {
        @JvmField
        val filter: EdgeFilter

        @JvmField
        var nextEdgeId = 0

        init {
            if (filter == null)
                throw IllegalArgumentException("Instead null filter use EdgeFilter.ALL_EDGES")
            this.filter = filter
        }

        override fun setBaseNode(baseNode: Int): EdgeIterator {
            edgeId = store.getEdgeRef(store.toNodePointer(baseNode))
            nextEdgeId = edgeId
            this._baseNode = baseNode
            return this
        }

        final override fun next(): Boolean {
            while (EdgeIterator.Edge.isValid(nextEdgeId)) {
                goToNext()
                if (filter.accept(this))
                    return true
            }
            return false
        }

        fun goToNext() {
            edgePointer = store.toEdgePointer(nextEdgeId)
            edgeId = nextEdgeId
            val nodeA = store.getNodeA(edgePointer)
            val baseNodeIsNodeA = _baseNode == nodeA
            _adjNode = if (baseNodeIsNodeA) store.getNodeB(edgePointer) else nodeA
            reverse = !baseNodeIsNodeA

            // position to next edge
            nextEdgeId = if (baseNodeIsNodeA) store.getLinkA(edgePointer) else store.getLinkB(edgePointer)
            assert(nextEdgeId != edgeId) {
                ("endless loop detected for base node: " + _baseNode + ", adj node: " + _adjNode
                        + ", edge pointer: " + edgePointer + ", edge: " + edgeId)
            }
        }

        override fun detach(reverse: Boolean): EdgeIteratorState {
            if (edgeId == nextEdgeId)
                throw IllegalStateException("call next before detaching (edgeId:$edgeId vs. next $nextEdgeId)")
            return super.detach(reverse)
        }
    }

    /**
     * Include all edges of this storage in the iterator.
     */
    internal class AllEdgeIterator(baseGraph: BaseGraph, from: Int, private val toExclusive: Int) : EdgeIteratorStateImpl(baseGraph), AllEdgesIterator {

        constructor(baseGraph: BaseGraph) : this(baseGraph, 0, baseGraph.store.getEdges())

        init {
            this.edgeId = from - 1
        }

        override fun length(): Int = toExclusive

        override fun next(): Boolean {
            edgeId++
            if (edgeId >= toExclusive)
                return false
            edgePointer = store.toEdgePointer(edgeId)
            _baseNode = store.getNodeA(edgePointer)
            _adjNode = store.getNodeB(edgePointer)
            reverse = false
            return true
        }

        override fun detach(reverse: Boolean): EdgeIteratorState {
            if (edgePointer < 0)
                throw IllegalStateException("call next before detaching")

            val iter = AllEdgeIterator(baseGraph)
            iter.edgeId = edgeId
            iter.edgePointer = edgePointer
            if (reverse) {
                iter.reverse = !this.reverse
                iter._baseNode = _adjNode
                iter._adjNode = _baseNode
            } else {
                iter.reverse = this.reverse
                iter._baseNode = _baseNode
                iter._adjNode = _adjNode
            }
            return iter
        }
    }

    internal open class EdgeIteratorStateImpl(@JvmField val baseGraph: BaseGraph) : EdgeIteratorState {
        @JvmField
        val store: BaseGraphNodesAndEdges = baseGraph.store

        @JvmField
        var edgePointer: Long = -1

        @JvmField
        var _baseNode = 0

        @JvmField
        var _adjNode = 0

        // we need reverse if detach is called
        @JvmField
        var reverse = false

        @JvmField
        var edgeId = -1

        private val edgeIntAccess: EdgeIntAccess = baseGraph.edgeAccess

        /**
         * @return false if the edge has not a node equal to expectedAdjNode
         */
        fun init(edgeId: Int, expectedAdjNode: Int): Boolean {
            if (edgeId < 0 || edgeId >= store.getEdges())
                throw IllegalArgumentException("edge: " + edgeId + " out of bounds: [0," + store.getEdges() + "[")
            this.edgeId = edgeId
            edgePointer = store.toEdgePointer(edgeId)
            _baseNode = store.getNodeA(edgePointer)
            _adjNode = store.getNodeB(edgePointer)

            if (expectedAdjNode == _adjNode || expectedAdjNode == Int.MIN_VALUE) {
                reverse = false
                return true
            } else if (expectedAdjNode == _baseNode) {
                reverse = true
                _baseNode = _adjNode
                _adjNode = expectedAdjNode
                return true
            }
            return false
        }

        /**
         * Similar to [init], but here we retrieve the edge in a certain direction
         * directly using an edge key.
         */
        fun init(edgeKey: Int) {
            if (edgeKey < 0)
                throw IllegalArgumentException("edge keys must not be negative, given: $edgeKey")
            this.edgeId = GHUtility.getEdgeFromEdgeKey(edgeKey)
            edgePointer = store.toEdgePointer(edgeId)
            _baseNode = store.getNodeA(edgePointer)
            _adjNode = store.getNodeB(edgePointer)

            if (edgeKey % 2 == 0) {
                reverse = false
            } else {
                reverse = true
                val tmp = _baseNode
                _baseNode = _adjNode
                _adjNode = tmp
            }
        }

        final override val baseNode: Int
            get() = _baseNode

        final override val adjNode: Int
            get() = _adjNode

        override val distance: Double
            // never return infinity even if dist_mm is INT MAX, see #435
            get() = distance_mm / 1000.0

        override fun setDistance(dist: Double): EdgeIteratorState {
            if (dist < 0)
                throw IllegalArgumentException("distances must be non-negative, got: $dist")
            // distances above ~2147km are capped
            var d = dist
            if (d > MAX_DIST_METERS)
                d = MAX_DIST_METERS
            // distances below 0.5mm are rounded down to zero
            val dist_mm = Math.round(d * 1000)
            setDistance_mm(dist_mm)
            return this
        }

        /**
         * Returns the distance in millimeters
         */
        override val distance_mm: Long
            get() = store.getDist_mm(edgePointer)

        override fun setDistance_mm(distance_mm: Long): EdgeIteratorState {
            if (distance_mm < 0)
                throw IllegalArgumentException("distances must be non-negative, got: $distance_mm")
            var d = distance_mm
            if (d > BaseGraphNodesAndEdges.MAX_DIST_MM)
                d = BaseGraphNodesAndEdges.MAX_DIST_MM
            store.setDist_mm(edgePointer, d)
            return this
        }

        override val flags: IntsRef
            get() {
                val edgeFlags = store.createEdgeFlags()
                store.readFlags(edgePointer, edgeFlags)
                return edgeFlags
            }

        final override fun setFlags(edgeFlags: IntsRef): EdgeIteratorState {
            assert(edgeId < store.getEdges()) { "must be edge but was shortcut: " + edgeId + " >= " + store.getEdges() + ". Use setFlagsAndWeight" }
            store.writeFlags(edgePointer, edgeFlags)
            return this
        }

        override fun get(property: BooleanEncodedValue): Boolean =
            property.getBool(reverse, edgeId, edgeIntAccess)

        override fun set(property: BooleanEncodedValue, value: Boolean): EdgeIteratorState {
            property.setBool(reverse, edgeId, edgeIntAccess, value)
            return this
        }

        override fun getReverse(property: BooleanEncodedValue): Boolean =
            property.getBool(!reverse, edgeId, edgeIntAccess)

        override fun setReverse(property: BooleanEncodedValue, value: Boolean): EdgeIteratorState {
            property.setBool(!reverse, edgeId, edgeIntAccess, value)
            return this
        }

        override fun set(property: BooleanEncodedValue, fwd: Boolean, bwd: Boolean): EdgeIteratorState {
            if (!property.isStoreTwoDirections)
                throw IllegalArgumentException("EncodedValue " + property.name + " supports only one direction")
            property.setBool(reverse, edgeId, edgeIntAccess, fwd)
            property.setBool(!reverse, edgeId, edgeIntAccess, bwd)
            return this
        }

        override fun get(property: IntEncodedValue): Int =
            property.getInt(reverse, edgeId, edgeIntAccess)

        override fun set(property: IntEncodedValue, value: Int): EdgeIteratorState {
            property.setInt(reverse, edgeId, edgeIntAccess, value)
            return this
        }

        override fun getReverse(property: IntEncodedValue): Int =
            property.getInt(!reverse, edgeId, edgeIntAccess)

        override fun setReverse(property: IntEncodedValue, value: Int): EdgeIteratorState {
            property.setInt(!reverse, edgeId, edgeIntAccess, value)
            return this
        }

        override fun set(property: IntEncodedValue, fwd: Int, bwd: Int): EdgeIteratorState {
            if (!property.isStoreTwoDirections)
                throw IllegalArgumentException("EncodedValue " + property.name + " supports only one direction")
            property.setInt(reverse, edgeId, edgeIntAccess, fwd)
            property.setInt(!reverse, edgeId, edgeIntAccess, bwd)
            return this
        }

        override fun get(property: DecimalEncodedValue): Double =
            property.getDecimal(reverse, edgeId, edgeIntAccess)

        override fun set(property: DecimalEncodedValue, value: Double): EdgeIteratorState {
            property.setDecimal(reverse, edgeId, edgeIntAccess, value)
            return this
        }

        override fun getReverse(property: DecimalEncodedValue): Double =
            property.getDecimal(!reverse, edgeId, edgeIntAccess)

        override fun setReverse(property: DecimalEncodedValue, value: Double): EdgeIteratorState {
            property.setDecimal(!reverse, edgeId, edgeIntAccess, value)
            return this
        }

        override fun set(property: DecimalEncodedValue, fwd: Double, bwd: Double): EdgeIteratorState {
            if (!property.isStoreTwoDirections)
                throw IllegalArgumentException("EncodedValue " + property.name + " supports only one direction")
            property.setDecimal(reverse, edgeId, edgeIntAccess, fwd)
            property.setDecimal(!reverse, edgeId, edgeIntAccess, bwd)
            return this
        }

        override fun <T : Enum<*>> get(property: EnumEncodedValue<T>): T =
            property.getEnum(reverse, edgeId, edgeIntAccess)

        override fun <T : Enum<*>> set(property: EnumEncodedValue<T>, value: T): EdgeIteratorState {
            property.setEnum(reverse, edgeId, edgeIntAccess, value)
            return this
        }

        override fun <T : Enum<*>> getReverse(property: EnumEncodedValue<T>): T =
            property.getEnum(!reverse, edgeId, edgeIntAccess)

        override fun <T : Enum<*>> setReverse(property: EnumEncodedValue<T>, value: T): EdgeIteratorState {
            property.setEnum(!reverse, edgeId, edgeIntAccess, value)
            return this
        }

        override fun <T : Enum<*>> set(property: EnumEncodedValue<T>, fwd: T, bwd: T): EdgeIteratorState {
            if (!property.isStoreTwoDirections)
                throw IllegalArgumentException("EncodedValue " + property.name + " supports only one direction")
            property.setEnum(reverse, edgeId, edgeIntAccess, fwd)
            property.setEnum(!reverse, edgeId, edgeIntAccess, bwd)
            return this
        }

        override fun get(property: StringEncodedValue): String? =
            property.getString(reverse, edgeId, edgeIntAccess)

        override fun set(property: StringEncodedValue, value: String?): EdgeIteratorState {
            property.setString(reverse, edgeId, edgeIntAccess, value)
            return this
        }

        override fun getReverse(property: StringEncodedValue): String? =
            property.getString(!reverse, edgeId, edgeIntAccess)

        override fun setReverse(property: StringEncodedValue, value: String?): EdgeIteratorState {
            property.setString(!reverse, edgeId, edgeIntAccess, value)
            return this
        }

        override fun set(property: StringEncodedValue, fwd: String?, bwd: String?): EdgeIteratorState {
            if (!property.isStoreTwoDirections)
                throw IllegalArgumentException("EncodedValue " + property.name + " supports only one direction")
            property.setString(reverse, edgeId, edgeIntAccess, fwd)
            property.setString(!reverse, edgeId, edgeIntAccess, bwd)
            return this
        }

        final override fun copyPropertiesFrom(e: EdgeIteratorState): EdgeIteratorState =
            baseGraph.copyProperties(e, this)

        override fun setWayGeometry(list: PointList?): EdgeIteratorState {
            baseGraph.setWayGeometry_(list, edgePointer, reverse)
            return this
        }

        override fun fetchWayGeometry(mode: FetchMode): PointList =
            baseGraph.fetchWayGeometry_(edgePointer, reverse, mode, baseNode, adjNode)

        override val edge: Int
            get() = edgeId

        override val edgeKey: Int
            get() = GHUtility.createEdgeKey(edgeId, reverse)

        override val reverseEdgeKey: Int
            get() = GHUtility.reverseEdgeKey(edgeKey)

        override fun setKeyValues(map: Map<String, KVStorage.KValue>?): EdgeIteratorState {
            val pointer = baseGraph.edgeKVStorage.add(map)
            // Shift right to use 4x more address space (pointers are 4-byte aligned)
            val shiftedPointer = pointer shr KVStorage.ALIGNMENT_SHIFT
            if (shiftedPointer > MAX_UNSIGNED_INT)
                throw IllegalStateException("Too many key value pairs are stored, currently limited to " + (MAX_UNSIGNED_INT shl KVStorage.ALIGNMENT_SHIFT) + " was " + pointer)
            store.setKeyValuesRef(edgePointer, BitUtil.toSignedInt(shiftedPointer))
            return this
        }

        override val keyValues: Map<String, KVStorage.KValue>
            get() {
                val shiftedRef = Integer.toUnsignedLong(store.getKeyValuesRef(edgePointer))
                // Shift left to restore the actual byte offset
                val kvEntryRef = shiftedRef shl KVStorage.ALIGNMENT_SHIFT
                return baseGraph.edgeKVStorage.getAll(kvEntryRef)
            }

        override fun getValue(key: String): Any? {
            val shiftedRef = Integer.toUnsignedLong(store.getKeyValuesRef(edgePointer))
            // Shift left to restore the actual byte offset
            val kvEntryRef = shiftedRef shl KVStorage.ALIGNMENT_SHIFT
            return baseGraph.edgeKVStorage.get(kvEntryRef, key, reverse)
        }

        override val name: String
            get() {
                val name = getValue(STREET_NAME) as String?
                // preserve backward compatibility (returns empty string if name tag missing)
                return name ?: ""
            }

        override fun detach(reverse: Boolean): EdgeIteratorState {
            if (!EdgeIterator.Edge.isValid(edgeId))
                throw IllegalStateException("call setEdgeId before detaching (edgeId:$edgeId)")
            val edge = EdgeIteratorStateImpl(baseGraph)
            val valid = edge.init(edgeId, if (reverse) _baseNode else _adjNode)
            assert(valid)
            if (reverse) {
                // for #162
                edge.reverse = !this.reverse
            }
            return edge
        }

        override val isVirtual: Boolean
            get() = false

        final override fun toString(): String = "$edge $baseNode-$adjNode"
    }

    // stores a byte array plus the length of actually used bytes (the array may be over-allocated)
    private class EncodedBytes(@JvmField val bytes: ByteArray, @JvmField val length: Int)

    companion object {
        /**
         * Maximum distance per edge in meters (~2147 km).
         */
        const val MAX_DIST_METERS: Double = BaseGraphNodesAndEdges.MAX_DIST_MM / 1000.0

        internal const val MAX_UNSIGNED_INT = 0xFFFF_FFFFL
        private const val MAX_PILLAR_NODES = 65535

        // Worst-case bytes per varint-encoded signed int.
        private const val MAX_VARINT_BYTES = 5

        private fun writeZigZagVarInt(bytes: ByteArray, offset: Int, value: Int): Int {
            var off = offset
            var zz = (value shl 1) xor (value shr 31)
            while ((zz and 0x7F.inv()) != 0) {
                bytes[off++] = ((zz and 0x7F) or 0x80).toByte()
                zz = zz ushr 7
            }
            bytes[off++] = zz.toByte()
            return off
        }

        // Returns the decoded signed int; advances pos[0] past the varint bytes.
        private fun readZigZagVarInt(bytes: ByteArray, pos: IntArray): Int {
            var p = pos[0]
            var raw = 0
            var shift = 0
            var b: Byte
            do {
                b = bytes[p++]
                raw = raw or ((b.toInt() and 0x7F) shl shift)
                shift += 7
            } while ((b.toInt() and 0x80) != 0)
            pos[0] = p
            return (raw ushr 1) xor -(raw and 1)
        }

        private fun getPointListLength(pillarNodes: Int, mode: FetchMode): Int = when (mode) {
            FetchMode.TOWER_ONLY -> 2
            FetchMode.PILLAR_ONLY -> pillarNodes
            FetchMode.BASE_AND_PILLAR, FetchMode.PILLAR_AND_ADJ -> pillarNodes + 1
            FetchMode.ALL -> pillarNodes + 2
        }
    }
}
