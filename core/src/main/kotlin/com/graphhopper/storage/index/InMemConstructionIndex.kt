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

import com.carrotsearch.hppc.IntArrayList
import com.graphhopper.geohash.SpatialKeyAlgo
import com.graphhopper.util.DistancePlaneProjection.Companion.DIST_PLANE
import org.locationtech.jts.geom.Coordinate

class InMemConstructionIndex(indexStructureInfo: IndexStructureInfo) {

    internal interface InMemEntry {
        fun isLeaf(): Boolean
    }

    internal class InMemLeafEntry(count: Int) : IntArrayList(count), InMemEntry {

        override fun isLeaf(): Boolean = true

        override fun toString(): String = "LEAF " + /*key +*/ " " + super.toString()

        fun getResults(): IntArrayList = this
    }

    internal class InMemTreeEntry(subEntryNo: Int) : InMemEntry {
        val subEntries: Array<InMemEntry?> = arrayOfNulls(subEntryNo)

        fun getSubEntry(index: Int): InMemEntry? = subEntries[index]

        fun setSubEntry(index: Int, subEntry: InMemEntry?) {
            this.subEntries[index] = subEntry
        }

        override fun isLeaf(): Boolean = false

        override fun toString(): String = "TREE"
    }

    private val pixelGridTraversal: PixelGridTraversal = indexStructureInfo.pixelGridTraversal
    private val keyAlgo: SpatialKeyAlgo = indexStructureInfo.keyAlgo
    private val entries: IntArray = indexStructureInfo.entries
    private val shifts: ByteArray = indexStructureInfo.shifts
    internal val root: InMemTreeEntry = InMemTreeEntry(indexStructureInfo.entries[0])

    fun addToAllTilesOnLine(value: Int, lat1: Double, lon1: Double, lat2: Double, lon2: Double) {
        if (!DIST_PLANE.isCrossBoundary(lon1, lon2)) {
            // Find all the tiles on the line from (y1, x1) to (y2, y2) in tile coordinates (y, x)
            pixelGridTraversal.traverse(Coordinate(lon1, lat1), Coordinate(lon2, lat2)) { p ->
                val key = keyAlgo.encode(p.x.toInt(), p.y.toInt())
                put(key, value)
            }
        }
    }

    internal fun put(key: Long, value: Int) {
        put(key shl (64 - keyAlgo.bits), root, 0, value)
    }

    private fun put(keyPart: Long, entry: InMemEntry, depth: Int, value: Int) {
        if (entry.isLeaf()) {
            val leafEntry = entry as InMemLeafEntry
            // Avoid adding the same edge id multiple times.
            // Since each edge id is handled only once, this can only happen when
            // this method is called several times in a row with the same edge id,
            // so it is enough to check the last entry.
            // (It happens when one edge has several segments. Every segment is traversed
            // on its own, without de-duplicating the tiles that are touched.)
            if (leafEntry.isEmpty || leafEntry.get(leafEntry.size() - 1) != value) {
                leafEntry.add(value)
            }
        } else {
            val index = (keyPart ushr (64 - shifts[depth])).toInt()
            val nextKeyPart = keyPart shl shifts[depth].toInt()
            val treeEntry = entry as InMemTreeEntry
            var subentry = treeEntry.getSubEntry(index)
            val nextDepth = depth + 1
            if (subentry == null) {
                subentry = if (nextDepth == entries.size) {
                    InMemLeafEntry(4)
                } else {
                    InMemTreeEntry(entries[nextDepth])
                }
                treeEntry.setSubEntry(index, subentry)
            }
            put(nextKeyPart, subentry, nextDepth, value)
        }
    }
}
