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
package com.graphhopper.util

import com.graphhopper.ResponsePath
import com.graphhopper.util.details.PathDetail

/**
 * This class simplifies the path, using [RamerDouglasPeucker], but also considers a given list of partitions of
 * the path. Each partition separates the points of the path into non-overlapping intervals and the simplification is
 * done such that we never simplify across the boundaries of these intervals. This is important, because the points
 * at the interval boundaries must not be removed, e.g. when they are referenced by instructions.
 * For example for a path with twenty points and three partitions like this
 *
 * - (0,1,2,3)(3,4)(4,4)(4,5,6,7)(7,8,9,10,11,12)(12,13,14,15,16)(17,18,19)
 * - (0,1)(1,2,3,4)(4,5,6,7)(7,7)(8,9,10,11)(12,13,14,15)(16,17,18,19)
 * - (0,1,2,3,4,5)(6,7,8,9,10,11,12,13,14),(14,15,16,17,18)(18,18)(18,19)
 *
 * we run the simplification for the following intervals:
 *
 * (0,1)(1,2,3)(3,4)(4,5)(5,6,7)(7,8,9,10,11)(11,12)(12,13,14)(14,15)(15,16)(16,17,18)(18,19)
 *
 * @author Robin Boldt
 * @author easbar
 */
class PathSimplification private constructor(
    private val pointList: PointList,
    /**
     * @see PathSimplification
     */
    private val partitions: List<Partition>,
    private val ramerDouglasPeucker: RamerDouglasPeucker
) {
    // temporary variables used when traversing the different partitions
    private val numPartitions: Int = partitions.size
    private val currIntervalIndex = IntArray(numPartitions)
    private val currIntervalStart = IntArray(numPartitions)
    private val currIntervalEnd = IntArray(numPartitions)
    private val partitionFinished = BooleanArray(numPartitions)
    // keep track of how many points were removed by Ramer-Douglas-Peucker in the current and previous intervals
    private val removedPointsInCurrInterval = IntArray(numPartitions)
    private val removedPointsInPrevIntervals = IntArray(numPartitions)

    private fun simplify() {
        if (pointList.size() <= 2) {
            pointList.makeImmutable()
            return
        }

        // no partitions -> no constraints, just simplify the entire point list
        if (partitions.isEmpty()) {
            ramerDouglasPeucker.simplify(pointList, 0, pointList.size() - 1)
            pointList.makeImmutable()
            return
        }

        // Ramer-Douglas-Peucker never removes the first/last point of a given interval, so as long as we only run it
        // on each interval we can be sure that the interval boundaries will remain in the point list.
        // Whenever we remove points from an interval we have to update the interval indices of all partitions.
        // For example if an interval goes from point 4 to 9 and we remove points 5 and 7 we have to update the interval
        // to [4,7].
        // The basic idea to do this is as follows: We iterate through the point list and whenever we hit an interval
        // end (q) in one of the partitions we run Ramer-Douglas-Peucker for the interval [p,q], where p is the point where
        // the last interval ended. We keep track of the number of removed points in the current and previous intervals
        // to be able to calculate the updated indices.

        // prepare for the first interval in each partition
        var intervalStart = 0
        for (i in 0 until numPartitions) {
            currIntervalEnd[i] = this.partitions[i].getIntervalLength(currIntervalIndex[i])
        }

        // iterate the point list and simplify and update the intervals on the go
        var p = 0
        while (p < pointList.size()) {
            var removed = 0
            // first we check if we hit the end of an interval for one of the partitions and run Ramer-Douglas-Peucker if we do
            for (s in 0 until numPartitions) {
                if (partitionFinished[s]) {
                    continue
                }
                if (p == currIntervalEnd[s]) {
                    // This is important for performance: we must not compress the point list after each call to
                    // simplify, otherwise a lot of data is copied, especially for long routes (e.g. many via nodes),
                    // see #1764. Note that since the point list does not get compressed here yet we have to keep track
                    // of the total number of removed points to calculate the new interval boundaries later
                    val compress = false
                    removed = ramerDouglasPeucker.simplify(pointList, intervalStart, currIntervalEnd[s], compress)
                    intervalStart = p
                    break
                }
            }

            // now we have (possibly) removed some points we need to update the current intervals in all partitions
            for (s in 0 until numPartitions) {
                if (partitionFinished[s]) {
                    continue
                }
                removedPointsInCurrInterval[s] += removed
                // if the current interval of this partition ends at p, we update the interval boundaries. there is
                // just a special catch: there can be multiple consecutive intervals that end with p, because there
                // are intervals with a single point, for example p=3 and a partition=[0,3][3,3][3,3]
                var nextIntervalHasOnlyOnePoint: Boolean
                do {
                    if (p == currIntervalEnd[s]) {
                        nextIntervalHasOnlyOnePoint = updateInterval(p, s)
                    } else {
                        break
                    }
                } while (nextIntervalHasOnlyOnePoint)
            }
            p++
        }

        // now we finally have to compress the pointList (actually remove the deleted points). note only after this
        // call the (now shifted) indices in path details and instructions are correct
        RamerDouglasPeucker.removeNaN(pointList)

        // Make sure that the instruction references are not broken
        pointList.makeImmutable()

        assertConsistencyOfIntervals()
    }

    /**
     * @param p point index
     * @param s partition index
     */
    private fun updateInterval(p: Int, s: Int): Boolean {
        var nextIntervalHasOnlyOnePoint = false
        // update interval boundaries
        val updatedStart = currIntervalStart[s] - removedPointsInPrevIntervals[s]
        val updatedEnd = currIntervalEnd[s] - removedPointsInPrevIntervals[s] - removedPointsInCurrInterval[s]
        this.partitions[s].setInterval(currIntervalIndex[s], updatedStart, updatedEnd)

        // update the removed point counters
        removedPointsInPrevIntervals[s] += removedPointsInCurrInterval[s]
        removedPointsInCurrInterval[s] = 0

        // prepare for the next interval
        currIntervalIndex[s]++
        currIntervalStart[s] = p
        if (currIntervalIndex[s] >= this.partitions[s].size()) {
            partitionFinished[s] = true
        } else {
            val length = this.partitions[s].getIntervalLength(currIntervalIndex[s])
            currIntervalEnd[s] += length
            // special case at via points etc.
            if (length == 0) {
                nextIntervalHasOnlyOnePoint = true
            }
        }
        return nextIntervalHasOnlyOnePoint
    }

    private fun assertConsistencyOfIntervals() {
        val expected = pointList.size() - 1
        for (i in partitions.indices) {
            val partition = partitions[i]
            var count = 0
            for (j in 0 until partition.size()) {
                count += partition.getIntervalLength(j)
            }
            if (count != expected) {
                throw IllegalStateException("Simplified intervals are inconsistent: $count vs. $expected for intervals with index: $i")
            }
        }
    }

    /**
     * Represents a partition of a [PointList] into consecutive intervals, for example a list with six points
     * can be partitioned into something like [0,2],[2,2],[2,3][3,5]. Note that intervals with a single point are
     * allowed, but each interval must start where the previous one ended.
     */
    internal interface Partition {
        fun size(): Int

        // todo: it would be nice to be able to retrieve the actual start and end of each interval to make the
        // code here more straight-forward, but currently instructions only offer the length of the interval
        fun getIntervalLength(index: Int): Int

        fun setInterval(index: Int, start: Int, end: Int)
    }

    class Interval(@JvmField var start: Int, @JvmField var end: Int) {
        override fun toString(): String = "[$start, $end]"
    }

    companion object {
        /**
         * Convenience method used to obtain the partitions from a calculated path with details and instructions
         */
        @JvmStatic
        fun simplify(responsePath: ResponsePath, ramerDouglasPeucker: RamerDouglasPeucker, enableInstructions: Boolean): PointList {
            val pointList = responsePath.points
            val partitions = ArrayList<Partition>()

            // make sure all waypoints are retained in the simplified point list
            // we copy the waypoint indices into temporary intervals where they will be mutated by the simplification,
            // afterwards we need to update the way point indices accordingly.
            val intervals = ArrayList<Interval>()
            for (i in 0 until responsePath.waypointIndices.size - 1)
                intervals.add(Interval(responsePath.waypointIndices[i], responsePath.waypointIndices[i + 1]))
            partitions.add(object : Partition {
                override fun size(): Int = intervals.size

                override fun getIntervalLength(index: Int): Int = intervals[index].end - intervals[index].start

                override fun setInterval(index: Int, start: Int, end: Int) {
                    intervals[index].start = start
                    intervals[index].end = end
                }
            })

            // todo: maybe this code can be simplified if path details and instructions would be merged, see #1121
            if (enableInstructions) {
                val instructions = responsePath.instructions
                partitions.add(object : Partition {
                    override fun size(): Int = instructions.size

                    override fun getIntervalLength(index: Int): Int = instructions[index].length

                    override fun setInterval(index: Int, start: Int, end: Int) {
                        val instruction = instructions[index]
                        var end = end
                        if (instruction is ViaInstruction || instruction is FinishInstruction) {
                            if (start != end) {
                                throw IllegalStateException("via- and finish-instructions are expected to have zero length")
                            }
                            // have to make sure that via instructions and finish instructions contain a single point
                            // even though their 'instruction length' is zero.
                            end++
                        }
                        instruction.setPoints(pointList.shallowCopy(start, end, false))
                    }
                })
            }

            for (entry in responsePath.pathDetails.entries) {
                // If the pointList only contains one point, PathDetails have to be empty because 1 point => 0 edges
                val detail = entry.value
                if (detail.isEmpty() && pointList.size() > 1)
                    throw IllegalStateException("PathDetails " + entry.key + " must not be empty")

                partitions.add(object : Partition {
                    override fun size(): Int = detail.size

                    override fun getIntervalLength(index: Int): Int = detail[index].length

                    override fun setInterval(index: Int, start: Int, end: Int) {
                        val pd = detail[index]
                        pd.first = start
                        pd.last = end
                    }
                })
            }

            simplify(responsePath.points, partitions, ramerDouglasPeucker)

            val simplifiedWaypointIndices = ArrayList<Int>()
            simplifiedWaypointIndices.add(intervals[0].start)
            for (interval in intervals)
                simplifiedWaypointIndices.add(interval.end)
            responsePath.setWaypointIndices(simplifiedWaypointIndices)

            assertConsistencyOfPathDetails(responsePath.pathDetails)
            if (enableInstructions)
                assertConsistencyOfInstructions(responsePath.instructions, responsePath.points.size())
            return pointList
        }

        @JvmStatic
        @JvmName("simplify")
        internal fun simplify(pointList: PointList, partitions: List<Partition>, ramerDouglasPeucker: RamerDouglasPeucker) {
            PathSimplification(pointList, partitions, ramerDouglasPeucker).simplify()
        }

        private fun assertConsistencyOfPathDetails(pathDetails: Map<String, List<PathDetail>>) {
            for (pdEntry in pathDetails.entries) {
                val list = pdEntry.value
                if (list.isEmpty())
                    continue

                var prevPD = list[0]
                for (i in 1 until list.size) {
                    if (prevPD.last != list[i].first)
                        throw IllegalStateException("PathDetail list " + pdEntry.key + " is inconsistent due to entries " + prevPD + " vs. " + list[i])

                    prevPD = list[i]
                }
            }
        }

        private fun assertConsistencyOfInstructions(instructions: InstructionList, numPoints: Int) {
            // the total length of the instruction intervals must match the length of the point list.
            // todo: it would be even better to make sure each instruction interval starts where the previous one ended, but
            // currently instructions do not offer this
            val expected = numPoints - 1
            var count = 0
            for (instruction in instructions) {
                count += instruction.length
            }
            if (count != expected) {
                throw IllegalArgumentException("inconsistent instructions, total interval length: $count vs. point list length $expected")
            }
        }
    }
}
