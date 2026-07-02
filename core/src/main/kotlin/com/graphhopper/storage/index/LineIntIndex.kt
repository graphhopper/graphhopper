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

package com.graphhopper.storage.index

import com.carrotsearch.hppc.IntHashSet
import com.graphhopper.geohash.SpatialKeyAlgo
import com.graphhopper.storage.DAType
import com.graphhopper.storage.DataAccess
import com.graphhopper.storage.Directory
import com.graphhopper.util.Constants
import com.graphhopper.util.GHUtility
import com.graphhopper.util.Helper
import com.graphhopper.util.shapes.BBox
import java.util.function.IntConsumer

class LineIntIndex @JvmOverloads constructor(
    bBox: BBox,
    dir: Directory,
    name: String,
    daType: DAType = dir.getDefaultType(name, true)
) {
    private val dataAccess: DataAccess = dir.create(name, daType)
    private val bounds: BBox = bBox
    var minResolutionInMeter: Int = 300
    var size: Int = 0
        private set
    var leafs: Int = 0
        private set
    var checksum: Int = 0
    private lateinit var indexStructureInfo: IndexStructureInfo
    private lateinit var entries: IntArray
    private lateinit var shifts: ByteArray
    private var initialized = false
    private lateinit var keyAlgo: SpatialKeyAlgo

    fun loadExisting(): Boolean {
        if (initialized)
            throw IllegalStateException("Call loadExisting only once")

        if (!dataAccess.loadExisting())
            return false

        GHUtility.checkDAVersion("location_index", Constants.VERSION_LOCATION_IDX, dataAccess.getHeader(0))
        checksum = dataAccess.getHeader(1 * 4)
        minResolutionInMeter = dataAccess.getHeader(2 * 4)
        indexStructureInfo = IndexStructureInfo.create(bounds, minResolutionInMeter)
        keyAlgo = indexStructureInfo.keyAlgo
        entries = indexStructureInfo.entries
        shifts = indexStructureInfo.shifts
        initialized = true
        return true
    }

    fun store(inMem: InMemConstructionIndex) {
        indexStructureInfo = IndexStructureInfo.create(bounds, minResolutionInMeter)
        keyAlgo = indexStructureInfo.keyAlgo
        entries = indexStructureInfo.entries
        shifts = indexStructureInfo.shifts
        dataAccess.create(64 * 1024)
        try {
            store(inMem.root, START_POINTER)
        } catch (ex: Exception) {
            throw IllegalStateException("Problem while storing location index. " + Helper.getMemInfo(), ex)
        }
        initialized = true
    }

    private fun store(entry: InMemConstructionIndex.InMemEntry, intPointer: Int): Int {
        @Suppress("NAME_SHADOWING")
        var intPointer = intPointer
        val pointer = intPointer.toLong() * 4
        if (entry.isLeaf()) {
            val leaf = entry as InMemConstructionIndex.InMemLeafEntry
            val entries = leaf.getResults()
            val len = entries.size()
            if (len == 0) {
                return intPointer
            }
            size += len
            intPointer++
            leafs++
            dataAccess.ensureCapacity((intPointer + len + 1).toLong() * 4)
            if (len == 1) {
                // less disc space for single entries
                dataAccess.setInt(pointer, -entries.get(0) - 1)
            } else {
                var index = 0
                while (index < len) {
                    dataAccess.setInt(intPointer.toLong() * 4, entries.get(index))
                    index++
                    intPointer++
                }
                dataAccess.setInt(pointer, intPointer)
            }
        } else {
            val treeEntry = entry as InMemConstructionIndex.InMemTreeEntry
            val len = treeEntry.subEntries.size
            intPointer += len
            var subPointer = pointer
            for (subCounter in 0 until len) {
                val subEntry = treeEntry.subEntries[subCounter]
                if (subEntry != null) {
                    dataAccess.ensureCapacity((intPointer + 1).toLong() * 4)
                    val prevIntPointer = intPointer
                    intPointer = store(subEntry, prevIntPointer)
                    if (intPointer == prevIntPointer) {
                        dataAccess.setInt(subPointer, 0)
                    } else {
                        dataAccess.setInt(subPointer, prevIntPointer)
                    }
                }
                subPointer += 4
            }
        }
        return intPointer
    }

    private fun fillIDs(keyPart: Long, consumer: IntConsumer) {
        @Suppress("NAME_SHADOWING")
        var keyPart = keyPart
        var intPointer = START_POINTER
        for (depth in entries.indices) {
            val offset = (keyPart ushr (64 - shifts[depth])).toInt()
            val nextIntPointer = dataAccess.getInt((intPointer + offset).toLong() * 4)
            if (nextIntPointer <= 0) {
                // empty cell
                return
            }
            keyPart = keyPart shl shifts[depth].toInt()
            intPointer = nextIntPointer
        }
        val data = dataAccess.getInt(intPointer.toLong() * 4)
        if (data < 0) {
            // single data entries (less disc space)
            val edgeId = -(data + 1)
            consumer.accept(edgeId)
        } else {
            // "data" is index of last data item
            var leafIndex = intPointer + 1
            while (leafIndex < data) {
                val edgeId = dataAccess.getInt(leafIndex.toLong() * 4)
                consumer.accept(edgeId)
                leafIndex++
            }
        }
    }

    fun query(queryShape: BBox?, function: LocationIndex.Visitor) {
        query(LocationIndex.createBBoxTileFilter(queryShape), function)
    }

    fun query(tileFilter: LocationIndex.TileFilter?, function: LocationIndex.Visitor) {
        val set = IntHashSet()
        query(START_POINTER, tileFilter,
                bounds.minLat, bounds.minLon, bounds.maxLat - bounds.minLat, bounds.maxLon - bounds.minLon,
                object : LocationIndex.Visitor {
                    override fun isTileInfo(): Boolean = function.isTileInfo()

                    override fun onTile(bbox: BBox, depth: Int) {
                        function.onTile(bbox, depth)
                    }

                    override fun onEdge(edgeId: Int) {
                        if (set.add(edgeId))
                            function.onEdge(edgeId)
                    }
                }, 0)
    }

    private fun query(intPointer: Int, tileFilter: LocationIndex.TileFilter?,
                      minLat: Double, minLon: Double,
                      deltaLatPerDepth: Double, deltaLonPerDepth: Double,
                      function: LocationIndex.Visitor, depth: Int) {
        @Suppress("NAME_SHADOWING")
        var deltaLatPerDepth = deltaLatPerDepth
        @Suppress("NAME_SHADOWING")
        var deltaLonPerDepth = deltaLonPerDepth
        val pointer = intPointer.toLong() * 4
        if (depth == entries.size) {
            val nextIntPointer = dataAccess.getInt(pointer)
            if (nextIntPointer < 0) {
                // single data entries (less disc space)
                function.onEdge(-(nextIntPointer + 1))
            } else {
                val maxPointer = nextIntPointer.toLong() * 4
                // loop through every leaf entry => nextIntPointer is maxPointer
                var leafPointer = pointer + 4
                while (leafPointer < maxPointer) {
                    // we could read the whole info at once via getBytes instead of getInt
                    function.onEdge(dataAccess.getInt(leafPointer))
                    leafPointer += 4
                }
            }
            return
        }
        val max = 1 shl shifts[depth].toInt()
        val factor = if (max == 4) 2 else 4
        deltaLonPerDepth /= factor
        deltaLatPerDepth /= factor
        for (cellIndex in 0 until max) {
            val nextIntPointer = dataAccess.getInt(pointer + cellIndex * 4)
            if (nextIntPointer <= 0)
                continue
            val pixelXY = keyAlgo.decode(cellIndex.toLong())
            val tmpMinLon = minLon + deltaLonPerDepth * pixelXY[0]
            val tmpMinLat = minLat + deltaLatPerDepth * pixelXY[1]

            val bbox = if (tileFilter != null || function.isTileInfo()) BBox(tmpMinLon, tmpMinLon + deltaLonPerDepth, tmpMinLat, tmpMinLat + deltaLatPerDepth) else null
            if (function.isTileInfo())
                function.onTile(bbox!!, depth)
            if (tileFilter == null || tileFilter.acceptAll(bbox!!)) {
                // fill without a restriction!
                query(nextIntPointer, null, tmpMinLat, tmpMinLon, deltaLatPerDepth, deltaLonPerDepth, function, depth + 1)
            } else if (tileFilter.acceptPartially(bbox!!)) {
                query(nextIntPointer, tileFilter, tmpMinLat, tmpMinLon, deltaLatPerDepth, deltaLonPerDepth, function, depth + 1)
            }
        }
    }

    /**
     * This method collects edge ids from the neighborhood of a point and puts them into foundEntries.
     * <p>
     * If it is called with iteration = 0, it just looks in the tile the query point is in.
     * If it is called with iteration = 0,1,2,.., it will look in additional tiles further and further
     * from the start tile. (In a square that grows by one pixel in all four directions per iteration).
     * <p>
     * See discussion at issue #221.
     * <p>
     */
    fun findEdgeIdsInNeighborhood(queryLat: Double, queryLon: Double, iteration: Int, foundEntries: IntConsumer) {
        val x = keyAlgo.x(queryLon)
        val y = keyAlgo.y(queryLat)
        for (yreg in -iteration..iteration) {
            val subqueryY = y + yreg
            val subqueryXA = x - iteration
            val subqueryXB = x + iteration
            if (subqueryXA >= 0 && subqueryY >= 0 && subqueryXA < indexStructureInfo.parts && subqueryY < indexStructureInfo.parts) {
                val keyPart = keyAlgo.encode(subqueryXA, subqueryY) shl (64 - keyAlgo.bits)
                fillIDs(keyPart, foundEntries)
            }
            if (iteration > 0 && subqueryXB >= 0 && subqueryY >= 0 && subqueryXB < indexStructureInfo.parts && subqueryY < indexStructureInfo.parts) {
                val keyPart = keyAlgo.encode(subqueryXB, subqueryY) shl (64 - keyAlgo.bits)
                fillIDs(keyPart, foundEntries)
            }
        }

        for (xreg in -iteration + 1..iteration - 1) {
            val subqueryX = x + xreg
            val subqueryYA = y - iteration
            val subqueryYB = y + iteration
            if (subqueryX >= 0 && subqueryYA >= 0 && subqueryX < indexStructureInfo.parts && subqueryYA < indexStructureInfo.parts) {
                val keyPart = keyAlgo.encode(subqueryX, subqueryYA) shl (64 - keyAlgo.bits)
                fillIDs(keyPart, foundEntries)
            }
            if (subqueryX >= 0 && subqueryYB >= 0 && subqueryX < indexStructureInfo.parts && subqueryYB < indexStructureInfo.parts) {
                val keyPart = keyAlgo.encode(subqueryX, subqueryYB) shl (64 - keyAlgo.bits)
                fillIDs(keyPart, foundEntries)
            }
        }
    }

    fun flush() {
        dataAccess.setHeader(0, Constants.VERSION_LOCATION_IDX)
        dataAccess.setHeader(1 * 4, checksum)
        dataAccess.setHeader(2 * 4, minResolutionInMeter)

        // saving space not necessary: dataAccess.trimTo((lastPointer + 1) * 4);
        dataAccess.flush()
    }

    fun close() {
        dataAccess.close()
    }

    val isClosed: Boolean
        get() = dataAccess.isClosed

    val capacity: Long
        get() = dataAccess.capacity

    companion object {
        // do not start with 0 as a positive value means leaf and a negative means "entry with subentries"
        internal const val START_POINTER = 1
    }
}
