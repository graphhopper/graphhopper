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
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.util.StopWatch;
import com.graphhopper.util.shapes.GHPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.graphhopper.util.Parameters.Routing.*;

/**
 * Provide an endpoint that is compatible with the Mapbox API
 *
 * @author Robin Boldt
 */
@Path("mapbox/directions/v5/mapbox")
public class MapboxResource {

    private static final Logger logger = LoggerFactory.getLogger(MapboxResource.class);

    private final GraphHopperAPI graphHopper;
    private final Boolean hasElevation;

    @Inject
    public MapboxResource(GraphHopperAPI graphHopper, @Named("hasElevation") Boolean hasElevation) {
        this.graphHopper = graphHopper;
        this.hasElevation = hasElevation;
    }

    @GET
    @Path("/{profile}/{coordinatesArray: .*}")
    @Produces({MediaType.APPLICATION_JSON})
    public Response doGet(
            @Context HttpServletRequest httpReq,
            @Context UriInfo uriInfo,
            @Context ContainerRequestContext rc,
            @QueryParam("steps") @DefaultValue("false") boolean enableSteps,
            @QueryParam("voice_instructions") @DefaultValue("false") boolean voiceInstructions,
            @QueryParam("banner_instructions") @DefaultValue("false") boolean bannerInstructions,
            @QueryParam("roundabout_exits") @DefaultValue("false") boolean roundaboutExits,
            @QueryParam("voice_units") @DefaultValue("metric") String voiceUnits,
            @QueryParam("overview") @DefaultValue("simplified") String overview,
            @QueryParam("geometries") @DefaultValue("polyline") String geometries,
            @QueryParam("language") @DefaultValue("en") String localeStr,
            @QueryParam("heading") List<Double> favoredHeadings,
            @PathParam("profile") String profile,
            @PathParam("coordinatesArray") PathSegment pathSegment) {

        try {

            // Mapbox always uses fastest or priority weighting, except for walking, it's shortest
            // https://www.mapbox.com/api-documentation/#directions
            final String weighting = "fastest";

            if (!geometries.equals("polyline6")) {
                String logStr = "Currently, we only support polyline6";
                logger.error(logStr);
                throw new IllegalArgumentException(logStr);
            }

            if (!enableSteps) {
                String logStr = "Currently, you need to enable steps";
                logger.error(logStr);
                throw new IllegalArgumentException(logStr);
            }

            if (!roundaboutExits) {
                String logStr = "Roundabout exits have to be enabled right now";
                logger.error(logStr);
                throw new IllegalArgumentException(logStr);
            }

            if (!voiceUnits.equals("metric")) {
                String logStr = "Voice units only support metric right now";
                logger.error(logStr);
                throw new IllegalArgumentException(logStr);
            }

            if (!voiceInstructions) {
                String logStr = "You need to enable voice instructions right now";
                logger.error(logStr);
                throw new IllegalArgumentException(logStr);
            }

            if (!bannerInstructions) {
                String logStr = "You need to enable banner instructions right now";
                logger.error(logStr);
                throw new IllegalArgumentException(logStr);
            }

            double minPathPrecision = 1;
            if (overview.equals("full"))
                minPathPrecision = 0.1;

            boolean instructions = enableSteps;

            String vehicleStr = convertProfileToGraphHopperVehicleString(profile);
            List<GHPoint> requestPoints = getPointsFromRequest(pathSegment);

            StopWatch sw = new StopWatch().start();

            // TODO: heading
            GHRequest request = new GHRequest(requestPoints);
            request.setVehicle(vehicleStr).
                    setWeighting(weighting).
                    setLocale(localeStr).
                    getHints().
                    put(CALC_POINTS, true).
                    put(INSTRUCTIONS, instructions).
                    put(WAY_POINT_MAX_DISTANCE, minPathPrecision);

            GHResponse ghResponse = graphHopper.route(request);

            float took = sw.stop().getSeconds();
            String infoStr = httpReq.getRemoteAddr() + " " + httpReq.getLocale() + " " + httpReq.getHeader("User-Agent");
            String logStr = httpReq.getQueryString() + " " + infoStr + " " + requestPoints + ", took:"
                    + took + ", " + weighting + ", " + vehicleStr;

            if (ghResponse.hasErrors()) {
                logger.error(logStr + ", errors:" + ghResponse.getErrors());
                throw new MultiException(ghResponse.getErrors());
            } else {
                return Response.ok(MapboxResponseConverter.convertFromGHResponse(ghResponse, request.getLocale())).
                        header("X-GH-Took", "" + Math.round(took * 1000)).
                        build();
            }

        } catch (Exception e) {
            logger.error("Exception while processing: ", e);
            throw new MultiException(e);
        }


        /*

        StopWatch sw = new StopWatch().start();

        if (requestPoints.isEmpty())
            throw new IllegalArgumentException("You have to pass at least one point");
        if (enableElevation && !hasElevation)
            throw new IllegalArgumentException("Elevation not supported!");
        if (favoredHeadings.size() > 1 && favoredHeadings.size() != requestPoints.size())
            throw new IllegalArgumentException("The number of 'heading' parameters must be <= 1 "
                    + "or equal to the number of points (" + requestPoints.size() + ")");
        if (pointHints.size() > 0 && pointHints.size() != requestPoints.size())
            throw new IllegalArgumentException("If you pass " + POINT_HINT + ", you need to pass a hint for every point, empty hints will be ignored");

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
        request.setVehicle(vehicleStr).
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
            throw new MultiException(ghResponse.getErrors());
        } else {
            logger.info(logStr + ", alternatives: " + ghResponse.getAll().size()
                    + ", distance0: " + ghResponse.getBest().getDistance()
                    + ", time0: " + Math.round(ghResponse.getBest().getTime() / 60000f) + "min"
                    + ", points0: " + ghResponse.getBest().getPoints().getSize()
                    + ", debugInfo: " + ghResponse.getDebugInfo());
            return Response.ok(WebHelper.jsonObject(ghResponse, instructions, calcPoints, enableElevation, pointsEncoded, took)).
                    header("X-GH-Took", "" + Math.round(took * 1000)).
                    build();
        }

        */
    }

    private List<GHPoint> getPointsFromRequest(PathSegment pathSegment) {

        // TODO: Could the map at some point break the order?
        Set<String> pointParams = pathSegment.getMatrixParameters().keySet();
        List<GHPoint> points = new ArrayList<>(pointParams.size() + 1);

        points.add(GHPoint.fromStringLonLat(pathSegment.getPath()));
        for (String pointParam : pointParams) {
            points.add(GHPoint.fromStringLonLat(pointParam));
        }

        return points;
    }

    private String convertProfileToGraphHopperVehicleString(String profile) {
        switch (profile) {
            case "driving":
                // driving-traffic is mapped to regular car as well
            case "driving-traffic":
                return "car";
            case "walking":
                return "foot";
            case "cycling":
                return "bike";
            default:
                throw new IllegalArgumentException("Not supported profile: " + profile);
        }
    }

    private static Response.ResponseBuilder gpxSuccessResponseBuilder(GHResponse ghRsp, String timeString, String
            trackName, boolean enableElevation, boolean withRoute, boolean withTrack, boolean withWayPoints, String version) {
        if (ghRsp.getAll().size() > 1) {
            throw new IllegalArgumentException("Alternatives are currently not yet supported for GPX");
        }

        long time = timeString != null ? Long.parseLong(timeString) : System.currentTimeMillis();
        return Response.ok(ghRsp.getBest().getInstructions().createGPX(trackName, time, enableElevation, withRoute, withTrack, withWayPoints, version), "application/gpx+xml").
                header("Content-Disposition", "attachment;filename=" + "GraphHopper.gpx");
    }

    static void initHints(HintsMap m, MultivaluedMap<String, String> parameterMap) {
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
