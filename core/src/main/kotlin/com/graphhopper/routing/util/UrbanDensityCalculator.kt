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
package com.graphhopper.routing.util

import com.graphhopper.routing.ev.BooleanEncodedValue
import com.graphhopper.routing.ev.EnumEncodedValue
import com.graphhopper.routing.ev.RoadClass
import com.graphhopper.routing.ev.UrbanDensity
import com.graphhopper.storage.Graph
import com.graphhopper.util.EdgeIteratorState
import com.graphhopper.util.StopWatch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.function.BiConsumer
import java.util.function.ToDoubleFunction

object UrbanDensityCalculator {
    private val logger: Logger = LoggerFactory.getLogger(UrbanDensityCalculator::class.java)

    /**
     * Calculates the urban density (rural/residential/city) for all edges of the graph.
     * First a weighted road density is calculated for every edge to determine whether it belongs to a residential area.
     * In a second step very dense residential areas are classified as 'city'.
     *
     * @param residentialAreaRadius      radius used for residential area calculation in meters
     * @param residentialAreaSensitivity Use higher values if there are too many residential areas that are not recognized. Use
     *                                   smaller values if there are too many non-residential areas that are classified as residential.
     * @param cityAreaRadius             in meters, see residentialAreaRadius
     * @param cityAreaSensitivity        similar to residentialAreaSensitivity, but for the city classification
     * @param threads                    number of threads used to calculate the road densities
     */
    @JvmStatic
    fun calcUrbanDensity(graph: Graph, urbanDensityEnc: EnumEncodedValue<UrbanDensity>,
                         roadClassEnc: EnumEncodedValue<RoadClass>, roadClassLinkEnc: BooleanEncodedValue,
                         residentialAreaRadius: Double, residentialAreaSensitivity: Double,
                         cityAreaRadius: Double, cityAreaSensitivity: Double,
                         threads: Int) {
        logger.info("Calculating residential areas ..., radius={}, sensitivity={}, threads={}", residentialAreaRadius, residentialAreaSensitivity, threads)
        var sw = StopWatch.started()
        calcResidential(graph, urbanDensityEnc, roadClassEnc, roadClassLinkEnc, residentialAreaRadius, residentialAreaSensitivity, threads)
        logger.info("Finished calculating residential areas, took: " + sw.stop().getSeconds() + "s")
        if (cityAreaRadius > 1) {
            logger.info("Calculating city areas ..., radius={}, sensitivity={}, threads={}", cityAreaRadius, cityAreaSensitivity, threads)
            sw = StopWatch.started()
            calcCity(graph, urbanDensityEnc, cityAreaRadius, cityAreaSensitivity, threads)
            logger.info("Finished calculating city areas, took: " + sw.stop().getSeconds() + "s")
        }
    }

    private fun calcResidential(graph: Graph, urbanDensityEnc: EnumEncodedValue<UrbanDensity>,
                                roadClassEnc: EnumEncodedValue<RoadClass>, roadClassLinkEnc: BooleanEncodedValue,
                                radius: Double, sensitivity: Double, threads: Int) {
        val calcRoadFactor = ToDoubleFunction<EdgeIteratorState> { edge ->
            val roadClass = edge.get(roadClassEnc)
            // we're interested in the road density of 'urban' roads, so dense road clusters of outdoor
            // roads like tracks or paths and road class links should not contribute to the residential density
            if (edge.get(roadClassLinkEnc) ||
                roadClass == RoadClass.TRACK ||
                roadClass == RoadClass.SERVICE ||
                roadClass == RoadClass.PATH ||
                roadClass == RoadClass.BRIDLEWAY
            )
                0.0
            else
                1.0
        }
        // temporarily write results to an external array for thread-safety
        val isResidential = BooleanArray(graph.edges)
        RoadDensityCalculator.calcRoadDensities(graph, BiConsumer { calculator, edge ->
            val roadDensity = calculator.calcRoadDensity(edge, radius, calcRoadFactor)
            isResidential[edge.edge] = roadDensity * sensitivity >= 1.0
        }, threads)
        for (edge in isResidential.indices)
            graph.getEdgeIteratorState(edge, Int.MIN_VALUE)!!.set(urbanDensityEnc, if (isResidential[edge]) UrbanDensity.RESIDENTIAL else UrbanDensity.RURAL)
    }

    private fun calcCity(graph: Graph, urbanDensityEnc: EnumEncodedValue<UrbanDensity>,
                         radius: Double, sensitivity: Double, threads: Int) {
        // do not modify the urban density values as long as we are still reading them -> store city flags in this array first
        val isCity = BooleanArray(graph.edges)
        val calcRoadFactor = ToDoubleFunction<EdgeIteratorState> { edge ->
            if (edge.get(urbanDensityEnc) == UrbanDensity.RESIDENTIAL) 1.0 else 0.0
        }
        RoadDensityCalculator.calcRoadDensities(graph, BiConsumer { calculator, edge ->
            val urbanDensity = edge.get(urbanDensityEnc)
            if (urbanDensity != UrbanDensity.RURAL) {
                val roadDensity = calculator.calcRoadDensity(edge, radius, calcRoadFactor)
                if (roadDensity * sensitivity >= 1.0)
                    isCity[edge.edge] = true
            }
        }, threads)
        for (edge in isCity.indices)
            if (isCity[edge])
                graph.getEdgeIteratorState(edge, Int.MIN_VALUE)!!.set(urbanDensityEnc, UrbanDensity.CITY)
    }
}
