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
package com.graphhopper.http;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphhopper.GHResponse;
import com.graphhopper.PathWrapper;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PointList;
import com.graphhopper.util.exceptions.GHException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.net.URLEncoder;
import java.text.NumberFormat;
import java.util.*;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;

/**
 * Code which handles polyline encoding and other web stuff.
 * <p>
 * The necessary information for polyline encoding is in this answer:
 * http://stackoverflow.com/a/24510799/194609 with a link to official Java sources as well as to a
 * good explanation.
 * <p>
 *
 * @author Peter Karich
 */
public class WebHelper {
    public static String encodeURL(String str) {
        try {
            return URLEncoder.encode(str, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static PointList decodePolyline(String encoded, int initCap, boolean is3D) {
        PointList poly = new PointList(initCap, is3D);
        int index = 0;
        int len = encoded.length();
        int lat = 0, lng = 0, ele = 0;
        while (index < len) {
            // latitude
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int deltaLatitude = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += deltaLatitude;

            // longitute
            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int deltaLongitude = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += deltaLongitude;

            if (is3D) {
                // elevation
                shift = 0;
                result = 0;
                do {
                    b = encoded.charAt(index++) - 63;
                    result |= (b & 0x1f) << shift;
                    shift += 5;
                } while (b >= 0x20);
                int deltaElevation = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
                ele += deltaElevation;
                poly.add((double) lat / 1e5, (double) lng / 1e5, (double) ele / 100);
            } else
                poly.add((double) lat / 1e5, (double) lng / 1e5);
        }
        return poly;
    }

    public static String encodePolyline(PointList poly) {
        if (poly.isEmpty())
            return "";

        return encodePolyline(poly, poly.is3D());
    }

    public static String encodePolyline(PointList poly, boolean includeElevation) {
        StringBuilder sb = new StringBuilder();
        int size = poly.getSize();
        int prevLat = 0;
        int prevLon = 0;
        int prevEle = 0;
        for (int i = 0; i < size; i++) {
            int num = (int) Math.floor(poly.getLatitude(i) * 1e5);
            encodeNumber(sb, num - prevLat);
            prevLat = num;
            num = (int) Math.floor(poly.getLongitude(i) * 1e5);
            encodeNumber(sb, num - prevLon);
            prevLon = num;
            if (includeElevation) {
                num = (int) Math.floor(poly.getElevation(i) * 100);
                encodeNumber(sb, num - prevEle);
                prevEle = num;
            }
        }
        return sb.toString();
    }

    private static void encodeNumber(StringBuilder sb, int num) {
        num = num << 1;
        if (num < 0) {
            num = ~num;
        }
        while (num >= 0x20) {
            int nextValue = (0x20 | (num & 0x1f)) + 63;
            sb.append((char) (nextValue));
            num >>= 5;
        }
        num += 63;
        sb.append((char) (num));
    }

    public static Response.ResponseBuilder gpxSuccessResponseBuilder(GHResponse ghRsp, String timeString, String trackName, boolean enableElevation, boolean withRoute, boolean withTrack, boolean withWayPoints) {
        if (ghRsp.getAll().size() > 1) {
            throw new WebApplicationException("Alternatives are currently not yet supported for GPX");
        }

        long time = timeString != null ? Long.parseLong(timeString) : System.currentTimeMillis();
        return Response.ok(ghRsp.getBest().getInstructions().createGPX(trackName, time, enableElevation, withRoute, withTrack, withWayPoints), "application/gpx+xml").
                header("Content-Disposition", "attachment;filename=" + "GraphHopper.gpx");
    }

    private static Response xmlErrorResponse(Collection<Throwable> list) {
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

    public static ObjectNode jsonObject(GHResponse ghRsp, boolean enableInstructions, boolean calcPoints, boolean enableElevation, boolean pointsEncoded, float took) {
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
            jsonPath.put("transfers", ar.getNumChanges());
            if (!ar.getDescription().isEmpty()) {
                jsonPath.putPOJO("description", ar.getDescription());
            }
            if (calcPoints) {
                jsonPath.put("points_encoded", pointsEncoded);
                if (ar.getPoints().getSize() >= 2) {
                    jsonPath.putPOJO("bbox", ar.calcBBox2D());
                }
                jsonPath.putPOJO("points", pointsEncoded ? encodePolyline(ar.getPoints(), enableElevation) : ar.getPoints().toLineString(enableElevation));
                if (enableInstructions) {
                    jsonPath.putPOJO("instructions", ar.getInstructions());
                }
                jsonPath.putPOJO("legs", ar.getLegs());
                jsonPath.putPOJO("details", ar.getPathDetails());
                jsonPath.put("ascend", ar.getAscend());
                jsonPath.put("descend", ar.getDescend());
            }
            jsonPath.putPOJO("snapped_waypoints", pointsEncoded ? encodePolyline(ar.getWaypoints(), enableElevation) : ar.getWaypoints().toLineString(enableElevation));
            if (ar.getFare() != null) {
                jsonPath.put("fare", NumberFormat.getCurrencyInstance(Locale.ROOT).format(ar.getFare()));
            }
        }
        return json;
    }

    public static Response errorResponse(List<Throwable> t, boolean writeGPX) {
        if (writeGPX) {
            return xmlErrorResponse(t);
        } else {
            return jsonErrorResponse(t);
        }
    }

    public static Response errorResponse(Throwable t, boolean writeGPX) {
        return errorResponse(Collections.singletonList(t), writeGPX);
    }

    private static Response jsonErrorResponse(List<Throwable> errors) {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
        json.put("message", getMessage(errors.get(0)));
        ArrayNode errorHintList = json.putArray("hints");
        for (Throwable t : errors) {
            ObjectNode error = errorHintList.addObject();
            error.put("message", getMessage(t));
            error.put("details", t.getClass().getName());
            if (t instanceof GHException) {
                ((GHException) t).getDetails().forEach(error::putPOJO);
            }
        }
        return Response.status(SC_BAD_REQUEST).entity(json).build();
    }

    private static String getMessage(Throwable t) {
        if (t.getMessage() == null)
            return t.getClass().getSimpleName();
        else
            return t.getMessage();
    }

    public static void initHints(HintsMap m, MultivaluedMap<String, String> parameterMap) {
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
