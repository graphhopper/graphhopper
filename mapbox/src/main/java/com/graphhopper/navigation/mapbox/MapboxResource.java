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
package com.graphhopper.navigation.mapbox;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopperAPI;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.StopWatch;
import com.graphhopper.util.TranslationMap;
import com.graphhopper.util.shapes.GHPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    private final TranslationMap translationMap;
    private static final TranslationMap mapboxResponseConverterTranslationMap = new MapboxResponseConverterTranslationMap().doImport();

    @Inject
    public MapboxResource(GraphHopperAPI graphHopper, TranslationMap translationMap) {
        this.graphHopper = graphHopper;
        this.translationMap = translationMap;
    }

    @GET
    @Path("/{profile}/{coordinatesArray : .+}")
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
            @QueryParam("bearings") @DefaultValue("") String bearings,
            @QueryParam("language") @DefaultValue("en") String localeStr,
            @PathParam("profile") String profile) {

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
            throw new IllegalArgumentException("Currently, we only support polyline6");
        if (!enableInstructions)
            throw new IllegalArgumentException("Currently, you need to enable steps");
        if (!roundaboutExits)
            throw new IllegalArgumentException("Roundabout exits have to be enabled right now");
        if (!voiceUnits.equals("metric"))
            throw new IllegalArgumentException("Voice units only support metric right now");
        if (!voiceInstructions)
            throw new IllegalArgumentException("You need to enable voice instructions right now");
        if (!bannerInstructions)
            throw new IllegalArgumentException("You need to enable banner instructions right now");

        double minPathPrecision = 1;
        if (overview.equals("full"))
            minPathPrecision = 0;

        String vehicleStr = convertProfileToGraphHopperVehicleString(profile);
        List<GHPoint> requestPoints = getPointsFromRequest(httpReq, profile);

        List<Double> favoredHeadings = getBearing(bearings);
        if (favoredHeadings.size() > 0 && favoredHeadings.size() != requestPoints.size()) {
            throw new IllegalArgumentException("Number of bearings and waypoints did not match");
        }

        StopWatch sw = new StopWatch().start();

        GHRequest request;
        if (favoredHeadings.size() > 0) {
            request = new GHRequest(requestPoints, favoredHeadings);
        } else {
            request = new GHRequest(requestPoints);
        }

        request.setVehicle(vehicleStr).
                setWeighting(weighting).
                setLocale(localeStr).
                getHints().
                put(CALC_POINTS, true).
                put(INSTRUCTIONS, enableInstructions).
                put(WAY_POINT_MAX_DISTANCE, minPathPrecision).
                put(Parameters.CH.DISABLE, true).
                put(Parameters.Routing.PASS_THROUGH, false);

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
            return Response.ok(MapboxResponseConverter.convertFromGHResponse(ghResponse, translationMap, mapboxResponseConverterTranslationMap, request.getLocale())).
                    header("X-GH-Took", "" + Math.round(took * 1000)).
                    build();
        }
    }

    /**
     * This method is parsing the request URL String. Unfortunately it seems that there is no better options right now.
     * See: https://stackoverflow.com/q/51420380/1548788
     *
     * The url looks like: ".../{profile}/1.522438,42.504606;1.527209,42.504776;1.526113,42.505144;1.527218,42.50529?.."
     */
    private List<GHPoint> getPointsFromRequest(HttpServletRequest httpServletRequest, String profile) {

        String url = httpServletRequest.getRequestURI();
        url = url.replaceFirst("/mapbox/directions/v5/mapbox/" + profile + "/", "");
        url = url.replaceAll("\\?[*]", "");

        String[] pointStrings = url.split(";");

        List<GHPoint> points = new ArrayList<>(pointStrings.length);
        for (int i = 0; i < pointStrings.length; i++) {
            points.add(GHPoint.fromStringLonLat(pointStrings[i]));
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

    static List<Double> getBearing(String bearingString) {
        if (bearingString == null || bearingString.isEmpty())
            return Collections.EMPTY_LIST;

        String[] bearingArray = bearingString.split(";", -1);
        List<Double> bearings = new ArrayList<>(bearingArray.length);

        for (int i = 0; i < bearingArray.length; i++) {
            String singleBearing = bearingArray[i];
            if (singleBearing.isEmpty()) {
                bearings.add(Double.NaN);
            } else {
                if (!singleBearing.contains(",")) {
                    throw new IllegalArgumentException("You passed an invalid bearings parameter " + bearingString);
                }
                String[] singleBearingArray = singleBearing.split(",");
                try {
                    bearings.add(Double.parseDouble(singleBearingArray[0]));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("You passed an invalid bearings parameter " + bearingString);
                }
            }
        }
        return bearings;
    }
}
