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

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphhopper.*;
import com.graphhopper.http.WebHelper;
import com.graphhopper.http.api.JsonErrorEntity;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.util.Helper;
import com.graphhopper.util.StopWatch;
import com.graphhopper.util.exceptions.GHException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.text.NumberFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static com.graphhopper.util.Parameters.Routing.*;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;

/**
 * Servlet to use GraphHopper in a remote client application like mobile or browser. Note: If type
 * is json it returns the points in GeoJson format (longitude,latitude) unlike the format "lat,lon"
 * used otherwise. See the full API response format in docs/web/api-doc.md
 * <p>
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
            @ValidGHRequest @BeanParam GHRequest request,
            @Context HttpServletRequest httpReq,
            @Context UriInfo uriInfo,
            @QueryParam("type") @DefaultValue("json") String type,
            @QueryParam("elevation") @DefaultValue("false") boolean enableElevation,
            @QueryParam("points_encoded") @DefaultValue("true") boolean pointsEncoded,
            @QueryParam("gpx.route") @DefaultValue("true") boolean withRoute /* default to false for the route part in next API version, see #437 */,
            @QueryParam("gpx.track") @DefaultValue("true") boolean withTrack,
            @QueryParam("gpx.waypoints") @DefaultValue("false") boolean withWayPoints,
            @QueryParam("trackname") @DefaultValue("GraphHopper Track") String trackName,
            @QueryParam("millis") String timeString) {
        boolean writeGPX = "gpx".equalsIgnoreCase(type);
        if (writeGPX) {
            request.getHints().put(INSTRUCTIONS, true);
        }

        StopWatch sw = new StopWatch().start();

        if (!encodingManager.supports(request.getVehicle())) {
            throw new WebApplicationException(errorResponse(new IllegalArgumentException("Vehicle not supported: " + request.getVehicle()), writeGPX));
        } else if (enableElevation && !hasElevation) {
            throw new WebApplicationException(errorResponse(new IllegalArgumentException("Elevation not supported!"), writeGPX));
        }

        GHResponse ghResponse = graphHopper.route(request);

        // TODO: Request logging and timing should perhaps be done somewhere outside
        float took = sw.stop().getSeconds();
        String infoStr = httpReq.getRemoteAddr() + " " + httpReq.getLocale() + " " + httpReq.getHeader("User-Agent");
        String logStr = httpReq.getQueryString() + " " + infoStr + " " + request.getPoints() + ", took:"
                + took + ", " + request.getAlgorithm() + ", " + request.getWeighting() + ", " + request.getVehicle();

        if (ghResponse.hasErrors()) {
            logger.error(logStr + ", errors:" + ghResponse.getErrors());
            throw new WebApplicationException(errorResponse(ghResponse.getErrors(), writeGPX));
        } else {
            logger.info(logStr + ", alternatives: " + ghResponse.getAll().size()
                    + ", distance0: " + ghResponse.getBest().getDistance()
                    + ", time0: " + Math.round(ghResponse.getBest().getTime() / 60000f) + "min"
                    + ", points0: " + ghResponse.getBest().getPoints().getSize()
                    + ", debugInfo: " + ghResponse.getDebugInfo());
            return Response.fromResponse(writeGPX ?
                    gpxSuccessResponse(ghResponse, timeString, trackName, enableElevation, withRoute, withTrack, withWayPoints) :
                    jsonSuccessResponse(ghResponse, request.getHints().getBool(INSTRUCTIONS, true), request.getHints().getBool(CALC_POINTS, true), enableElevation, pointsEncoded, took))
                    .header("X-GH-Took", "" + Math.round(took * 1000))
                    .build();
        }
    }

    private Response gpxSuccessResponse(GHResponse ghRsp, String timeString, String trackName, boolean enableElevation, boolean withRoute, boolean withTrack, boolean withWayPoints) {
        if (ghRsp.getAll().size() > 1) {
            throw new WebApplicationException("Alternatives are currently not yet supported for GPX");
        }

        long time = timeString != null ? Long.parseLong(timeString) : System.currentTimeMillis();
        return Response.ok(ghRsp.getBest().getInstructions().createGPX(trackName, time, enableElevation, withRoute, withTrack, withWayPoints), "application/gpx+xml").header("Content-Disposition", "attachment;filename=" + "GraphHopper.gpx").build();
    }

    private Response xmlErrorResponse(Collection<Throwable> list) {
        if (list.isEmpty())
            throw new RuntimeException("errorsToXML should not be called with an empty list");

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.newDocument();
            Element gpxElement = doc.createElement("gpx");
            gpxElement.setAttribute("creator", "GraphHopper");
            gpxElement.setAttribute("version", "1.1");
            doc.appendChild(gpxElement);

            Element mdElement = doc.createElement("metadata");
            gpxElement.appendChild(mdElement);

            Element extensionsElement = doc.createElement("extensions");
            mdElement.appendChild(extensionsElement);

            Element messageElement = doc.createElement("message");
            extensionsElement.appendChild(messageElement);
            messageElement.setTextContent(list.iterator().next().getMessage());

            Element hintsElement = doc.createElement("hints");
            extensionsElement.appendChild(hintsElement);

            for (Throwable t : list) {
                Element error = doc.createElement("error");
                hintsElement.appendChild(error);
                error.setAttribute("message", t.getMessage());
                error.setAttribute("details", t.getClass().getName());
            }
            return Response.status(SC_BAD_REQUEST).entity(doc).build();
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    // TODO: I think the only thing requiring this manual serialization is the rounding
    // and the optional point-encoding. Find out how to do that properly with Jackson and then delete this.
    private Response jsonSuccessResponse(GHResponse ghRsp, boolean enableInstructions, boolean calcPoints, boolean enableElevation, boolean pointsEncoded, float took) {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
        json.putPOJO("hints", ghRsp.getHints().toMap());
        // If you replace GraphHopper with your own brand name, this is fine.
        // Still it would be highly appreciated if you mention us in your about page!
        final ObjectNode info = json.putObject("info");
        info.putArray("copyrights")
                .add("GraphHopper")
                .add("OpenStreetMap contributors");
        info.put("took", Math.round(took * 1000));
        ArrayNode jsonPathList = json.putArray("paths");
        for (PathWrapper ar : ghRsp.getAll()) {
            ObjectNode jsonPath = jsonPathList.addObject();
            jsonPath.put("distance", Helper.round(ar.getDistance(), 3));
            jsonPath.put("weight", Helper.round6(ar.getRouteWeight()));
            jsonPath.put("time", ar.getTime());
            if (!ar.getDescription().isEmpty()) {
                jsonPath.putPOJO("description", ar.getDescription());
            }
            if (calcPoints) {
                jsonPath.put("points_encoded", pointsEncoded);
                if (ar.getPoints().getSize() >= 2) {
                    jsonPath.putPOJO("bbox", ar.calcBBox2D());
                }
                jsonPath.putPOJO("points", pointsEncoded ? WebHelper.encodePolyline(ar.getPoints(), enableElevation) : ar.getPoints().toLineString(enableElevation));
                if (enableInstructions) {
                    jsonPath.putPOJO("instructions", ar.getInstructions());
                }
                jsonPath.putPOJO("details", ar.getPathDetails());
                jsonPath.put("ascend", ar.getAscend());
                jsonPath.put("descend", ar.getDescend());
            }
            jsonPath.putPOJO("snapped_waypoints", pointsEncoded ? WebHelper.encodePolyline(ar.getWaypoints(), enableElevation) : ar.getWaypoints().toLineString(enableElevation));
        }
        return Response.ok(json).build();
    }

    private Response errorResponse(List<Throwable> t, boolean writeGPX) {
        if (writeGPX) {
            return xmlErrorResponse(t);
        } else {
            return Response.status(SC_BAD_REQUEST).entity(new JsonErrorEntity(t)).build();
        }
    }

    private Response errorResponse(Throwable t, boolean writeGPX) {
        return errorResponse(Collections.singletonList(t), writeGPX);
    }

}
