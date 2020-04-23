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
package com.graphhopper.resources;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopperAPI;
import com.graphhopper.MultiException;
import com.graphhopper.http.GHPointParam;
import com.graphhopper.http.WebHelper;
import com.graphhopper.routing.ProfileResolver;
import com.graphhopper.util.*;
import com.graphhopper.util.gpx.GpxFromInstructions;
import com.graphhopper.util.shapes.GHPoint;
import io.dropwizard.jersey.params.AbstractParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.util.List;
import java.util.Map;

import static com.graphhopper.util.Parameters.Details.PATH_DETAILS;
import static com.graphhopper.util.Parameters.Routing.*;
import static java.util.stream.Collectors.toList;

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
    private final ProfileResolver profileResolver;
    private final Boolean hasElevation;

    @Inject
    public RouteResource(GraphHopperAPI graphHopper, ProfileResolver profileResolver, @Named("hasElevation") Boolean hasElevation) {
        this.graphHopper = graphHopper;
        this.profileResolver = profileResolver;
        this.hasElevation = hasElevation;
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, "application/gpx+xml"})
    public Response doGet(
            @Context HttpServletRequest httpReq,
            @Context UriInfo uriInfo,
            @QueryParam(WAY_POINT_MAX_DISTANCE) @DefaultValue("1") double minPathPrecision,
            @QueryParam("point") @NotNull List<GHPointParam> pointParams,
            @QueryParam("type") @DefaultValue("json") String type,
            @QueryParam(INSTRUCTIONS) @DefaultValue("true") boolean instructions,
            @QueryParam(CALC_POINTS) @DefaultValue("true") boolean calcPoints,
            @QueryParam("elevation") @DefaultValue("false") boolean enableElevation,
            @QueryParam("points_encoded") @DefaultValue("true") boolean pointsEncoded,
            @QueryParam("profile") String profileName,
            @QueryParam("algorithm") @DefaultValue("") String algoStr,
            @QueryParam("locale") @DefaultValue("en") String localeStr,
            @QueryParam(POINT_HINT) List<String> pointHints,
            @QueryParam(CURBSIDE) List<String> curbsides,
            @QueryParam(SNAP_PREVENTION) List<String> snapPreventions,
            @QueryParam(PATH_DETAILS) List<String> pathDetails,
            @QueryParam("heading") @NotNull List<Double> headings,
            @QueryParam("gpx.route") @DefaultValue("true") boolean withRoute /* default to false for the route part in next API version, see #437 */,
            @QueryParam("gpx.track") @DefaultValue("true") boolean withTrack,
            @QueryParam("gpx.waypoints") @DefaultValue("false") boolean withWayPoints,
            @QueryParam("gpx.trackname") @DefaultValue("GraphHopper Track") String trackName,
            @QueryParam("gpx.millis") String timeString) {
        List<GHPoint> points = pointParams.stream().map(AbstractParam::get).collect(toList());
        boolean writeGPX = "gpx".equalsIgnoreCase(type);
        instructions = writeGPX || instructions;
        if (enableElevation && !hasElevation)
            throw new IllegalArgumentException("Elevation not supported!");

        StopWatch sw = new StopWatch().start();
        GHRequest request = new GHRequest();
        initHints(request.getHints(), uriInfo.getQueryParameters());
        String weightingVehicleLogStr = "weighting: " + request.getHints().getString("weighting", "") + ", vehicle: " + request.getHints().getString("vehicle", "");
        if (Helper.isEmpty(profileName)) {
            enableEdgeBasedIfThereAreCurbsides(curbsides, request);
            profileName = profileResolver.resolveProfile(request.getHints()).getName();
            removeLegacyParameters(request);
        }
        errorIfLegacyParameters(request.getHints());
        request.setPoints(points).
                setProfile(profileName).
                setAlgorithm(algoStr).
                setLocale(localeStr).
                setHeadings(headings).
                setPointHints(pointHints).
                setCurbsides(curbsides).
                setSnapPreventions(snapPreventions).
                setPathDetails(pathDetails).
                getHints().
                putObject(CALC_POINTS, calcPoints).
                putObject(INSTRUCTIONS, instructions).
                putObject(WAY_POINT_MAX_DISTANCE, minPathPrecision);

        GHResponse ghResponse = graphHopper.route(request);

        long took = sw.stop().getNanos() / 1_000_000;
        String infoStr = httpReq.getRemoteAddr() + " " + httpReq.getLocale() + " " + httpReq.getHeader("User-Agent");
        String logStr = httpReq.getQueryString() + " " + infoStr + " " + points + ", took: "
                + String.format("%.1f", (double) took) + "ms, algo: " + algoStr + ", profile: " + profileName + ", " + weightingVehicleLogStr;

        if (ghResponse.hasErrors()) {
            logger.error(logStr + ", errors:" + ghResponse.getErrors());
            throw new MultiException(ghResponse.getErrors());
        } else {
            logger.info(logStr + ", alternatives: " + ghResponse.getAll().size()
                    + ", distance0: " + ghResponse.getBest().getDistance()
                    + ", weight0: " + ghResponse.getBest().getRouteWeight()
                    + ", time0: " + Math.round(ghResponse.getBest().getTime() / 60000f) + "min"
                    + ", points0: " + ghResponse.getBest().getPoints().getSize()
                    + ", debugInfo: " + ghResponse.getDebugInfo());
            return writeGPX ?
                    gpxSuccessResponseBuilder(ghResponse, timeString, trackName, enableElevation, withRoute, withTrack, withWayPoints, Constants.VERSION).
                            header("X-GH-Took", "" + Math.round(took * 1000)).
                            build()
                    :
                    Response.ok(WebHelper.jsonObject(ghResponse, instructions, calcPoints, enableElevation, pointsEncoded, took)).
                            header("X-GH-Took", "" + Math.round(took * 1000)).
                            type(MediaType.APPLICATION_JSON).
                            build();
        }
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response doPost(@NotNull GHRequest request, @Context HttpServletRequest httpReq) {
        StopWatch sw = new StopWatch().start();
        String weightingVehicleLogStr = "weighting: " + request.getHints().getString("weighting", "")
                + ", vehicle: " + request.getHints().getString("vehicle", "");
        if (Helper.isEmpty(request.getProfile())) {
            enableEdgeBasedIfThereAreCurbsides(request.getCurbsides(), request);
            request.setProfile(profileResolver.resolveProfile(request.getHints()).getName());
            removeLegacyParameters(request);
        }
        errorIfLegacyParameters(request.getHints());
        GHResponse ghResponse = graphHopper.route(request);
        boolean instructions = request.getHints().getBool(INSTRUCTIONS, true);
        boolean enableElevation = request.getHints().getBool("elevation", false);
        boolean calcPoints = request.getHints().getBool(CALC_POINTS, true);
        boolean pointsEncoded = request.getHints().getBool("points_encoded", true);

        long took = sw.stop().getNanos() / 1_000_000;
        String infoStr = httpReq.getRemoteAddr() + " " + httpReq.getLocale() + " " + httpReq.getHeader("User-Agent");
        String queryString = httpReq.getQueryString() == null ? "" : (httpReq.getQueryString() + " ");
        String logStr = queryString + infoStr + " " + request.getPoints().size() + ", took: "
                + String.format("%.1f", (double) took) + " ms, algo: " + request.getAlgorithm() + ", profile: " + request.getProfile()
                + ", " + weightingVehicleLogStr;

        if (ghResponse.hasErrors()) {
            logger.error(logStr + ", errors:" + ghResponse.getErrors());
            throw new MultiException(ghResponse.getErrors());
        } else {
            logger.info(logStr + ", alternatives: " + ghResponse.getAll().size()
                    + ", distance0: " + ghResponse.getBest().getDistance()
                    + ", weight0: " + ghResponse.getBest().getRouteWeight()
                    + ", time0: " + Math.round(ghResponse.getBest().getTime() / 60000f) + "min"
                    + ", points0: " + ghResponse.getBest().getPoints().getSize()
                    + ", debugInfo: " + ghResponse.getDebugInfo());
            return Response.ok(WebHelper.jsonObject(ghResponse, instructions, calcPoints, enableElevation, pointsEncoded, took)).
                    header("X-GH-Took", "" + Math.round(took * 1000)).
                    type(MediaType.APPLICATION_JSON).
                    build();
        }
    }

    private void enableEdgeBasedIfThereAreCurbsides(List<String> curbsides, GHRequest request) {
        if (!curbsides.isEmpty()) {
            if (!request.getHints().getBool(TURN_COSTS, true))
                throw new IllegalArgumentException("Disabling '" + TURN_COSTS + "' when using '" + CURBSIDE + "' is not allowed");
            if (!request.getHints().getBool(EDGE_BASED, true))
                throw new IllegalArgumentException("Disabling '" + EDGE_BASED + "' when using '" + CURBSIDE + "' is not allowed");
            request.getHints().putObject(EDGE_BASED, true);
        }
    }

    public static void errorIfLegacyParameters(PMap hints) {
        if (hints.has("weighting"))
            throw new IllegalArgumentException("Since you are using the 'profile' parameter, do not use the 'weighting' parameter." +
                    " You used 'weighting=" + hints.getString("weighting", "") + "'");
        if (hints.has("vehicle"))
            throw new IllegalArgumentException("Since you are using the 'profile' parameter, do not use the 'vehicle' parameter." +
                    " You used 'vehicle=" + hints.getString("vehicle", "") + "'");
        if (hints.has("edge_based"))
            throw new IllegalArgumentException("Since you are using the 'profile' parameter, do not use the 'edge_based' parameter." +
                    " You used 'edge_based=" + hints.getBool("edge_based", false) + "'");
        if (hints.has("turn_costs"))
            throw new IllegalArgumentException("Since you are using the 'profile' parameter, do not use the 'turn_costs' parameter." +
                    " You used 'turn_costs=" + hints.getBool("turn_costs", false) + "'");
    }

    private void removeLegacyParameters(GHRequest request) {
        // these parameters should only be used to resolve the profile, but should not be passed to GraphHopper
        request.getHints().remove("weighting");
        request.getHints().remove("vehicle");
        request.getHints().remove("edge_based");
        request.getHints().remove("turn_costs");
    }

    private static Response.ResponseBuilder gpxSuccessResponseBuilder(GHResponse ghRsp, String timeString, String
            trackName, boolean enableElevation, boolean withRoute, boolean withTrack, boolean withWayPoints, String version) {
        if (ghRsp.getAll().size() > 1) {
            throw new IllegalArgumentException("Alternatives are currently not yet supported for GPX");
        }

        long time = timeString != null ? Long.parseLong(timeString) : System.currentTimeMillis();
        InstructionList instructions = ghRsp.getBest().getInstructions();
        return Response.ok(GpxFromInstructions.createGPX(instructions, trackName, time, enableElevation, withRoute, withTrack, withWayPoints, version, instructions.getTr()), "application/gpx+xml").
                header("Content-Disposition", "attachment;filename=" + "GraphHopper.gpx");
    }

    static void initHints(PMap m, MultivaluedMap<String, String> parameterMap) {
        for (Map.Entry<String, List<String>> e : parameterMap.entrySet()) {
            if (e.getValue().size() == 1) {
                m.putObject(Helper.camelCaseToUnderScore(e.getKey()), Helper.toObject(e.getValue().get(0)));
            } else {
                // TODO e.g. 'point' parameter occurs multiple times and we cannot throw an exception here
                //  unknown parameters (hints) should be allowed to be multiparameters, too, or we shouldn't use them for
                //  known parameters either, _or_ known parameters must be filtered before they come to this code point,
                //  _or_ we stop passing unknown parameters alltogether.
                // throw new WebApplicationException(String.format("This query parameter (hint) is not allowed to occur multiple times: %s", e.getKey()));
                // see also #1976
            }
        }
    }
}
