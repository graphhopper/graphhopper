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

package com.graphhopper.routing.ch

import com.carrotsearch.hppc.IntObjectMap
import com.carrotsearch.hppc.IntObjectScatterMap
import com.graphhopper.coll.primitive.IntObjectHashMap
import com.graphhopper.util.EdgeIterator.Companion.NO_EDGE
import java.util.PriorityQueue

/**
 * Used to find 'bridge-paths' during edge-based CH preparation. Bridge-paths are paths that start and end at neighbor
 * nodes of the node we like to contract without visiting any nodes other than that node. They can include loops at the
 * node to be contracted. These are the paths that we might have to replace with shortcuts if no witness paths exist.
 *
 * @author easbar
 * @see EdgeBasedNodeContractor
 */
class BridgePathFinder(private val graph: CHPreparationGraph) {
    private val outExplorer: PrepareGraphEdgeExplorer = graph.createOutEdgeExplorer()
    private val origOutExplorer: PrepareGraphOrigEdgeExplorer = graph.createOutOrigEdgeExplorer()
    private val queue: PriorityQueue<PrepareCHEntry> = PriorityQueue()
    private val map: IntObjectMap<PrepareCHEntry> = IntObjectScatterMap()

    /**
     * Finds all bridge paths starting at a given node and starting edge key.
     *
     * @return a mapping between the target edge keys we can reach via bridge paths and information about the
     * corresponding bridge path
     */
    fun find(startInEdgeKey: Int, startNode: Int, centerNode: Int): IntObjectHashMap<BridePathEntry> {
        queue.clear()
        map.clear()
        // seed 123 pins the iteration order of the result map (formerly HashOrderMixing.constant(123)),
        // which drives shortcut creation order in EdgeBasedNodeContractor
        val result = IntObjectHashMap<BridePathEntry>(16, 0.5, 123L)
        val startEntry = PrepareCHEntry(NO_EDGE, startInEdgeKey, startInEdgeKey, startNode, 0.0, 0)
        map.put(startInEdgeKey, startEntry)
        queue.add(startEntry)
        while (!queue.isEmpty()) {
            val currEntry = queue.poll()
            val iter = outExplorer.setBaseNode(currEntry.adjNode)
            while (iter.next()) {
                if (iter.getAdjNode() == centerNode) {
                    // We arrived at the center node, so we keep expanding the search
                    val weight = currEntry.weight +
                            graph.getTurnWeight(currEntry.incEdgeKey, currEntry.adjNode, iter.getOrigEdgeKeyFirst()) +
                            iter.getWeight()
                    if (weight.isInfinite())
                        continue
                    var entry = map.get(iter.getOrigEdgeKeyLast())
                    if (entry == null) {
                        entry = PrepareCHEntry(iter.getPrepareEdge(), iter.getOrigEdgeKeyFirst(), iter.getOrigEdgeKeyLast(), iter.getAdjNode(), weight, currEntry.origEdges + iter.getOrigEdgeCount())
                        entry.parent = currEntry
                        map.put(iter.getOrigEdgeKeyLast(), entry)
                        queue.add(entry)
                    } else if (weight < entry.weight) {
                        queue.remove(entry)
                        entry.prepareEdge = iter.getPrepareEdge()
                        entry.origEdges = currEntry.origEdges + iter.getOrigEdgeCount()
                        entry.firstEdgeKey = iter.getOrigEdgeKeyFirst()
                        entry.weight = weight
                        entry.parent = currEntry
                        queue.add(entry)
                    }
                } else if (currEntry.adjNode == centerNode) {
                    // We just left the center node, so we arrived at some neighbor node. Every edge we can reach from
                    // there is a target edge, so we add a bridge path entry for it. We do not continue the search from the
                    // neighbor node anymore
                    val weight = currEntry.weight +
                            graph.getTurnWeight(currEntry.incEdgeKey, currEntry.adjNode, iter.getOrigEdgeKeyFirst()) +
                            iter.getWeight()
                    if (weight.isInfinite())
                        continue
                    val origOutIter = origOutExplorer.setBaseNode(iter.getAdjNode())
                    while (origOutIter.next()) {
                        val totalWeight = weight + graph.getTurnWeight(
                                iter.getOrigEdgeKeyLast(), iter.getAdjNode(), origOutIter.getOrigEdgeKeyFirst())
                        if (totalWeight.isInfinite())
                            continue
                        var resEntry = result.get(origOutIter.getOrigEdgeKeyFirst())
                        if (resEntry == null) {
                            val chEntry = PrepareCHEntry(iter.getPrepareEdge(), iter.getOrigEdgeKeyFirst(), iter.getOrigEdgeKeyLast(), iter.getAdjNode(), weight, currEntry.origEdges + iter.getOrigEdgeCount())
                            chEntry.parent = currEntry
                            resEntry = BridePathEntry(totalWeight, chEntry)
                            result.put(origOutIter.getOrigEdgeKeyFirst(), resEntry)
                        } else if (totalWeight < resEntry.weight) {
                            resEntry.weight = totalWeight
                            resEntry.chEntry.prepareEdge = iter.getPrepareEdge()
                            resEntry.chEntry.firstEdgeKey = iter.getOrigEdgeKeyFirst()
                            resEntry.chEntry.origEdges = currEntry.origEdges + iter.getOrigEdgeCount()
                            resEntry.chEntry.incEdgeKey = iter.getOrigEdgeKeyLast()
                            resEntry.chEntry.weight = weight
                            resEntry.chEntry.parent = currEntry
                        }
                    }
                }
                // We arrived at some node that is not the center node. We do not expand the search as we are only
                // concerned with finding bridge paths.
            }
        }
        return result
    }

    class BridePathEntry(@JvmField var weight: Double, @JvmField var chEntry: PrepareCHEntry) {
        override fun toString(): String = "weight: $weight, chEntry: $chEntry"
    }
}
