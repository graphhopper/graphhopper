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

import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.util.Helper;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.shapes.GHPoint;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import java.util.*;

import static com.graphhopper.util.Parameters.Routing.*;

/**
 * GraphHopper request wrapper to simplify requesting GraphHopper.
 *
 * @author Peter Karich
 * @author ratrun
 */
public class GHRequest {
    private final List<GHPoint> points;
    private final HintsMap hints = new HintsMap();
    // List of favored start (1st element) and arrival heading (all other).
    // Headings are north based azimuth (clockwise) in (0, 360) or NaN for equal preference
    private final List<Double> favoredHeadings;
    private List<String> pointHints = new ArrayList<>();
    private List<String> pathDetails = new ArrayList<>();
    private String algo = "";
    private boolean possibleToAdd = false;
    private Locale locale = Locale.US;

    public GHRequest() {
        this(5);
    }

    public GHRequest(int size) {
        points = new ArrayList<GHPoint>(size);
        favoredHeadings = new ArrayList<Double>(size);
        possibleToAdd = true;
    }

    /**
     * Set routing request from specified startPlace (fromLat, fromLon) to endPlace (toLat, toLon)
     * with a preferred start and end heading. Headings are north based azimuth (clockwise) in (0,
     * 360) or NaN for equal preference.
     */
    public GHRequest(double fromLat, double fromLon, double toLat, double toLon,
                     double startHeading, double endHeading) {
        this(new GHPoint(fromLat, fromLon), new GHPoint(toLat, toLon), startHeading, endHeading);
    }

    /**
     * Set routing request from specified startPlace (fromLat, fromLon) to endPlace (toLat, toLon)
     */
    public GHRequest(double fromLat, double fromLon, double toLat, double toLon) {
        this(new GHPoint(fromLat, fromLon), new GHPoint(toLat, toLon));
    }

    /**
     * Set routing request from specified startPlace to endPlace with a preferred start and end
     * heading. Headings are north based azimuth (clockwise) in (0, 360) or NaN for equal preference
     */
    public GHRequest(GHPoint startPlace, GHPoint endPlace, double startHeading, double endHeading) {
        if (startPlace == null)
            throw new IllegalStateException("'from' cannot be null");

        if (endPlace == null)
            throw new IllegalStateException("'to' cannot be null");

        points = new ArrayList<GHPoint>(2);
        points.add(startPlace);
        points.add(endPlace);

        favoredHeadings = new ArrayList<Double>(2);
        validateAzimuthValue(startHeading);
        favoredHeadings.add(startHeading);
        validateAzimuthValue(endHeading);
        favoredHeadings.add(endHeading);
    }

    public GHRequest(GHPoint startPlace, GHPoint endPlace) {
        this(startPlace, endPlace, Double.NaN, Double.NaN);
    }

    /**
     * Set routing request
     * <p>
     *
     * @param points          List of stopover points in order: start, 1st stop, 2nd stop, ..., end
     * @param favoredHeadings List of favored headings for starting (start point) and arrival (via
     *                        and end points) Headings are north based azimuth (clockwise) in (0, 360) or NaN for equal
     */
    public GHRequest(List<GHPoint> points, List<Double> favoredHeadings) {
        if (points.size() != favoredHeadings.size())
            throw new IllegalArgumentException("Size of headings (" + favoredHeadings.size()
                    + ") must match size of points (" + points.size() + ")");

        for (Double heading : favoredHeadings) {
            validateAzimuthValue(heading);
        }
        this.points = points;
        this.favoredHeadings = favoredHeadings;
    }

    public GHRequest(
            @Context UriInfo uriInfo,
            @QueryParam("point") List<GHPoint> points,
            @QueryParam("heading") List<Double> favoredHeadings,
            @QueryParam("vehicle") @DefaultValue("car") String vehicleStr,
            @QueryParam("weighting") @DefaultValue("fastest") String weighting,
            @QueryParam("algorithm") @DefaultValue("") String algoStr,
            @QueryParam("locale") @DefaultValue("en") String localeStr,
            @QueryParam(CALC_POINTS) @DefaultValue("true") boolean calcPoints,
            @QueryParam(INSTRUCTIONS) @DefaultValue("true") boolean instructions,
            @QueryParam(WAY_POINT_MAX_DISTANCE) @DefaultValue("1") double minPathPrecision,
            @QueryParam(Parameters.Routing.POINT_HINT) List<String> pointHints,
            @QueryParam(Parameters.DETAILS.PATH_DETAILS) List<String> pathDetails) {
        if (favoredHeadings.size() == 0) {
            favoredHeadings = new ArrayList<>(Collections.nCopies(points.size(), Double.NaN));
        } else if (favoredHeadings.size() == 1 && points.size() > 0) {
            // if only one favored heading is specified take as start heading
            final Double startHeading = favoredHeadings.get(0);
            favoredHeadings = new ArrayList<>(Collections.nCopies(points.size(), Double.NaN));
            favoredHeadings.set(0, startHeading);
        }
        this.points = points;
        this.favoredHeadings = favoredHeadings;
        for (Map.Entry<String, List<String>> e : uriInfo.getQueryParameters().entrySet()) {
            if (e.getValue().size() == 1) {
                hints.put(e.getKey(), e.getValue().get(0));
            } else {
                // Do nothing.
                // TODO: this is dangerous: I can only silently swallow
                // the forbidden multiparameter. If I comment-in the line below,
                // I get an exception, because "point" regularly occurs
                // multiple times.
                // I think either unknown parameters (hints) should be allowed
                // to be multiparameters, too, or we shouldn't use them for
                // known parameters either, _or_ known parameters
                // must be filtered before they come to this code point,
                // _or_ we stop passing unknown parameters alltogether..
                //
                // throw new WebApplicationException(String.format("This query parameter (hint) is not allowed to occur multiple times: %s", e.getKey()));
            }
        }
        setVehicle(vehicleStr);
        setWeighting(weighting);
        setAlgorithm(algoStr);
        setLocale(localeStr);
        setPointHints(pointHints);
        setPathDetails(pathDetails);
        hints.
            put(CALC_POINTS, calcPoints).
            put(INSTRUCTIONS, instructions).
            put(WAY_POINT_MAX_DISTANCE, minPathPrecision);
    }


    /**
     * Set routing request
     * <p>
     *
     * @param points List of stopover points in order: start, 1st stop, 2nd stop, ..., end
     */
    public GHRequest(List<GHPoint> points) {
        this(points, Collections.nCopies(points.size(), Double.NaN));
    }

    /**
     * Add stopover point to routing request.
     * <p>
     *
     * @param point          geographical position (see GHPoint)
     * @param favoredHeading north based azimuth (clockwise) in (0, 360) or NaN for equal preference
     */
    public GHRequest addPoint(GHPoint point, double favoredHeading) {
        if (point == null)
            throw new IllegalArgumentException("point cannot be null");

        if (!possibleToAdd)
            throw new IllegalStateException("Please call empty constructor if you intent to use "
                    + "more than two places via addPoint method.");

        points.add(point);
        validateAzimuthValue(favoredHeading);
        favoredHeadings.add(favoredHeading);
        return this;
    }

    /**
     * Add stopover point to routing request.
     * <p>
     *
     * @param point geographical position (see GHPoint)
     */
    public GHRequest addPoint(GHPoint point) {
        addPoint(point, Double.NaN);
        return this;
    }

    /**
     * @return north based azimuth (clockwise) in (0, 360) or NaN for equal preference
     */
    public double getFavoredHeading(int i) {
        return favoredHeadings.get(i);
    }

    /**
     * @return if there exist a preferred heading for start/via/end point i
     */
    public boolean hasFavoredHeading(int i) {
        if (i >= favoredHeadings.size())
            return false;

        return !Double.isNaN(favoredHeadings.get(i));
    }

    private void validateAzimuthValue(double heading) {
        // heading must be in (0, 360) oder NaN
        if (!Double.isNaN(heading) && (Double.compare(heading, 360) > 0 || Double.compare(heading, 0) < 0))
            throw new IllegalArgumentException("Heading " + heading + " must be in range (0,360) or NaN");
    }

    public List<GHPoint> getPoints() {
        return points;
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

    public String getWeighting() {
        return hints.getWeighting();
    }

    /**
     * By default it supports fastest and shortest. Or specify empty to use default.
     */
    public GHRequest setWeighting(String w) {
        hints.setWeighting(w);
        return this;
    }

    public String getVehicle() {
        return hints.getVehicle();
    }

    /**
     * Specify car, bike or foot. Or specify empty to use default.
     */
    public GHRequest setVehicle(String vehicle) {
        hints.setVehicle(vehicle);
        return this;
    }

    public HintsMap getHints() {
        return hints;
    }

    public GHRequest setPointHints(List<String> pointHints) {
        this.pointHints = pointHints;
        return this;
    }

    public List<String> getPointHints() {
        return pointHints;
    }

    public boolean hasPointHints() {
        return pointHints.size() == points.size();
    }

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

    public List<Double> getFavoredHeadings() {
        return favoredHeadings;
    }
}
