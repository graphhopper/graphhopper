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

import java.util.Arrays;
import java.util.List;

public class OSMReaderConfig {
    private List<String> acceptedHighways = createDefaultAcceptedHighways();
    private boolean parseWayNames = true;
    private String preferredLanguage = "";
    private double maxWayPointDistance = 1;
    private double elevationMaxWayPointDistance = Double.MAX_VALUE;
    private String smoothElevation = "";
    private int ramerElevationSmoothingMax = 5;
    private double longEdgeSamplingDistance = Double.MAX_VALUE;
    private int workerThreads = 2;

    public static List<String> createDefaultAcceptedHighways() {
        return Arrays.asList(
                "motorway", "motorway_link",
                "trunk", "trunk_link",
                "primary", "primary_link",
                "secondary", "secondary_link",
                "tertiary", "tertiary_link",
                "unclassified",
                "residential",
                "living_street",
                "service",
                "pedestrian",
                "track",
                "bus_guideway", "busway",
                "escape", "emergency_bay",
                "raceway",
                "road",
                "footway",
                "bridleway",
                "steps",
                "corridor",
                "path",
                "cycleway",
                "proposed",
                "construction"
        );
    }

    public List<String> getAcceptedHighways() {
        return acceptedHighways;
    }

    /**
     * Sets the values of the highway tag that shall be considered when we read the OSM file. OSM ways that do not include
     * a highway tag with one of these values will be ignored entirely during import, but just because they are
     * accepted does not necessarily mean they will be considered by the route calculations. This still depends on the
     * weighting and/or the access flags for the corresponding edges. Not including some road types here is mostly a
     * performance optimization. For example if one is only interested in routing for motorized vehicles the routing
     * graph size can be reduced by not including tracks, footways and paths (20-50% depending on your area, ~25%
     * for planet files). Another reason to exclude footways etc. for motorized vehicle routing could be preventing
     * undesired u-turns (#1858). Similarly, one could exclude motorway, trunk or even primary for bicycle or pedestrian routing, but since there
     * aren't many such roads the graph size reduction will be very small (<4%).
     */
    public OSMReaderConfig setAcceptedHighways(List<String> acceptedHighways) {
        this.acceptedHighways = acceptedHighways;
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
