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
package com.graphhopper.routing.lm

import com.graphhopper.routing.ev.EncodedValueLookup
import com.graphhopper.routing.util.AreaIndex
import com.graphhopper.routing.weighting.Weighting
import com.graphhopper.storage.BaseGraph
import com.graphhopper.storage.Directory
import com.graphhopper.util.Helper
import com.graphhopper.util.StopWatch
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * This class does the preprocessing for the ALT algorithm (A* , landmark, triangle inequality).
 *
 * http://www.siam.org/meetings/alenex05/papers/03agoldberg.pdf
 *
 * @author Peter Karich
 */
class PrepareLandmarks(dir: Directory, private val graph: BaseGraph, encodedValueLookup: EncodedValueLookup, private val lmConfig: LMConfig, landmarks: Int) {

    private val lms: LandmarkStorage = LandmarkStorage(graph, encodedValueLookup, dir, lmConfig, landmarks)
    private var totalPrepareTime: Long = 0
    private var prepared = false

    /**
     * @see LandmarkStorage.setLandmarkSuggestions
     */
    fun setLandmarkSuggestions(landmarkSuggestions: List<LandmarkSuggestion>): PrepareLandmarks {
        lms.setLandmarkSuggestions(landmarkSuggestions)
        return this
    }

    /**
     * @see LandmarkStorage.setAreaIndex
     */
    fun setAreaIndex(areaIndex: AreaIndex<SplitArea>): PrepareLandmarks {
        lms.setAreaIndex(areaIndex)
        return this
    }

    /**
     * @see LandmarkStorage.setMaximumWeight
     */
    fun setMaximumWeight(maximumWeight: Double): PrepareLandmarks {
        lms.setMaximumWeight(maximumWeight)
        return this
    }

    /**
     * @see LandmarkStorage.setLMSelectionWeighting
     */
    fun setLMSelectionWeighting(w: Weighting) {
        lms.setLMSelectionWeighting(w)
    }

    /**
     * @see LandmarkStorage.setMinimumNodes
     */
    fun setMinimumNodes(nodes: Int) {
        if (nodes < 2)
            throw IllegalArgumentException("minimum node count must be at least 2")

        lms.setMinimumNodes(nodes)
    }

    fun setLogDetails(logDetails: Boolean): PrepareLandmarks {
        lms.setLogDetails(logDetails)
        return this
    }

    fun getLandmarkStorage(): LandmarkStorage = lms

    fun getLMConfig(): LMConfig = lmConfig

    fun loadExisting(): Boolean = lms.loadExisting()

    fun doWork() {
        if (prepared)
            throw IllegalStateException("Call doWork only once!")
        prepared = true
        val sw = StopWatch().start()
        LOGGER.info("Start calculating " + lms.getLandmarkCount() + " landmarks, weighting:" + lms.getLmSelectionWeighting() + ", " + Helper.getMemInfo())

        lms.createLandmarks()
        lms.flush()

        LOGGER.info("Calculated landmarks for " + (lms.getSubnetworksWithLandmarks() - 1) + " subnetworks, took:" + sw.stop().getSeconds().toInt() + "s => "
                + lms.getLandmarksAsGeoJSON() + ", stored weights:" + lms.getLandmarkCount()
                + ", nodes:" + graph.nodes + ", " + Helper.getMemInfo())
        totalPrepareTime = sw.getMillis()
    }

    fun isPrepared(): Boolean = prepared

    fun getTotalPrepareTime(): Long = totalPrepareTime

    /**
     * Release landmark storage resources
     */
    @JvmName("close")
    internal fun close() {
        this.lms.close()
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(PrepareLandmarks::class.java)
    }
}
