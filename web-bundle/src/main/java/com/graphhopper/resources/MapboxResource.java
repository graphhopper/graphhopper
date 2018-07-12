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
import java.util.Set;

import static com.graphhopper.util.Parameters.Routing.*;

/**
 * Provides an endpoint that is compatible with the Mapbox API v5
 *
 * See: https://www.mapbox.com/api-documentation/#directions
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
            @QueryParam("steps") @DefaultValue("false") boolean enableInstructions,
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

            /*
                Mapbox always uses fastest or priority weighting, except for walking, it's shortest
                https://www.mapbox.com/api-documentation/#directions
             */
            final String weighting = "fastest";

            /*
                Currently, the MapboxResponseConverter is pretty limited.
                Therefore, we enforce these values to make sure the client does not receive an unexpected answer.
             */
            if (!geometries.equals("polyline6"))
                throwIllegalArgumentException("Currently, we only support polyline6");
            if (!enableInstructions)
                throwIllegalArgumentException("Currently, you need to enable steps");
            if (!roundaboutExits)
                throwIllegalArgumentException("Roundabout exits have to be enabled right now");
            if (!voiceUnits.equals("metric"))
                throwIllegalArgumentException("Voice units only support metric right now");
            if (!voiceInstructions)
                throwIllegalArgumentException("You need to enable voice instructions right now");
            if (!bannerInstructions)
                throwIllegalArgumentException("You need to enable banner instructions right now");

            double minPathPrecision = 1;
            if (overview.equals("full"))
                minPathPrecision = 0;

            String vehicleStr = convertProfileToGraphHopperVehicleString(profile);
            List<GHPoint> requestPoints = getPointsFromRequest(pathSegment);

            StopWatch sw = new StopWatch().start();

            // TODO: initialization with heading/bearings
            // TODO: how should we use the "continue_straight" parameter? This is analog to pass_through, would require disabling CH

            GHRequest request = new GHRequest(requestPoints);
            request.setVehicle(vehicleStr).
                    setWeighting(weighting).
                    setLocale(localeStr).
                    getHints().
                    put(CALC_POINTS, true).
                    put(INSTRUCTIONS, enableInstructions).
                    put(WAY_POINT_MAX_DISTANCE, minPathPrecision);

            GHResponse ghResponse = graphHopper.route(request);

            float took = sw.stop().getSeconds();
            String infoStr = httpReq.getRemoteAddr() + " " + httpReq.getLocale() + " " + httpReq.getHeader("User-Agent");
            String logStr = httpReq.getQueryString() + " " + infoStr + " " + requestPoints + ", took:"
                    + took + ", " + weighting + ", " + vehicleStr;

            if (ghResponse.hasErrors()) {
                logger.error(logStr + ", errors:" + ghResponse.getErrors());
                // Mapbox specifies 422 return type for input errors
                return Response.status(422).entity(MapboxResponseConverter.convertFromGHResponseError(ghResponse)).
                        header("X-GH-Took", "" + Math.round(took * 1000)).
                        build();
            } else {
                return Response.ok(MapboxResponseConverter.convertFromGHResponse(ghResponse, request.getLocale())).
                        header("X-GH-Took", "" + Math.round(took * 1000)).
                        build();
            }

        } catch (Exception e) {
            logger.error("Exception while processing: ", e);
            throw new MultiException(e);
        }
    }

    private void throwIllegalArgumentException(String exceptionMessage) {
        logger.error(exceptionMessage);
        throw new IllegalArgumentException(exceptionMessage);
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
}
