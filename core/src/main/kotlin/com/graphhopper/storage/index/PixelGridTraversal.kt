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

import com.graphhopper.util.shapes.BBox
import org.locationtech.jts.geom.Coordinate
import java.util.function.Consumer
import kotlin.math.abs
import kotlin.math.min

/**
 * We need all grid cells intersected by a line. The best algorithm is a 'voxel grid traversal algorithm' and
 * described in "A Fast Voxel Traversal Algorithm for Ray Tracing" by John Amanatides and Andrew Woo
 * (1987): http://www.cse.yorku.ca/~amana/research/grid.pdf
 *
 * @author Michael Zilske
 */
class PixelGridTraversal(private val parts: Int, private val bounds: BBox) {
    private val deltaY: Double = (bounds.maxLat - bounds.minLat) / parts
    private val deltaX: Double = (bounds.maxLon - bounds.minLon) / parts

    fun traverse(a: Coordinate, b: Coordinate, consumer: Consumer<Coordinate>) {
        val ax = a.x - bounds.minLon
        val ay = a.y - bounds.minLat
        val bx = b.x - bounds.minLon
        val by = b.y - bounds.minLat

        val stepX = if (ax < bx) 1 else -1
        val stepY = if (ay < by) 1 else -1
        val tDeltaX = deltaX / abs(bx - ax)
        val tDeltaY = deltaY / abs(by - ay)

        // Bounding this with parts - 1 only concerns the case where we are exactly on the bounding box.
        // (The next cell would already start there..)
        var x = min((ax / deltaX).toInt(), parts - 1)
        var y = min((ay / deltaY).toInt(), parts - 1)
        val x2 = min((bx / deltaX).toInt(), parts - 1)
        val y2 = min((by / deltaY).toInt(), parts - 1)
        var tMaxX = ((x + (if (stepX < 0) 0 else 1)) * deltaX - ax) / (bx - ax)
        var tMaxY = ((y + (if (stepY < 0) 0 else 1)) * deltaY - ay) / (by - ay)

        consumer.accept(Coordinate(x.toDouble(), y.toDouble()))
        while (y != y2 || x != x2) {
            if ((tMaxX < tMaxY || y == y2) && x != x2) {
                tMaxX += tDeltaX
                x += stepX
            } else {
                tMaxY += tDeltaY
                y += stepY
            }
            consumer.accept(Coordinate(x.toDouble(), y.toDouble()))
        }
    }
}
