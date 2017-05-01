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

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopperAPI;
import com.graphhopper.PathWrapper;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.util.StopWatch;
import com.graphhopper.util.shapes.GHPoint;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.StringWriter;
import java.util.*;

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
public class GraphHopperServlet extends GHBaseServlet {

    @Inject
    private GraphHopperAPI graphHopper;
    @Inject
    private EncodingManager encodingManager;
    @Inject
    private RouteSerializer routeSerializer;
    @Inject
    @Named("hasElevation")
    private boolean hasElevation;

    @Override
    public void doGet(HttpServletRequest httpReq, HttpServletResponse httpRes) throws ServletException, IOException {
        List<GHPoint> requestPoints = getPoints(httpReq, "point");
        GHResponse ghRsp = new GHResponse();

        double minPathPrecision = getDoubleParam(httpReq, WAY_POINT_MAX_DISTANCE, 1d);
        boolean writeGPX = "gpx".equalsIgnoreCase(getParam(httpReq, "type", "json"));
        boolean enableInstructions = writeGPX || getBooleanParam(httpReq, INSTRUCTIONS, true);
        boolean calcPoints = getBooleanParam(httpReq, CALC_POINTS, true);
        boolean enableElevation = getBooleanParam(httpReq, "elevation", false);
        boolean pointsEncoded = getBooleanParam(httpReq, "points_encoded", true);

        String vehicleStr = getParam(httpReq, "vehicle", "car");
        String weighting = getParam(httpReq, "weighting", "fastest");
        String algoStr = getParam(httpReq, "algorithm", "");
        String localeStr = getParam(httpReq, "locale", "en");

        StopWatch sw = new StopWatch().start();

        if (!ghRsp.hasErrors()) {
            try {
                if(requestPoints.isEmpty()){
                    throw new IllegalArgumentException("You have to pass at least one point");
                }
                List<Double> favoredHeadings = Collections.EMPTY_LIST;
                try {
                    favoredHeadings = getDoubleParamList(httpReq, "heading");

                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("heading list in wrong format: " + e.getMessage());
                }

                if (!encodingManager.supports(vehicleStr)) {
                    throw new IllegalArgumentException("Vehicle not supported: " + vehicleStr);
                } else if (enableElevation && !hasElevation) {
                    throw new IllegalArgumentException("Elevation not supported!");
                } else if (favoredHeadings.size() > 1 && favoredHeadings.size() != requestPoints.size()) {
                    throw new IllegalArgumentException("The number of 'heading' parameters must be <= 1 "
                            + "or equal to the number of points (" + requestPoints.size() + ")");
                }

                List<String> pointHints = new ArrayList<String>(Arrays.asList(getParams(httpReq, POINT_HINT)));
                if (pointHints.size() > 0 && pointHints.size() != requestPoints.size()) {
                    throw new IllegalArgumentException("If you pass " + POINT_HINT + ", you need to pass a hint for every point, empty hints will be ignored");
                }

                FlagEncoder algoVehicle = encodingManager.getEncoder(vehicleStr);
                GHRequest request;
                if (favoredHeadings.size() > 0) {
                    // if only one favored heading is specified take as start heading
                    if (favoredHeadings.size() == 1) {
                        List<Double> paddedHeadings = new ArrayList<Double>(Collections.nCopies(requestPoints.size(),
                                Double.NaN));
                        paddedHeadings.set(0, favoredHeadings.get(0));
                        request = new GHRequest(requestPoints, paddedHeadings);
                    } else {
                        request = new GHRequest(requestPoints, favoredHeadings);
                    }
                } else {
                    request = new GHRequest(requestPoints);
                }

                initHints(request.getHints(), httpReq.getParameterMap());
                request.setVehicle(algoVehicle.toString()).
                        setWeighting(weighting).
                        setAlgorithm(algoStr).
                        setLocale(localeStr).
                        setPointHints(pointHints).
                        getHints().
                        put(CALC_POINTS, calcPoints).
                        put(INSTRUCTIONS, enableInstructions).
                        put(WAY_POINT_MAX_DISTANCE, minPathPrecision);

                ghRsp = graphHopper.route(request);
            } catch (IllegalArgumentException ex) {
                ghRsp.addError(ex);
            }
        }

        float took = sw.stop().getSeconds();
        String infoStr = httpReq.getRemoteAddr() + " " + httpReq.getLocale() + " " + httpReq.getHeader("User-Agent");
        String logStr = httpReq.getQueryString() + " " + infoStr + " " + requestPoints + ", took:"
                + took + ", " + algoStr + ", " + weighting + ", " + vehicleStr;
        httpRes.setHeader("X-GH-Took", "" + Math.round(took * 1000));

        int alternatives = ghRsp.getAll().size();
        if (writeGPX && alternatives > 1)
            ghRsp.addError(new IllegalArgumentException("Alternatives are currently not yet supported for GPX"));

        if (ghRsp.hasErrors()) {
            logger.error(logStr + ", errors:" + ghRsp.getErrors());
        } else {
            PathWrapper altRsp0 = ghRsp.getBest();
            logger.info(logStr + ", alternatives: " + alternatives
                    + ", distance0: " + altRsp0.getDistance()
                    + ", time0: " + Math.round(altRsp0.getTime() / 60000f) + "min"
                    + ", points0: " + altRsp0.getPoints().getSize()
                    + ", debugInfo: " + ghRsp.getDebugInfo());
        }

        if (writeGPX) {
            if (ghRsp.hasErrors()) {
                httpRes.setStatus(SC_BAD_REQUEST);
                httpRes.getWriter().append(errorsToXML(ghRsp.getErrors()));
            } else {
                // no error => we can now safely call getFirst
                String xml = createGPXString(httpReq, httpRes, ghRsp.getBest());
                writeResponse(httpRes, xml);
            }
        } else {
            Map<String, Object> map = routeSerializer.toJSON(ghRsp, calcPoints, pointsEncoded,
                    enableElevation, enableInstructions);

            Object infoMap = map.get("info");
            if (infoMap != null)
                ((Map) infoMap).put("took", Math.round(took * 1000));

            if (ghRsp.hasErrors())
                writeJsonError(httpRes, SC_BAD_REQUEST, jsonNodeFactory.pojoNode(map));
            else {
                writeJson(httpReq, httpRes, jsonNodeFactory.pojoNode(map));
            }
        }
    }

    protected String createGPXString(HttpServletRequest req, HttpServletResponse res, PathWrapper rsp) {
        boolean includeElevation = getBooleanParam(req, "elevation", false);
        // default to false for the route part in next API version, see #437
        boolean withRoute = getBooleanParam(req, "gpx.route", true);
        boolean withTrack = getBooleanParam(req, "gpx.track", true);
        boolean withWayPoints = getBooleanParam(req, "gpx.waypoints", false);
        res.setCharacterEncoding("UTF-8");
        if ("application/xml".equals(req.getContentType()))
            res.setContentType("application/xml");
        else
            res.setContentType("application/gpx+xml");

        String trackName = getParam(req, "trackname", "GraphHopper Track");
        res.setHeader("Content-Disposition", "attachment;filename=" + "GraphHopper.gpx");
        long time = getLongParam(req, "millis", System.currentTimeMillis());
        return rsp.getInstructions().createGPX(trackName, time, includeElevation, withRoute, withTrack, withWayPoints);
    }

    protected String errorsToXML(Collection<Throwable> list) {
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
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(doc), new StreamResult(writer));
            return writer.toString();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    protected List<GHPoint> getPoints(HttpServletRequest req, String key) {
        String[] pointsAsStr = getParams(req, key);
        final List<GHPoint> infoPoints = new ArrayList<GHPoint>(pointsAsStr.length);
        for (String str : pointsAsStr) {
            String[] fromStrs = str.split(",");
            if (fromStrs.length == 2) {
                GHPoint point = GHPoint.parse(str);
                if (point != null)
                    infoPoints.add(point);
            }
        }

        return infoPoints;
    }
}
