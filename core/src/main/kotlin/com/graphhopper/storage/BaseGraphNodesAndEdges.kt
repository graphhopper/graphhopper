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

import com.carrotsearch.hppc.BitSet
import com.graphhopper.routing.ev.EdgeIntAccess
import com.graphhopper.util.BitUtil
import com.graphhopper.util.Constants
import com.graphhopper.util.EdgeIterator
import com.graphhopper.util.EdgeIterator.Companion.NO_EDGE
import com.graphhopper.util.GHUtility
import com.graphhopper.util.Helper
import com.graphhopper.util.Helper.nf
import com.graphhopper.util.shapes.BBox
import java.util.Locale
import java.util.function.IntUnaryOperator

/**
 * Underlying storage for nodes and edges of [BaseGraph]. Nodes and edges are stored using two [DataAccess]
 * instances. Nodes and edges are simply stored sequentially, see the memory layout in the constructor.
 */
internal class BaseGraphNodesAndEdges(
    dir: Directory,
    private val withElevation: Boolean,
    private val withTurnCosts: Boolean,
    val bytesForFlags: Int
) : EdgeIntAccess {

    // nodes
    private val nodes: DataAccess = dir.create("nodes", dir.getDefaultType("nodes", true))
    private val N_EDGE_REF: Int
    private val N_LAT: Int
    private val N_LON: Int
    private val N_ELE: Int
    private val N_TC: Int
    private var nodeEntryBytes: Int
    private var nodeCount = 0

    // edges
    private val edges: DataAccess = dir.create("edges", dir.getDefaultType("edges", false))
    private val E_NODEA: Int
    private val E_NODEB: Int
    private val E_LINKA: Int
    private val E_LINKB: Int
    private val E_DIST: Int
    private val E_KV: Int
    private val E_FLAGS: Int
    private val E_GEO: Int
    private var edgeEntryBytes: Int
    private var edgeCount = 0

    // we do not write the bounding box directly to storage, but rather to this bbox object. we only write to storage
    // when flushing. why? just because we did it like this in the past, and otherwise we run into rounding errors,
    // because of: #2393
    @JvmField
    val bounds: BBox = BBox.createInverse(withElevation)
    var frozen = false

    init {
        // memory layout for nodes
        N_EDGE_REF = 0
        N_LAT = 4
        N_LON = 8
        N_ELE = N_LON + (if (withElevation) 4 else 0)
        N_TC = N_ELE + (if (withTurnCosts) 4 else 0)
        nodeEntryBytes = N_TC + 4

        // memory layout for edges
        E_NODEA = 0
        E_NODEB = 4
        E_LINKA = 8
        E_LINKB = 12
        E_DIST = 16
        E_KV = 20
        E_FLAGS = 24
        E_GEO = E_FLAGS + bytesForFlags
        edgeEntryBytes = E_GEO + 5
    }

    fun create(initSize: Long) {
        nodes.create(initSize)
        edges.create(initSize)
    }

    fun loadExisting(): Boolean {
        if (!nodes.loadExisting() || !edges.loadExisting())
            return false

        // now load some properties from stored data
        val nodesVersion = nodes.getHeader(0 * 4)
        GHUtility.checkDAVersion("nodes", Constants.VERSION_NODE, nodesVersion)
        nodeEntryBytes = nodes.getHeader(1 * 4)
        nodeCount = nodes.getHeader(2 * 4)
        bounds.minLon = Helper.intToDegree(nodes.getHeader(3 * 4))
        bounds.maxLon = Helper.intToDegree(nodes.getHeader(4 * 4))
        bounds.minLat = Helper.intToDegree(nodes.getHeader(5 * 4))
        bounds.maxLat = Helper.intToDegree(nodes.getHeader(6 * 4))
        val hasElevation = nodes.getHeader(7 * 4) == 1
        if (hasElevation != withElevation)
            // :( we should load data from disk to create objects, not the other way around!
            throw IllegalStateException("Configured dimension elevation=" + withElevation + " is not equal "
                    + "to dimension of loaded graph elevation =" + hasElevation)
        if (withElevation) {
            bounds.minEle = Helper.uIntToEle(nodes.getHeader(8 * 4))
            bounds.maxEle = Helper.uIntToEle(nodes.getHeader(9 * 4))
        }
        frozen = nodes.getHeader(10 * 4) == 1

        val edgesVersion = edges.getHeader(0 * 4)
        GHUtility.checkDAVersion("edges", Constants.VERSION_EDGE, edgesVersion)
        edgeEntryBytes = edges.getHeader(1 * 4)
        edgeCount = edges.getHeader(2 * 4)
        return true
    }

    fun flush() {
        nodes.setHeader(0 * 4, Constants.VERSION_NODE)
        nodes.setHeader(1 * 4, nodeEntryBytes)
        nodes.setHeader(2 * 4, nodeCount)
        nodes.setHeader(3 * 4, Helper.degreeToInt(bounds.minLon))
        nodes.setHeader(4 * 4, Helper.degreeToInt(bounds.maxLon))
        nodes.setHeader(5 * 4, Helper.degreeToInt(bounds.minLat))
        nodes.setHeader(6 * 4, Helper.degreeToInt(bounds.maxLat))
        nodes.setHeader(7 * 4, if (withElevation) 1 else 0)
        if (withElevation) {
            nodes.setHeader(8 * 4, Helper.eleToUInt(bounds.minEle))
            nodes.setHeader(9 * 4, Helper.eleToUInt(bounds.maxEle))
        }
        nodes.setHeader(10 * 4, if (frozen) 1 else 0)

        edges.setHeader(0 * 4, Constants.VERSION_EDGE)
        edges.setHeader(1 * 4, edgeEntryBytes)
        edges.setHeader(2 * 4, edgeCount)

        edges.flush()
        nodes.flush()
    }

    fun close() {
        edges.close()
        nodes.close()
    }

    fun getNodes(): Int = nodeCount

    fun getEdges(): Int = edgeCount

    fun createEdgeFlags(): IntsRef = IntsRef(Math.ceil(bytesForFlags.toDouble() / 4).toInt())

    fun withElevation(): Boolean = withElevation

    fun withTurnCosts(): Boolean = withTurnCosts

    fun getBounds(): BBox = bounds

    fun getCapacity(): Long = nodes.capacity + edges.capacity

    val isClosed: Boolean
        get() {
            assert(nodes.isClosed == edges.isClosed)
            return nodes.isClosed
        }

    fun edge(nodeA: Int, nodeB: Int): Int {
        if (edgeCount == Int.MAX_VALUE)
            throw IllegalStateException("Maximum edge count exceeded: $edgeCount")
        if (nodeA == nodeB)
            throw IllegalArgumentException("Loop edges are not supported, got: $nodeA - $nodeB")
        ensureNodeCapacity(Math.max(nodeA, nodeB))
        val edge = edgeCount
        val edgePointer = edgeCount.toLong() * edgeEntryBytes
        edgeCount++
        edges.ensureCapacity(edgeCount.toLong() * edgeEntryBytes)

        setNodeA(edgePointer, nodeA)
        setNodeB(edgePointer, nodeB)
        // we keep a linked list of edges at each node. here we prepend the new edge at the already existing linked
        // list of edges.
        val nodePointerA = toNodePointer(nodeA)
        val edgeRefA = getEdgeRef(nodePointerA)
        setLinkA(edgePointer, if (EdgeIterator.Edge.isValid(edgeRefA)) edgeRefA else NO_EDGE)
        setEdgeRef(nodePointerA, edge)

        if (nodeA != nodeB) {
            val nodePointerB = toNodePointer(nodeB)
            val edgeRefB = getEdgeRef(nodePointerB)
            setLinkB(edgePointer, if (EdgeIterator.Edge.isValid(edgeRefB)) edgeRefB else NO_EDGE)
            setEdgeRef(nodePointerB, edge)
        }
        return edge
    }

    fun sortEdges(getNewEdgeForOldEdge: IntUnaryOperator) {
        val visited = BitSet(getEdges().toLong())
        for (edge in 0 until getEdges()) {
            if (visited.get(edge.toLong())) continue
            var curr = edge

            val pointer = toEdgePointer(curr)
            var nodeA = getNodeA(pointer)
            var nodeB = getNodeB(pointer)
            var linkA = getLinkA(pointer)
            var linkB = getLinkB(pointer)
            var dist = edges.getInt(pointer + E_DIST)
            var kv = getKeyValuesRef(pointer)
            var flags = createEdgeFlags()
            readFlags(pointer, flags)
            var geo = getGeoRef(pointer)

            do {
                visited.set(curr.toLong())

                val newEdge = getNewEdgeForOldEdge.applyAsInt(curr)
                val newPointer = toEdgePointer(newEdge)
                val tmpNodeA = getNodeA(newPointer)
                val tmpNodeB = getNodeB(newPointer)
                val tmpLinkA = getLinkA(newPointer)
                val tmpLinkB = getLinkB(newPointer)
                val tmpDist = edges.getInt(newPointer + E_DIST)
                val tmpKV = getKeyValuesRef(newPointer)
                val tmpFlags = createEdgeFlags()
                readFlags(newPointer, tmpFlags)
                val tmpGeo = getGeoRef(newPointer)

                setNodeA(newPointer, nodeA)
                setNodeB(newPointer, nodeB)
                setLinkA(newPointer, if (linkA == -1) -1 else getNewEdgeForOldEdge.applyAsInt(linkA))
                setLinkB(newPointer, if (linkB == -1) -1 else getNewEdgeForOldEdge.applyAsInt(linkB))
                edges.setInt(newPointer + E_DIST, dist)
                setKeyValuesRef(newPointer, kv)
                writeFlags(newPointer, flags)
                setGeoRef(newPointer, geo)

                nodeA = tmpNodeA
                nodeB = tmpNodeB
                linkA = tmpLinkA
                linkB = tmpLinkB
                dist = tmpDist
                kv = tmpKV
                flags = tmpFlags
                geo = tmpGeo

                curr = newEdge
            } while (curr != edge)
        }

        // update edge references
        for (node in 0 until getNodes()) {
            val pointer = toNodePointer(node)
            setEdgeRef(pointer, getNewEdgeForOldEdge.applyAsInt(getEdgeRef(pointer)))
        }
    }

    fun relabelNodes(getNewNodeForOldNode: IntUnaryOperator) {
        for (edge in 0 until getEdges()) {
            val pointer = toEdgePointer(edge)
            setNodeA(pointer, getNewNodeForOldNode.applyAsInt(getNodeA(pointer)))
            setNodeB(pointer, getNewNodeForOldNode.applyAsInt(getNodeB(pointer)))
        }
        val visited = BitSet(getNodes().toLong())
        for (node in 0 until getNodes()) {
            if (visited.get(node.toLong())) continue

            var curr = node
            val pointer = toNodePointer(node)
            var edgeRef = getEdgeRef(pointer)
            var lat = getLat(pointer)
            var lon = getLon(pointer)
            var ele = if (withElevation()) getEle(pointer) else Double.NaN
            var tc = if (withTurnCosts()) getTurnCostRef(pointer) else -1

            do {
                visited.set(curr.toLong())
                val newNode = getNewNodeForOldNode.applyAsInt(curr)
                val newPointer = toNodePointer(newNode)
                val tmpEdgeRef = getEdgeRef(newPointer)
                val tmpLat = getLat(newPointer)
                val tmpLon = getLon(newPointer)
                val tmpEle = if (withElevation()) getEle(newPointer) else Double.NaN
                val tmpTC = if (withTurnCosts()) getTurnCostRef(newPointer) else -1

                setEdgeRef(newPointer, edgeRef)
                setLat(newPointer, lat)
                setLon(newPointer, lon)
                if (withElevation())
                    setEle(newPointer, ele)
                if (withTurnCosts())
                    setTurnCostRef(newPointer, tc)

                edgeRef = tmpEdgeRef
                lat = tmpLat
                lon = tmpLon
                ele = tmpEle
                tc = tmpTC

                curr = newNode
            } while (curr != node)
        }
    }

    fun ensureNodeCapacity(node: Int) {
        if (node < nodeCount)
            return

        val oldNodes = nodeCount
        nodeCount = node + 1
        nodes.ensureCapacity(nodeCount.toLong() * nodeEntryBytes)
        for (n in oldNodes until nodeCount) {
            setEdgeRef(toNodePointer(n), NO_EDGE)
            if (withTurnCosts)
                setTurnCostRef(toNodePointer(n), TurnCostStorage.NO_TURN_ENTRY)
        }
    }

    fun toNodePointer(node: Int): Long {
        if (node < 0 || node >= nodeCount)
            throw IllegalArgumentException("node: $node out of bounds [0,$nodeCount[")
        return node.toLong() * nodeEntryBytes
    }

    fun toEdgePointer(edge: Int): Long {
        if (edge < 0 || edge >= edgeCount)
            throw IllegalArgumentException("edge: $edge out of bounds [0,$edgeCount[")
        return edge.toLong() * edgeEntryBytes
    }

    fun readFlags(edgePointer: Long, edgeFlags: IntsRef) {
        val size = edgeFlags.ints.size
        for (i in 0 until size)
            edgeFlags.ints[i] = getFlagInt(edgePointer, i * 4)
    }

    fun writeFlags(edgePointer: Long, edgeFlags: IntsRef) {
        val size = edgeFlags.ints.size
        for (i in 0 until size)
            setFlagInt(edgePointer, i * 4, edgeFlags.ints[i])
    }

    private fun getFlagInt(edgePointer: Long, byteOffset: Int): Int {
        if (byteOffset >= bytesForFlags)
            throw IllegalArgumentException("too large byteOffset $byteOffset vs $bytesForFlags")
        val pointer = edgePointer + byteOffset
        if (byteOffset + 3 == bytesForFlags) {
            return ((edges.getShort(pointer + E_FLAGS).toInt() shl 8) and 0x00FF_FFFF) or (edges.getByte(pointer + E_FLAGS + 2).toInt() and 0xFF)
        } else if (byteOffset + 2 == bytesForFlags) {
            return edges.getShort(pointer + E_FLAGS).toInt() and 0xFFFF
        } else if (byteOffset + 1 == bytesForFlags) {
            return edges.getByte(pointer + E_FLAGS).toInt() and 0xFF
        }
        return edges.getInt(pointer + E_FLAGS)
    }

    private fun setFlagInt(edgePointer: Long, byteOffset: Int, value: Int) {
        if (byteOffset >= bytesForFlags)
            throw IllegalArgumentException("too large byteOffset $byteOffset vs $bytesForFlags")
        val pointer = edgePointer + byteOffset
        if (byteOffset + 3 == bytesForFlags) {
            if ((value and 0xFF00_0000.toInt()) != 0)
                throw IllegalArgumentException("value at byteOffset $byteOffset must not have the highest byte set but was $value")
            edges.setShort(pointer + E_FLAGS, (value shr 8).toShort())
            edges.setByte(pointer + E_FLAGS + 2, value.toByte())
        } else if (byteOffset + 2 == bytesForFlags) {
            if ((value and 0xFFFF_0000.toInt()) != 0)
                throw IllegalArgumentException("value at byteOffset $byteOffset must not have the 2 highest bytes set but was $value")
            edges.setShort(pointer + E_FLAGS, value.toShort())
        } else if (byteOffset + 1 == bytesForFlags) {
            if ((value and 0xFFFF_FF00.toInt()) != 0)
                throw IllegalArgumentException("value at byteOffset $byteOffset must not have the 3 highest bytes set but was $value")
            edges.setByte(pointer + E_FLAGS, value.toByte())
        } else {
            edges.setInt(pointer + E_FLAGS, value)
        }
    }

    override fun getInt(edgeId: Int, index: Int): Int = getFlagInt(toEdgePointer(edgeId), index * 4)

    override fun setInt(edgeId: Int, index: Int, value: Int) {
        setFlagInt(toEdgePointer(edgeId), index * 4, value)
    }

    fun setNodeA(edgePointer: Long, nodeA: Int) {
        edges.setInt(edgePointer + E_NODEA, nodeA)
    }

    fun setNodeB(edgePointer: Long, nodeB: Int) {
        edges.setInt(edgePointer + E_NODEB, nodeB)
    }

    fun setLinkA(edgePointer: Long, linkA: Int) {
        edges.setInt(edgePointer + E_LINKA, linkA)
    }

    fun setLinkB(edgePointer: Long, linkB: Int) {
        edges.setInt(edgePointer + E_LINKB, linkB)
    }

    fun getDist_mm(pointer: Long): Long = edges.getInt(pointer + E_DIST).toLong()

    fun setDist_mm(pointer: Long, distance_mm: Long) {
        if (distance_mm < 0)
            throw IllegalArgumentException("distances must be non-negative, got: $distance_mm")
        if (distance_mm > MAX_DIST_MM)
            throw IllegalArgumentException("distances must not exceed " + MAX_DIST_MM + "mm, got: " + distance_mm)
        edges.setInt(pointer + E_DIST, distance_mm.toInt())
    }

    fun setGeoRef(edgePointer: Long, geoRef: Long) {
        val highest25Bits = (geoRef ushr 39).toInt()
        // Only two cases are allowed for highest bits. If geoRef is positive then all high bits are 0. If negative then all are 1.
        if (highest25Bits != 0 && highest25Bits != 0x1_FF_FFFF)
            throw IllegalArgumentException("geoRef is too " + (if (geoRef > 0) "large " else "small ") + geoRef + ", " + java.lang.Long.toBinaryString(geoRef))

        edges.setInt(edgePointer + E_GEO, geoRef.toInt())
        edges.setByte(edgePointer + E_GEO + 4, (geoRef shr 32).toByte())
    }

    fun setKeyValuesRef(edgePointer: Long, nameRef: Int) {
        edges.setInt(edgePointer + E_KV, nameRef)
    }

    fun getNodeA(edgePointer: Long): Int = edges.getInt(edgePointer + E_NODEA)

    fun getNodeB(edgePointer: Long): Int = edges.getInt(edgePointer + E_NODEB)

    fun getLinkA(edgePointer: Long): Int = edges.getInt(edgePointer + E_LINKA)

    fun getLinkB(edgePointer: Long): Int = edges.getInt(edgePointer + E_LINKB)

    fun getGeoRef(edgePointer: Long): Long =
        BitUtil.LITTLE.toLong(
            edges.getInt(edgePointer + E_GEO),
            // to support negative georefs (#2985) do not mask byte with 0xFF:
            edges.getByte(edgePointer + E_GEO + 4).toInt())

    fun getKeyValuesRef(edgePointer: Long): Int = edges.getInt(edgePointer + E_KV)

    fun setEdgeRef(nodePointer: Long, edgeRef: Int) {
        nodes.setInt(nodePointer + N_EDGE_REF, edgeRef)
    }

    fun setLat(nodePointer: Long, lat: Double) {
        nodes.setInt(nodePointer + N_LAT, Helper.degreeToInt(lat))
    }

    fun setLon(nodePointer: Long, lon: Double) {
        nodes.setInt(nodePointer + N_LON, Helper.degreeToInt(lon))
    }

    fun setEle(elePointer: Long, ele: Double) {
        nodes.setInt(elePointer + N_ELE, Helper.eleToUInt(ele))
    }

    fun setTurnCostRef(nodePointer: Long, tcRef: Int) {
        nodes.setInt(nodePointer + N_TC, tcRef)
    }

    fun getEdgeRef(nodePointer: Long): Int = nodes.getInt(nodePointer + N_EDGE_REF)

    fun getLat(nodePointer: Long): Double = Helper.intToDegree(nodes.getInt(nodePointer + N_LAT))

    fun getLon(nodePointer: Long): Double = Helper.intToDegree(nodes.getInt(nodePointer + N_LON))

    fun getEle(nodePointer: Long): Double = Helper.uIntToEle(nodes.getInt(nodePointer + N_ELE))

    fun getTurnCostRef(nodePointer: Long): Int = nodes.getInt(nodePointer + N_TC)

    fun debugPrint() {
        val printMax = 100
        println("nodes:")
        val formatNodes = "%12s | %12s | %12s | %12s | %12s | %15s\n"
        System.out.format(Locale.ROOT, formatNodes, "#", "N_EDGE_REF", "N_LAT", "N_LON", "N_ELE", "N_TC")
        for (i in 0 until Math.min(nodeCount, printMax)) {
            val nodePointer = toNodePointer(i)
            System.out.format(Locale.ROOT, formatNodes, i, getEdgeRef(nodePointer), getLat(nodePointer), getLon(nodePointer), if (withElevation) getEle(nodePointer) else "", if (withTurnCosts) getTurnCostRef(nodePointer) else "-")
        }
        if (nodeCount > printMax) {
            System.out.format(Locale.ROOT, " ... %d more nodes\n", nodeCount - printMax)
        }
        println("edges:")
        val formatEdges = "%12s | %12s | %12s | %12s | %12s | %12s | %15s \n"
        System.out.format(Locale.ROOT, formatEdges, "#", "E_NODEA", "E_NODEB", "E_LINKA", "E_LINKB", "E_FLAGS", "E_DIST")
        val edgeFlags = createEdgeFlags()
        for (i in 0 until Math.min(edgeCount, printMax)) {
            val edgePointer = toEdgePointer(i)
            readFlags(edgePointer, edgeFlags)
            System.out.format(Locale.ROOT, formatEdges, i,
                    getNodeA(edgePointer),
                    getNodeB(edgePointer),
                    getLinkA(edgePointer),
                    getLinkB(edgePointer),
                    edgeFlags,
                    getDist_mm(edgePointer))
        }
        if (edgeCount > printMax) {
            System.out.printf(Locale.ROOT, " ... %d more edges", edgeCount - printMax)
        }
    }

    fun toDetailsString(): String =
        "edges: " + nf(edgeCount.toLong()) + "(" + edges.capacity / Helper.MB + "MB), " +
                "nodes: " + nf(nodeCount.toLong()) + "(" + nodes.capacity / Helper.MB + "MB), " +
                "bounds: " + bounds

    companion object {
        // Distances are stored as 4-byte signed integers representing mm -> max ~2147km
        // We could quite easily use unsigned 4-byte integers to raise the max to ~4294km,
        // but if we ever wanted to use 4 instead of 8 bytes to represent (accumulated) distances
        // downstream, we would either have to lower the limit again, deal with unsigned arithmetic
        // everywhere, or increase the precision from 1mm to, say, 10mm
        const val MAX_DIST_MM: Long = Int.MAX_VALUE.toLong()
    }
}
