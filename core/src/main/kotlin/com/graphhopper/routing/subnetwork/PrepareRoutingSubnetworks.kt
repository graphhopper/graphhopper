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
package com.graphhopper.routing.subnetwork

import com.carrotsearch.hppc.BitSet
import com.carrotsearch.hppc.IntArrayList
import com.graphhopper.routing.ev.BooleanEncodedValue
import com.graphhopper.routing.weighting.Weighting
import com.graphhopper.storage.BaseGraph
import com.graphhopper.util.GHUtility
import com.graphhopper.util.GHUtility.getEdgeFromEdgeKey
import com.graphhopper.util.Helper
import com.graphhopper.util.StopWatch
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.Collectors
import java.util.stream.IntStream
import java.util.stream.Stream

/**
 * Detects and marks 'subnetworks' with a dedicated subnetwork encoded value. Subnetworks are parts of the road network
 * that are not connected to the rest of the network and that are below a certain size. These can be isolated nodes with
 * no edges at all, but also small subnetworks which could be bugs in OSM data or 'islands' that are separated from
 * the rest of the network because of some missing link, barrier or some closed road for example.
 *
 * Sometimes there are also subnetworks that can be reached from the main network but not the other way around (or the
 * opposite). For example this can be parking lots that can only be accessed by a single one-way road (a mapping error).
 * These are called 'one-way subnetworks' and are marked using the same subnetwork encoded value, see #86. To find such
 * one-way subnetworks it is important to search for strongly connected components on the directed graph and not do a
 * simple connectivity check for one direction.
 *
 * Note that it depends on the weighting whether or not edges belong to a subnetwork or not. For example if a weighting
 * 'closes' a bridge to an island the island might become a subnetwork, but if the bridge was open it would belong to
 * the main network. There can even be subnetworks that are due to turn restrictions.
 *
 * We always run an edge-based connected component search, because this way we retrieve the edges (not the nodes) that
 * belong to each component and can include turn restrictions as well. Node-based component search is faster, but since
 * the subnetwork search goes relatively fast anyway using it has no real benefit.
 *
 * @author Peter Karich
 * @author easbar
 */
class PrepareRoutingSubnetworks(private val graph: BaseGraph, private val prepareJobs: List<PrepareJob>) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private var minNetworkSize = 200
    private var threads = 1

    /**
     * All components of the graph with less than 2*[minNetworkSize] directed edges (edge keys) will be marked
     * as subnetworks. The biggest component will never be marked as subnetwork, even when it is below this size.
     */
    fun setMinNetworkSize(minNetworkSize: Int): PrepareRoutingSubnetworks {
        this.minNetworkSize = minNetworkSize
        return this
    }

    fun setThreads(threads: Int): PrepareRoutingSubnetworks {
        this.threads = threads
        return this
    }

    /**
     * Finds and marks all subnetworks according to [setMinNetworkSize]
     *
     * @return the total number of marked edges
     */
    fun doWork(): Int {
        if (minNetworkSize <= 0) {
            logger.info("Skipping subnetwork search: prepare.min_network_size: $minNetworkSize")
            return 0
        }
        val sw = StopWatch().start()
        logger.info("Start marking subnetworks, prepare.min_network_size: " + minNetworkSize + ", threads: " + threads + ", nodes: " +
                Helper.nf(graph.nodes.toLong()) + ", edges: " + Helper.nf(graph.edges.toLong()) + ", jobs: " + prepareJobs + ", " + Helper.getMemInfo())
        val total = AtomicInteger(0)
        val flags: List<BitSet> = Stream.generate { BitSet(graph.edges.toLong()) }.limit(prepareJobs.size.toLong()).collect(Collectors.toList())
        val runnables: Stream<Runnable> = IntStream.range(0, prepareJobs.size).mapToObj { i ->
            Runnable {
                val job = prepareJobs[i]
                total.addAndGet(setSubnetworks(job.weighting, job.subnetworkEnc.name.replace("_subnetwork", ""), flags[i]))
            }
        }
        GHUtility.runConcurrently(runnables, threads)
        val iter = graph.allEdges
        while (iter.next()) {
            for (i in prepareJobs.indices) {
                val prepareJob = prepareJobs[i]
                iter.set(prepareJob.subnetworkEnc, flags[i].get(iter.edge.toLong()))
            }
        }
        logger.info("Finished finding and marking subnetworks for " + prepareJobs.size + " jobs, took: " + sw.stop().getSeconds() + "s, " + Helper.getMemInfo())
        return total.get()
    }

    private fun setSubnetworks(weighting: Weighting, jobName: String, subnetworkFlags: BitSet): Int {
        // partition graph into strongly connected components using Tarjan's algorithm; stream the
        // components so we never materialize the giant main component. We still need to keep the
        // biggest component "alive" until we know we're not going to mark it, hence the explicit
        // belowThresholdCandidate buffer.
        val minNetworkSizeEdgeKeys = 2 * minNetworkSize
        var sw = StopWatch().start()

        class Consumer : EdgeBasedTarjanSCC.SCCConsumer {
            var currentBuffer: IntArrayList? = null
            var overflowed = false
            var currentSize = 0

            // largest below-threshold component seen so far; we keep it as long as no above-threshold
            // component has shown up. If one ever does, this is no longer the biggest and gets disposed.
            var belowThresholdCandidate: IntArrayList? = null
            var aboveThresholdSeen = false

            var totalComponents = 0
            var totalEdgeKeys = 0
            var numSingleEdgeComponents = 0
            var numMultiEdgeComponents = 0
            var subnetworks = 0
            var markedEdges = 0
            var biggestSubnetwork = 0
            var aboveThresholdMin = Int.MAX_VALUE
            var largestMultiEdgeComponentSize = 0

            override fun beginComponent() {
                currentBuffer = IntArrayList()
                overflowed = false
                currentSize = 0
            }

            override fun edgeKey(edgeKey: Int) {
                currentSize++
                totalEdgeKeys++
                if (!overflowed) {
                    val currentBuffer = this.currentBuffer!!
                    currentBuffer.add(edgeKey)
                    if (minNetworkSizeEdgeKeys > 0 && currentBuffer.size() >= minNetworkSizeEdgeKeys) {
                        this.currentBuffer = null
                        overflowed = true
                    }
                }
            }

            override fun endComponent() {
                totalComponents++
                numMultiEdgeComponents++
                if (currentSize > largestMultiEdgeComponentSize)
                    largestMultiEdgeComponentSize = currentSize
                if (overflowed) {
                    aboveThresholdSeen = true
                    belowThresholdCandidate?.let {
                        disposeMaterialized(it)
                        belowThresholdCandidate = null
                    }
                    if (currentSize < aboveThresholdMin)
                        aboveThresholdMin = currentSize
                } else if (aboveThresholdSeen) {
                    disposeMaterialized(currentBuffer!!)
                } else if (belowThresholdCandidate == null || currentBuffer!!.size() > belowThresholdCandidate!!.size()) {
                    belowThresholdCandidate?.let { disposeMaterialized(it) }
                    belowThresholdCandidate = currentBuffer
                } else {
                    disposeMaterialized(currentBuffer!!)
                }
                currentBuffer = null
                currentSize = 0
            }

            override fun singleEdgeComponent(edgeKey: Int) {
                totalComponents++
                numSingleEdgeComponents++
                totalEdgeKeys++
                if (minNetworkSizeEdgeKeys > 0) {
                    markedEdges += setSubnetworkEdge(edgeKey, weighting, subnetworkFlags)
                    subnetworks++
                    if (biggestSubnetwork < 1)
                        biggestSubnetwork = 1
                }
            }

            fun disposeMaterialized(c: IntArrayList) {
                if (c.size() < minNetworkSizeEdgeKeys) {
                    for (cursor in c)
                        markedEdges += setSubnetworkEdge(cursor.value, weighting, subnetworkFlags)
                    subnetworks++
                    if (c.size() > biggestSubnetwork)
                        biggestSubnetwork = c.size()
                } else if (c.size() < aboveThresholdMin) {
                    aboveThresholdMin = c.size()
                }
            }
        }

        val c = Consumer()
        EdgeBasedTarjanSCC.findComponentsStreaming(graph,
            { prev, edge -> GHUtility.calcWeightWithTurnWeight(weighting, edge, false, prev).isFinite() },
            c)

        logger.info(jobName + " - Found " + c.totalComponents + " subnetworks (" + c.numSingleEdgeComponents + " single edges and "
                + c.numMultiEdgeComponents + " components with more than one edge, total nodes: " + c.totalEdgeKeys + "), took: " + sw.stop().getSeconds() + "s")

        sw = StopWatch().start()
        var smallestNonSubnetwork = Int.MAX_VALUE
        val belowThresholdCandidate = c.belowThresholdCandidate
        if (belowThresholdCandidate != null)
            smallestNonSubnetwork = belowThresholdCandidate.size()
        if (c.aboveThresholdMin < smallestNonSubnetwork)
            smallestNonSubnetwork = c.aboveThresholdMin
        if (smallestNonSubnetwork == Int.MAX_VALUE)
            smallestNonSubnetwork = 0
        if (minNetworkSizeEdgeKeys == 0 && c.numSingleEdgeComponents > 0 && smallestNonSubnetwork > 1)
            smallestNonSubnetwork = 1

        val allowedMarked = graph.edges / 2
        if (c.markedEdges / 2 > allowedMarked)
            throw IllegalStateException("Too many total (directed) edges were marked as subnetwork edges: " + c.markedEdges + " out of " + (2 * graph.edges) + "\n" +
                    "The maximum number of subnetwork edges is: " + (2 * allowedMarked))

        logger.info(jobName + " - Marked " + c.subnetworks + " subnetworks (biggest: " + c.biggestSubnetwork + " edges) -> " +
                (c.totalComponents - c.subnetworks) + " components(s) remain (smallest: " + smallestNonSubnetwork + ", biggest: " + c.largestMultiEdgeComponentSize + " edges)"
                + ", total marked edges: " + c.markedEdges + ", took: " + sw.stop().getSeconds() + "s")
        return c.markedEdges
    }

    private fun setSubnetworkEdge(edgeKey: Int, weighting: Weighting, subnetworkFlags: BitSet): Int {
        // edges that are not accessible anyway are not marked as subnetworks additionally
        if (!weighting.calcEdgeWeight(graph.getEdgeIteratorStateForKey(edgeKey), false).isFinite())
            return 0

        // now get edge again but in stored direction so that subnetwork EV is not overwritten (as it is unidirectional)
        val edge = getEdgeFromEdgeKey(edgeKey)
        return if (!subnetworkFlags.get(edge.toLong())) {
            subnetworkFlags.set(edge.toLong())
            1
        } else {
            0
        }
    }

    class PrepareJob(internal val subnetworkEnc: BooleanEncodedValue, internal val weighting: Weighting) {
        override fun toString(): String = subnetworkEnc.name + "|" + weighting
    }
}
