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
import com.graphhopper.http.WebHelper;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.util.Constants;
import com.graphhopper.util.InstructionList;
import com.graphhopper.util.StopWatch;
import com.graphhopper.util.gpx.GpxFromInstructions;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.graphhopper.util.Parameters.Details.PATH_DETAILS;
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
    private final Boolean hasElevation;

    @Inject
    public RouteResource(GraphHopperAPI graphHopper, @Named("hasElevation") Boolean hasElevation) {
        this.graphHopper = graphHopper;
        this.hasElevation = hasElevation;
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, "application/gpx+xml"})
    public Response doGet(
            @Context HttpServletRequest httpReq,
            @Context UriInfo uriInfo,
            @Context ContainerRequestContext rc,
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
            @QueryParam(POINT_HINT) List<String> pointHints,
            @QueryParam(CURBSIDE) List<String> curbsides,
            @QueryParam(SNAP_PREVENTION) List<String> snapPreventions,
            @QueryParam(PATH_DETAILS) List<String> pathDetails,
            @QueryParam("heading") List<Double> favoredHeadings,
            @QueryParam("gpx.route") @DefaultValue("true") boolean withRoute /* default to false for the route part in next API version, see #437 */,
            @QueryParam("gpx.track") @DefaultValue("true") boolean withTrack,
            @QueryParam("gpx.waypoints") @DefaultValue("false") boolean withWayPoints,
            @QueryParam("gpx.trackname") @DefaultValue("GraphHopper Track") String trackName,
            @QueryParam("gpx.millis") String timeString) {

        GHRequest request;
        request = new GHRequest(requestPoints);

        request.getHints()
                .put(CALC_POINTS, calcPoints)
                .put(INSTRUCTIONS, instructions)
                .put(WAY_POINT_MAX_DISTANCE, minPathPrecision);

        GHResponse ghResponse = graphHopper.route(request);

        return Response.ok(WebHelper.jsonObject(ghResponse, instructions, calcPoints, enableElevation, pointsEncoded))
                .build();
    }

}
