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
package com.graphhopper.routing

class OSMReaderConfig {
    private var ignoredHighways: List<String> = ArrayList()
    private var parseWayNames = true
    private var preferredLanguage = ""
    private var maxWayPointDistance = 0.5
    private var elevationMaxWayPointDistance = Double.MAX_VALUE
    private var smoothElevation = ""

    private var smoothElevationAverageWindowSize = 150.0
    private var ramerElevationSmoothingMax = 5
    private var longEdgeSamplingDistance = Double.MAX_VALUE
    private var workerThreads = 2
    private var defaultElevation = 0.0

    fun getIgnoredHighways(): List<String> = ignoredHighways

    /**
     * Sets the values of the highway tag that shall be ignored when we read the OSM file. This can be used to speed up
     * the import and reduce the size of the resulting routing graph. For example if one is only interested in routing
     * for motorized vehicles the routing graph size can be reduced by excluding footways, cycleways, paths and/or
     * tracks. This can be quite significant depending on your area. Not only are there fewer ways to be processed, but
     * there are also fewer junctions, which means fewer nodes and edges. Another reason to exclude footways etc. for
     * motorized vehicle routing could be preventing undesired u-turns (#1858). Similarly, one could exclude motorway,
     * trunk or even primary highways for bicycle or pedestrian routing.
     */
    fun setIgnoredHighways(ignoredHighways: List<String>): OSMReaderConfig {
        this.ignoredHighways = ignoredHighways
        return this
    }

    fun getPreferredLanguage(): String = preferredLanguage

    /**
     * Sets the language used to parse way names. For example if this is set to 'en' we will use the 'name:en' tag
     * rather than the 'name' tag if it is present. The language code should be given as defined in ISO 639-1 or ISO 639-2.
     * This setting becomes irrelevant if parseWayNames is set to false.
     */
    fun setPreferredLanguage(preferredLanguage: String): OSMReaderConfig {
        this.preferredLanguage = preferredLanguage
        return this
    }

    fun isParseWayNames(): Boolean = parseWayNames

    /**
     * Enables/disables the parsing of the name and ref tags to set the name of the graph edges
     */
    fun setParseWayNames(parseWayNames: Boolean): OSMReaderConfig {
        this.parseWayNames = parseWayNames
        return this
    }

    fun getMaxWayPointDistance(): Double = maxWayPointDistance

    /**
     * This parameter affects the routine used to simplify the edge geometries (Ramer-Douglas-Peucker). Higher values mean
     * more details are preserved. The default is 1 (meter). Simplification can be disabled by setting it to 0.
     */
    fun setMaxWayPointDistance(maxWayPointDistance: Double): OSMReaderConfig {
        this.maxWayPointDistance = maxWayPointDistance
        return this
    }

    fun getElevationMaxWayPointDistance(): Double = elevationMaxWayPointDistance

    /**
     * Sets the max elevation discrepancy between way points and the simplified polyline in meters
     */
    fun setElevationMaxWayPointDistance(elevationMaxWayPointDistance: Double): OSMReaderConfig {
        this.elevationMaxWayPointDistance = elevationMaxWayPointDistance
        return this
    }

    fun getElevationSmoothing(): String = smoothElevation

    /**
     * Enables/disables elevation smoothing
     */
    fun setElevationSmoothing(smoothElevation: String): OSMReaderConfig {
        this.smoothElevation = smoothElevation
        return this
    }

    fun getElevationSmoothingRamerMax(): Int = ramerElevationSmoothingMax

    fun setElevationSmoothingRamerMax(max: Int): OSMReaderConfig {
        this.ramerElevationSmoothingMax = max
        return this
    }

    fun getSmoothElevationAverageWindowSize(): Double = smoothElevationAverageWindowSize

    fun setSmoothElevationAverageWindowSize(smoothElevationAverageWindowSize: Double) {
        this.smoothElevationAverageWindowSize = smoothElevationAverageWindowSize
    }

    fun getLongEdgeSamplingDistance(): Double = longEdgeSamplingDistance

    /**
     * Sets the distance between elevation samples on long edges
     */
    fun setLongEdgeSamplingDistance(longEdgeSamplingDistance: Double): OSMReaderConfig {
        this.longEdgeSamplingDistance = longEdgeSamplingDistance
        return this
    }

    fun getWorkerThreads(): Int = workerThreads

    /**
     * Sets the number of threads used for the OSM import
     */
    fun setWorkerThreads(workerThreads: Int): OSMReaderConfig {
        this.workerThreads = workerThreads
        return this
    }

    fun getDefaultElevation(): Double = defaultElevation

    /**
     * Sets the elevation in meters that shall be used if the elevation data source is missing a value
     */
    fun setDefaultElevation(defaultElevation: Double): OSMReaderConfig {
        this.defaultElevation = defaultElevation
        return this
    }
}
