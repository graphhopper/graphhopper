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
package com.graphhopper.http.resources;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopperAPI;
import com.graphhopper.http.WebHelper;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.StopWatch;
import com.graphhopper.util.shapes.GHPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.util.*;

import static com.graphhopper.util.Parameters.Routing.*;

/**
 * Resource to use GraphHopper in a remote client application like mobile or browser. Note: If type
 * is json it returns the points in GeoJson array format [longitude,latitude] unlike the format "lat,lon"
 * used for the request. See the full API response format in docs/web/api-doc.md
 *
 * @author Peter Karich
 */
@Path("route")
public class RouteResource {

    private static final Logger logger = LoggerFactory.getLogger(RouteResource.class);

    private final GraphHopperAPI graphHopper;
    private final EncodingManager encodingManager;
    private final Boolean hasElevation;

    @Inject
    public RouteResource(GraphHopperAPI graphHopper, EncodingManager encodingManager, @Named("hasElevation") Boolean hasElevation) {
        this.graphHopper = graphHopper;
        this.encodingManager = encodingManager;
        this.hasElevation = hasElevation;
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, "application/gpx+xml"})
    public Response doGet(
            @Context HttpServletRequest httpReq,
            @Context UriInfo uriInfo,
            @QueryParam(WAY_POINT_MAX_DISTANCE) @DefaultValue("1") double minPathPrecision,
            @QueryParam("point") List<GHPoint> requestPoints,
            @QueryParam("type") @DefaultValue("json") String type,
            @QueryParam(INSTRUCTIONS) @DefaultValue("true") boolean instructions,
            @QueryParam(CALC_POINTS) @DefaultValue("true") boolean calcPoints,
            @QueryParam("elevation") @DefaultValue("false") boolean enableElevation,
            @QueryParam("points_encoded") @DefaultValue("true") boolean pointsEncoded,
            @QueryParam("vehicle") @DefaultValue("car") String vehicleStr,
            @QueryParam("weighting") @DefaultValue("fastest") String weighting,
            @QueryParam("algorithm") @DefaultValue("") String algoStr,
            @QueryParam("locale") @DefaultValue("en") String localeStr,
            @QueryParam(Parameters.Routing.POINT_HINT) List<String> pointHints,
            @QueryParam(Parameters.DETAILS.PATH_DETAILS) List<String> pathDetails,
            @QueryParam("heading") List<Double> favoredHeadings,
            @QueryParam("gpx.route") @DefaultValue("true") boolean withRoute /* default to false for the route part in next API version, see #437 */,
            @QueryParam("gpx.track") @DefaultValue("true") boolean withTrack,
            @QueryParam("gpx.waypoints") @DefaultValue("false") boolean withWayPoints,
            @QueryParam("gpx.trackname") @DefaultValue("GraphHopper Track") String trackName,
            @QueryParam("gpx.millis") String timeString) {
        boolean writeGPX = "gpx".equalsIgnoreCase(type);
        instructions = writeGPX || instructions;

        StopWatch sw = new StopWatch().start();

        if(requestPoints.isEmpty()) {
            throw new WebApplicationException(WebHelper.errorResponse(new IllegalArgumentException("You have to pass at least one point"), writeGPX));
        }

        if (!encodingManager.supports(vehicleStr)) {
            throw new WebApplicationException(WebHelper.errorResponse(new IllegalArgumentException("Vehicle not supported: " + vehicleStr), writeGPX));
        } else if (enableElevation && !hasElevation) {
            throw new WebApplicationException(WebHelper.errorResponse(new IllegalArgumentException("Elevation not supported!"), writeGPX));
        } else if (favoredHeadings.size() > 1 && favoredHeadings.size() != requestPoints.size()) {
            throw new WebApplicationException(WebHelper.errorResponse(new IllegalArgumentException("The number of 'heading' parameters must be <= 1 "
                    + "or equal to the number of points (" + requestPoints.size() + ")"), writeGPX));
        }

        if (pointHints.size() > 0 && pointHints.size() != requestPoints.size()) {
            throw new WebApplicationException(WebHelper.errorResponse(new IllegalArgumentException("If you pass " + POINT_HINT + ", you need to pass a hint for every point, empty hints will be ignored"), writeGPX));
        }

        GHRequest request;
        if (favoredHeadings.size() > 0) {
            // if only one favored heading is specified take as start heading
            if (favoredHeadings.size() == 1) {
                List<Double> paddedHeadings = new ArrayList<>(Collections.nCopies(requestPoints.size(), Double.NaN));
                paddedHeadings.set(0, favoredHeadings.get(0));
                request = new GHRequest(requestPoints, paddedHeadings);
            } else {
                request = new GHRequest(requestPoints, favoredHeadings);
            }
        } else {
            request = new GHRequest(requestPoints);
        }

        initHints(request.getHints(), uriInfo.getQueryParameters());
        request.setVehicle(encodingManager.getEncoder(vehicleStr).toString()).
                setWeighting(weighting).
                setAlgorithm(algoStr).
                setLocale(localeStr).
                setPointHints(pointHints).
                setPathDetails(pathDetails).
                getHints().
                put(CALC_POINTS, calcPoints).
                put(INSTRUCTIONS, instructions).
                put(WAY_POINT_MAX_DISTANCE, minPathPrecision);

        GHResponse ghResponse = graphHopper.route(request);

        // TODO: Request logging and timing should perhaps be done somewhere outside
        float took = sw.stop().getSeconds();
        String infoStr = httpReq.getRemoteAddr() + " " + httpReq.getLocale() + " " + httpReq.getHeader("User-Agent");
        String logStr = httpReq.getQueryString() + " " + infoStr + " " + requestPoints + ", took:"
                + took + ", " + algoStr + ", " + weighting + ", " + vehicleStr;

        if (ghResponse.hasErrors()) {
            logger.error(logStr + ", errors:" + ghResponse.getErrors());
            throw new WebApplicationException(WebHelper.errorResponse(ghResponse.getErrors(), writeGPX));
        } else {
            logger.info(logStr + ", alternatives: " + ghResponse.getAll().size()
                    + ", distance0: " + ghResponse.getBest().getDistance()
                    + ", weight0: " + ghResponse.getBest().getRouteWeight()
                    + ", time0: " + Math.round(ghResponse.getBest().getTime() / 60000f) + "min"
                    + ", points0: " + ghResponse.getBest().getPoints().getSize()
                    + ", debugInfo: " + ghResponse.getDebugInfo());
            return writeGPX ?
                    WebHelper.gpxSuccessResponseBuilder(ghResponse, timeString, trackName, enableElevation, withRoute, withTrack, withWayPoints).
                            header("X-GH-Took", "" + Math.round(took * 1000)).
                            build()
                    :
                    Response.ok(WebHelper.jsonObject(ghResponse, instructions, calcPoints, enableElevation, pointsEncoded, took)).
                            header("X-GH-Took", "" + Math.round(took * 1000)).
                            build();
        }
    }

    private static void initHints(HintsMap m, MultivaluedMap<String, String> parameterMap) {
        for (Map.Entry<String, List<String>> e : parameterMap.entrySet()) {
            if (e.getValue().size() == 1) {
                m.put(e.getKey(), e.getValue().get(0));
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
    }

}
