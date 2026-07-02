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

import com.graphhopper.routing.util.EdgeFilter
import com.graphhopper.util.shapes.BBox

/**
 * Provides a way to map real world data "lat,lon" to internal ids/indices of a memory efficient graph
 * - often just implemented as an array.
 * <p>
 * The implementations of findID needs to be thread safe!
 * <p>
 *
 * @author Peter Karich
 */
interface LocationIndex {
    /**
     * This method returns the closest Snap for the specified location (lat, lon) and only if
     * the filter accepts the edge as valid candidate (e.g. filtering away car-only results for bike
     * search)
     * <p>
     *
     * @param edgeFilter if a graph supports multiple vehicles we have to make sure that the entry
     *                   node into the graph is accessible from a selected vehicle. E.g. if you have a FOOT-query do:
     *                   <pre>AccessFilter.allEdges(footFlagEncoder);</pre>
     * @return An object containing the closest node and edge for the specified location. The node id
     * has at least one edge which is accepted by the specified edgeFilter. If nothing is found
     * the method Snap.isValid will return false.
     */
    fun findClosest(lat: Double, lon: Double, edgeFilter: EdgeFilter): Snap

    /**
     * This method explores the LocationIndex with the specified Visitor. It visits only the stored edges (and only once)
     * and limited by the queryBBox. Also (a few) more edges slightly outside of queryBBox could be
     * returned that you can avoid via doing an explicit BBox check of the coordinates.
     */
    fun query(queryBBox: BBox?, function: Visitor) {
        query(createBBoxTileFilter(queryBBox), function)
    }

    fun query(tileFilter: TileFilter?, function: Visitor)

    fun close()

    interface TileFilter {
        /**
         * @return true if all edges within the given bounding box shall be accepted
         */
        fun acceptAll(tile: BBox): Boolean

        /**
         * @return true if edges within the given bounding box shall potentially be accepted. In this
         * case the tile filter will be applied again for smaller bounding boxes on a lower level.
         * If this is the lowest level already simply all edges will be accepted.
         */
        fun acceptPartially(tile: BBox): Boolean
    }

    /**
     * This interface allows to visit edges stored in the LocationIndex.
     */
    fun interface Visitor {
        fun onEdge(edgeId: Int)

        fun isTileInfo(): Boolean = false

        /**
         * This method is called if isTileInfo returns true.
         */
        fun onTile(bbox: BBox, depth: Int) {
        }
    }

    companion object {
        @JvmStatic
        fun createBBoxTileFilter(queryBBox: BBox?): TileFilter? {
            return if (queryBBox == null) null else object : TileFilter {
                override fun acceptAll(tile: BBox): Boolean = queryBBox.contains(tile)

                override fun acceptPartially(tile: BBox): Boolean = queryBBox.intersects(tile)
            }
        }
    }
}
