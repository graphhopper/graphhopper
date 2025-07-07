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
package com.graphhopper.navigation;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.routing.ev.MaxSpeed;
import com.graphhopper.util.Helper;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.StopWatch;
import com.graphhopper.util.TranslationMap;
import com.graphhopper.util.shapes.GHPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.*;

import static com.graphhopper.util.Parameters.Details.*;
import static com.graphhopper.util.Parameters.Routing.*;

/**
 * Provides an endpoint that is consumable with the Mapbox Navigation SDK. The Mapbox Navigation SDK consumes json
 * responses that follow the specification of the Mapbox API v5.
 * <p>
 * See: <a href="https://docs.mapbox.com/api/navigation/directions/">mapbox docs</a>.
 * <p>
 * The baseurl of this endpoint is: [YOUR-IP/HOST]/navigate
 * The version of this endpoint is: v5
 * The user of this endpoint is: gh
 *
 * @author Robin Boldt
 */
@Path("navigate")
public class NavigateResource {

    private static final Logger logger = LoggerFactory.getLogger(NavigateResource.class);

    private final GraphHopper graphHopper;
    private final TranslationMap translationMap;
    private final Map<String, String> resolverMap;

    @Inject
    public NavigateResource(GraphHopper graphHopper, TranslationMap translationMap, GraphHopperConfig config) {
        this.graphHopper = graphHopper;
        resolverMap = config.asPMap().getObject("profiles_mapbox", new HashMap<>());
        if (resolverMap.isEmpty()) {
            resolverMap.put("driving", "car");
            // driving-traffic is mapped to regular car as well
            resolverMap.put("driving-traffic", "car");
            resolverMap.put("walking", "foot");
            resolverMap.put("cycling", "bike");
        }
        this.translationMap = translationMap;
    }

    @GET
    @Path("/directions/v5/gh/{profile}/{coordinatesArray : .+}")
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
            @PathParam("profile") String mapboxProfile) {

        StopWatch sw = new StopWatch().start();

        // Make sure the client does not receive an unexpected answer as the NavigateResponseConverter is limited
        if (!geometries.equals("polyline6"))
            throw new IllegalArgumentException("Currently, we only support polyline6");
        if (!enableInstructions)
            throw new IllegalArgumentException("Currently, you need to enable steps");
        if (!roundaboutExits)
            throw new IllegalArgumentException("Roundabout exits have to be enabled right now");
        if (!voiceInstructions)
            throw new IllegalArgumentException("You need to enable voice instructions right now");
        if (!bannerInstructions)
            throw new IllegalArgumentException("You need to enable banner instructions right now");

        double minPathPrecision = overview.equals("full") ? 0 : 1;
        String ghProfile = resolverMap.getOrDefault(mapboxProfile, mapboxProfile);
        List<GHPoint> requestPoints = getPointsFromRequest(httpReq, mapboxProfile);

        List<Double> favoredHeadings = getBearing(bearings);
        if (!favoredHeadings.isEmpty() && favoredHeadings.size() != requestPoints.size()) {
            throw new IllegalArgumentException("Number of bearings and waypoints did not match");
        }

        GHResponse ghResponse = calcRouteForGET(favoredHeadings, requestPoints, ghProfile, localeStr, enableInstructions, minPathPrecision);

        // Only do this, when there are more than 2 points, otherwise we use alternative routes
        if (!ghResponse.hasErrors() && !favoredHeadings.isEmpty()) {
            GHResponse noHeadingResponse = calcRouteForGET(Collections.emptyList(), requestPoints, ghProfile, localeStr, enableInstructions, minPathPrecision);
            if (ghResponse.getBest().getDistance() != noHeadingResponse.getBest().getDistance()) {
                ghResponse.getAll().add(noHeadingResponse.getBest());
            }
        }

        float took = sw.stop().getSeconds();
        String infoStr = httpReq.getRemoteAddr() + " " + httpReq.getLocale() + " " + httpReq.getHeader("User-Agent");
        String logStr = httpReq.getQueryString() + " " + infoStr + " " + requestPoints + ", took:"
                + took + ", " + ghProfile;

        if (ghResponse.hasErrors()) {
            logger.error(logStr + ", errors:" + ghResponse.getErrors());
            // Mapbox specifies 422 return type for input errors
            return Response.status(422).entity(NavigateResponseConverter.convertFromGHResponseError(ghResponse)).
                    header("X-GH-Took", "" + Math.round(took * 1000)).
                    build();
        } else {
            DistanceUtils.Unit unit = voiceUnits.equals("metric") ? DistanceUtils.Unit.METRIC : DistanceUtils.Unit.IMPERIAL;
            Locale locale = Helper.getLocale(localeStr);
            DistanceConfig config = new DistanceConfig(unit, translationMap, locale, graphHopper.getProfile(ghProfile));
            logger.info(logStr);
            return Response.ok(NavigateResponseConverter.convertFromGHResponse(ghResponse, translationMap, locale, config)).
                    header("X-GH-Took", "" + Math.round(took * 1000)).
                    build();
        }
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response doPost(@NotNull GHRequest request, @Context HttpServletRequest httpReq) {
        StopWatch sw = new StopWatch().start();

        // do not use routing.snap_preventions_default here as they are not always good for navigation

        // request is a GraphHopper so reject mapbox parameters
        if (request.getHints().has("geometries"))
            throw new IllegalArgumentException("Do not set 'geometries'. Per default it is 'polyline6'.");
        if (request.getHints().has("steps"))
            throw new IllegalArgumentException("Do not set 'steps'. Per default it is true.");
        if (request.getHints().has("roundabout_exits"))
            throw new IllegalArgumentException("Do not set 'roundabout_exits'. Per default it is true.");
        if (request.getHints().has("voice_instructions"))
            throw new IllegalArgumentException("Do not set 'voice_instructions'. Per default it is true.");
        if (request.getHints().has("banner_instructions"))
            throw new IllegalArgumentException("Do not set 'banner_instructions'. Per default it is true.");
        if (request.getHints().has("elevation"))
            throw new IllegalArgumentException("Do not set 'elevation'. Per default it is false.");
        if (request.getHints().has("overview"))
            throw new IllegalArgumentException("Do not set 'overview'. Per default it is 'full'.");
        if (request.getHints().has("language"))
            throw new IllegalArgumentException("Instead of 'language' use 'locale'. Per default it is 'en'.");
        if (request.getHints().has("points_encoded"))
            throw new IllegalArgumentException("Do not set 'points_encoded'. Per default it is true.");
        if (request.getHints().has("points_encoded_multiplier"))
            throw new IllegalArgumentException("Do not set 'points_encoded_multiplier'. Per default it is 1e6.");
        if (!request.getHints().getString("type", "").equals("mapbox"))
            throw new IllegalArgumentException("Currently type=mapbox required.");

        if (request.getPathDetails().isEmpty()) {
            if (graphHopper.getEncodingManager().hasEncodedValue(MaxSpeed.KEY))
                request.setPathDetails(List.of(INTERSECTION, MaxSpeed.KEY, DISTANCE, TIME, AVERAGE_SPEED));
            else
                request.setPathDetails(List.of(INTERSECTION));
        }

        GHResponse ghResponse = graphHopper.route(request);

        double took = sw.stop().getMillisDouble();
        String infoStr = httpReq.getRemoteAddr() + " " + httpReq.getLocale() + " " + httpReq.getHeader("User-Agent");
        String logStr = infoStr + " " + request.getPoints().size() + ", took: "
                + String.format("%.1f", took) + " ms, algo: " + request.getAlgorithm() + ", profile: " + request.getProfile()
                + ", custom_model: " + request.getCustomModel();

        if (ghResponse.hasErrors()) {
            logger.error(logStr + ", errors:" + ghResponse.getErrors());
            // TODO Mapbox specifies 422 return type for input errors
            return Response.status(422).entity(NavigateResponseConverter.convertFromGHResponseError(ghResponse)).
                    header("X-GH-Took", "" + Math.round(took * 1000)).
                    build();
        } else {
            DistanceUtils.Unit unit;
            if (request.getHints().getString("voice_units", "metric").equals("metric")) {
                unit = DistanceUtils.Unit.METRIC;
            } else {
                unit = DistanceUtils.Unit.IMPERIAL;
            }

            DistanceConfig config = new DistanceConfig(unit, translationMap, request.getLocale(), graphHopper.getProfile(request.getProfile()));
            logger.info(logStr);
            return Response.ok(NavigateResponseConverter.convertFromGHResponse(ghResponse, translationMap, request.getLocale(), config)).
                    header("X-GH-Took", "" + Math.round(took * 1000)).
                    build();
        }
    }

    private GHResponse calcRouteForGET(List<Double> headings, List<GHPoint> requestPoints, String profileStr,
                                       String localeStr, boolean enableInstructions, double minPathPrecision) {
        GHRequest request = new GHRequest(requestPoints);
        if (!headings.isEmpty())
            request.setHeadings(headings);

        request.setProfile(profileStr).
                setLocale(localeStr).
                // We force the intersection details here as we cannot easily add this to the URL
                setPathDetails(List.of(INTERSECTION)).
                putHint(CALC_POINTS, true).
                putHint(INSTRUCTIONS, enableInstructions).
                putHint(WAY_POINT_MAX_DISTANCE, minPathPrecision);

        if (requestPoints.size() > 2 || !headings.isEmpty()) {
            request.putHint(Parameters.CH.DISABLE, true).
                    putHint(Parameters.Routing.PASS_THROUGH, true);
        }

        return graphHopper.route(request);
    }

    /**
     * This method is parsing the request URL String. Unfortunately it seems that there is no better options right now.
     * See: <a href="https://stackoverflow.com/q/51420380/1548788">...</a>
     * <p>
     * The url looks like: ".../{profile}/1.522438,42.504606;1.527209,42.504776;1.526113,42.505144;1.527218,42.50529?.."
     */
    private List<GHPoint> getPointsFromRequest(HttpServletRequest httpServletRequest, String profile) {
        String url = httpServletRequest.getRequestURI();
        String urlStart = "/navigate/directions/v5/gh/" + profile + "/";
        if (!url.startsWith(urlStart)) throw new IllegalArgumentException("Incorrect URL " + url);
        url = url.substring(urlStart.length());
        String[] pointStrings = url.split(";");
        List<GHPoint> points = new ArrayList<>(pointStrings.length);
        for (String pointString : pointStrings) {
            points.add(GHPoint.fromStringLonLat(pointString));
        }

        return points;
    }

    static List<Double> getBearing(String bearingString) {
        if (bearingString == null || bearingString.isEmpty())
            return Collections.emptyList();

        String[] bearingArray = bearingString.split(";", -1);
        List<Double> bearings = new ArrayList<>(bearingArray.length);

        for (String singleBearing : bearingArray) {
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
