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

public class OSMReaderConfig {
    private boolean parseWayNames = true;
    private String preferredLanguage = "";
    private double maxWayPointDistance = 1;
    private double elevationMaxWayPointDistance = Double.MAX_VALUE;
    private boolean smoothElevation = false;
    private double longEdgeSamplingDistance = Double.MAX_VALUE;
    private int workerThreads = 2;

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
     * This parameter affects the routine used to simplify the edge geometries (Douglas-Peucker). Higher values mean
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

    public boolean isSmoothElevation() {
        return smoothElevation;
    }

    /**
     * Enables/disables elevation smoothing
     */
    public OSMReaderConfig setSmoothElevation(boolean smoothElevation) {
        this.smoothElevation = smoothElevation;
        return this;
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
}
