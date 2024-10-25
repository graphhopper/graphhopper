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
package com.graphhopper;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.graphhopper.util.CustomModel;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PMap;
import com.graphhopper.util.shapes.GHPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Request object to perform routing with GraphHopper.
 *
 * @author Peter Karich
 * @author ratrun
 */
public class GHRequest {
    private List<GHPoint> points;
    private String profile = "";
    private final PMap hints = new PMap();
    private List<Double> headings = new ArrayList<>();
    private List<String> pointHints = new ArrayList<>();
    private List<String> curbsides = new ArrayList<>();
    private List<String> snapPreventions = List.of("tunnel", "bridge", "ferry");
    private List<String> pathDetails = new ArrayList<>();
    private String algo = "";
    private Locale locale = Locale.US;
    private CustomModel customModel;

    public GHRequest() {
        this(5);
    }

    public GHRequest(int size) {
        points = new ArrayList<>(size);
    }

    /**
     * Creates a routing request from one point (fromLat, fromLon) to another (toLat, toLon)
     */
    public GHRequest(double fromLat, double fromLon, double toLat, double toLon) {
        this(new GHPoint(fromLat, fromLon), new GHPoint(toLat, toLon));
    }

    public GHRequest(GHPoint startPlace, GHPoint endPlace) {
        if (startPlace == null)
            throw new IllegalStateException("'from' cannot be null");

        if (endPlace == null)
            throw new IllegalStateException("'to' cannot be null");

        points = new ArrayList<>(2);
        points.add(startPlace);
        points.add(endPlace);
    }

    /**
     * @param points List of stopover points in order: start, 1st stop, 2nd stop, ..., end
     */
    public GHRequest(List<GHPoint> points) {
        this.points = points;
    }

    public GHRequest setPoints(List<GHPoint> points) {
        this.points = points;
        return this;
    }

    public List<GHPoint> getPoints() {
        return points;
    }

    /**
     * Add stopover point to routing request.
     *
     * @param point geographical position (see GHPoint)
     */
    public GHRequest addPoint(GHPoint point) {
        if (point == null)
            throw new IllegalArgumentException("point cannot be null");
        points.add(point);
        return this;
    }

    /**
     * Sets the headings, i.e. the direction the route should leave the starting point and the directions the route
     * should arrive from at the via-points and the end point. Each heading is given as north based azimuth (clockwise)
     * in [0, 360) or NaN if no direction shall be specified.
     * <p>
     * The number of headings must be zero (default), one (for the start point) or equal to the number of points
     * when sending the request.
     */
    public GHRequest setHeadings(List<Double> headings) {
        this.headings = headings;
        return this;
    }

    public List<Double> getHeadings() {
        return headings;
    }

    public static boolean isAzimuthValue(double heading) {
        // heading must be in [0, 360) or NaN
        return Double.isNaN(heading) || (Double.compare(heading, 360) < 0 && Double.compare(heading, 0) >= 0);
    }

    public String getAlgorithm() {
        return algo;
    }

    /**
     * For possible values see AlgorithmOptions.*
     */
    public GHRequest setAlgorithm(String algo) {
        if (algo != null)
            this.algo = Helper.camelCaseToUnderScore(algo);
        return this;
    }

    public Locale getLocale() {
        return locale;
    }

    public GHRequest setLocale(Locale locale) {
        if (locale != null)
            this.locale = locale;
        return this;
    }

    public GHRequest setLocale(String localeStr) {
        return setLocale(Helper.getLocale(localeStr));
    }

    public CustomModel getCustomModel() {
        return customModel;
    }

    public GHRequest setCustomModel(CustomModel customModel) {
        this.customModel = customModel;
        return this;
    }

    public String getProfile() {
        return profile;
    }

    public GHRequest setProfile(String profile) {
        this.profile = profile;
        return this;
    }

    public PMap getHints() {
        return hints;
    }

    /**
     * This method sets a key value pair in the hints and is unrelated to the setPointHints method.
     * It is mainly used for deserialization with Jackson.
     *
     * @see #setPointHints(List)
     */
    // a good trick to serialize unknown properties into the HintsMap
    @JsonAnySetter
    public GHRequest putHint(String fieldName, Object value) {
        this.hints.putObject(fieldName, value);
        return this;
    }

    public GHRequest setPointHints(List<String> pointHints) {
        this.pointHints = pointHints;
        return this;
    }

    public List<String> getPointHints() {
        return pointHints;
    }

    public GHRequest setCurbsides(List<String> curbsides) {
        this.curbsides = curbsides;
        return this;
    }

    public List<String> getCurbsides() {
        return curbsides;
    }

    public GHRequest setSnapPreventions(List<String> snapPreventions) {
        this.snapPreventions = snapPreventions;
        return this;
    }

    public List<String> getSnapPreventions() {
        return snapPreventions;
    }

    @JsonProperty("details")
    public GHRequest setPathDetails(List<String> pathDetails) {
        this.pathDetails = pathDetails;
        return this;
    }

    public List<String> getPathDetails() {
        return this.pathDetails;
    }

    @Override
    public String toString() {
        String res = "";
        for (GHPoint point : points) {
            if (res.isEmpty()) {
                res = point.toString();
            } else {
                res += "; " + point.toString();
            }
        }
        if (!algo.isEmpty())
            res += " (" + algo + ")";

        if (!pathDetails.isEmpty())
            res += " (PathDetails: " + pathDetails + ")";

        if (!hints.isEmpty())
            res += " (Hints:" + hints + ")";

        return res;
    }
}
