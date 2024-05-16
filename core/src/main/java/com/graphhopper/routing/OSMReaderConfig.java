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

package com.graphhopper.routing;

import java.util.ArrayList;
import java.util.List;

public class OSMReaderConfig {
    private List<String> ignoredHighways = new ArrayList<>();
    private boolean parseWayNames = true;
    private String preferredLanguage = "";
    private double maxWayPointDistance = 0.5;
    private double elevationMaxWayPointDistance = Double.MAX_VALUE;
    private String smoothElevation = "";

    private double smoothElevationAverageWindowSize = 150.0;
    private int ramerElevationSmoothingMax = 5;
    private double longEdgeSamplingDistance = Double.MAX_VALUE;
    private int workerThreads = 2;
    private double defaultElevation = 0;

    public List<String> getIgnoredHighways() {
        return ignoredHighways;
    }

    /**
     * Sets the values of the highway tag that shall be ignored when we read the OSM file. This can be used to speed up
     * the import and reduce the size of the resulting routing graph. For example if one is only interested in routing
     * for motorized vehicles the routing graph size can be reduced by excluding footways, cycleways, paths and/or
     * tracks. This can be quite significant depending on your area. Not only are there fewer ways to be processed, but
     * there are also fewer junctions, which means fewer nodes and edges. Another reason to exclude footways etc. for
     * motorized vehicle routing could be preventing undesired u-turns (#1858). Similarly, one could exclude motorway,
     * trunk or even primary highways for bicycle or pedestrian routing.
     */
    public OSMReaderConfig setIgnoredHighways(List<String> ignoredHighways) {
        this.ignoredHighways = ignoredHighways;
        return this;
    }

    public String getPreferredLanguage() {
        return preferredLanguage;
    }

    /**
     * Sets the language used to parse way names. For example if this is set to 'en' we will use the 'name:en' tag
     * rather than the 'name' tag if it is present. The language code should be given as defined in ISO 639-1 or ISO 639-2.
     * This setting becomes irrelevant if parseWayNames is set to false.
     */
    public OSMReaderConfig setPreferredLanguage(String preferredLanguage) {
        this.preferredLanguage = preferredLanguage;
        return this;
    }

    public boolean isParseWayNames() {
        return parseWayNames;
    }

    /**
     * Enables/disables the parsing of the name and ref tags to set the name of the graph edges
     */
    public OSMReaderConfig setParseWayNames(boolean parseWayNames) {
        this.parseWayNames = parseWayNames;
        return this;
    }

    public double getMaxWayPointDistance() {
        return maxWayPointDistance;
    }

    /**
     * This parameter affects the routine used to simplify the edge geometries (Ramer-Douglas-Peucker). Higher values mean
     * more details are preserved. The default is 1 (meter). Simplification can be disabled by setting it to 0.
     */
    public OSMReaderConfig setMaxWayPointDistance(double maxWayPointDistance) {
        this.maxWayPointDistance = maxWayPointDistance;
        return this;
    }

    public double getElevationMaxWayPointDistance() {
        return elevationMaxWayPointDistance;
    }

    /**
     * Sets the max elevation discrepancy between way points and the simplified polyline in meters
     */
    public OSMReaderConfig setElevationMaxWayPointDistance(double elevationMaxWayPointDistance) {
        this.elevationMaxWayPointDistance = elevationMaxWayPointDistance;
        return this;
    }

    public String getElevationSmoothing() {
        return smoothElevation;
    }

    /**
     * Enables/disables elevation smoothing
     */
    public OSMReaderConfig setElevationSmoothing(String smoothElevation) {
        this.smoothElevation = smoothElevation;
        return this;
    }

    public int getElevationSmoothingRamerMax() {
        return ramerElevationSmoothingMax;
    }

    public OSMReaderConfig setElevationSmoothingRamerMax(int max) {
        this.ramerElevationSmoothingMax = max;
        return this;
    }

    public double getSmoothElevationAverageWindowSize() {
        return smoothElevationAverageWindowSize;
    }

    public void setSmoothElevationAverageWindowSize(double smoothElevationAverageWindowSize) {
        this.smoothElevationAverageWindowSize = smoothElevationAverageWindowSize;
    }

    public double getLongEdgeSamplingDistance() {
        return longEdgeSamplingDistance;
    }

    /**
     * Sets the distance between elevation samples on long edges
     */
    public OSMReaderConfig setLongEdgeSamplingDistance(double longEdgeSamplingDistance) {
        this.longEdgeSamplingDistance = longEdgeSamplingDistance;
        return this;
    }

    public int getWorkerThreads() {
        return workerThreads;
    }

    /**
     * Sets the number of threads used for the OSM import
     */
    public OSMReaderConfig setWorkerThreads(int workerThreads) {
        this.workerThreads = workerThreads;
        return this;
    }

    public double getDefaultElevation() {
        return defaultElevation;
    }

    public OSMReaderConfig setDefaultElevation(double defaultElevation) {
        this.defaultElevation = defaultElevation;
        return this;
    }
}
