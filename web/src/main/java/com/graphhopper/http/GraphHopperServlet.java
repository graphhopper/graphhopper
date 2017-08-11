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
import com.graphhopper.GraphHopper;
import com.graphhopper.PathWrapper;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PointList;
import com.graphhopper.util.StopWatch;
import com.graphhopper.util.shapes.GHPoint;
import com.graphhopper.util.shapes.GHPoint3D;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.inject.Inject;
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
import java.util.Base64;
import java.util.List;
import java.util.Map.Entry;

// for testing
import java.io.File;
import java.io.FileInputStream;
import java.io.BufferedInputStream;

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
    private GraphHopper hopper;
    @Inject
    private RouteSerializer routeSerializer;

    public enum RerouteResult {
        NONE, TRAFFIC, CLOSURE
    }

    // test image
    private static String testClosureBase64;

    @Override
    public void doGet(HttpServletRequest httpReq, HttpServletResponse httpRes) throws ServletException, IOException {
        List<GHPoint> requestPoints = getPoints(httpReq, "point");
        GHResponse ghRsp = new GHResponse();

        // we can reduce the path length based on the maximum differences to the original coordinates
        double minPathPrecision = getDoubleParam(httpReq, "way_point_max_distance", 1d);
        boolean writeGPX = "gpx".equalsIgnoreCase(getParam(httpReq, "type", "json"));
        boolean enableInstructions = writeGPX || getBooleanParam(httpReq, "instructions", true);
        boolean calcPoints = getBooleanParam(httpReq, "calc_points", true);
        boolean enableElevation = getBooleanParam(httpReq, "elevation", false);
        boolean pointsEncoded = getBooleanParam(httpReq, "points_encoded", true);

        PointList currentRoutePoints = getCurrentRoutePoints(httpReq, "current_route_points", null);
        boolean rerouteRequested = currentRoutePoints != null;

        boolean eventImageRequested = getBooleanParam(httpReq, "event_image", false);

        String vehicleStr = getParam(httpReq, "vehicle", "car");
        String weighting = getParam(httpReq, "weighting", "fastest");
        String algoStr = getParam(httpReq, "algorithm", "");
        String localeStr = getParam(httpReq, "locale", "en");

        StopWatch sw = new StopWatch().start();

        if (this.testClosureBase64 == null) {
            try {
                this.testClosureBase64 = Base64.getEncoder().encodeToString(loadFileAsBytesArray("../graphhopper/test_closure_image.jpg"));
            }
            catch (Exception e) {
                logger.error(e.getMessage());
            }
        }

        if (rerouteRequested && requestPoints.size() > 2) {
            ghRsp.addError(new IllegalArgumentException("Rerouting with multiple waypoints is currently not yet supported"));
        }

        if (!ghRsp.hasErrors()) {
            try {
                List<Double> favoredHeadings = Collections.EMPTY_LIST;
                try {
                    favoredHeadings = getDoubleParamList(httpReq, "heading");

                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("heading list in from format: " + e.getMessage());
                }

                if (!hopper.getEncodingManager().supports(vehicleStr)) {
                    throw new IllegalArgumentException("Vehicle not supported: " + vehicleStr);
                } else if (enableElevation && !hopper.hasElevation()) {
                    throw new IllegalArgumentException("Elevation not supported!");
                } else if (favoredHeadings.size() > 1 && favoredHeadings.size() != requestPoints.size()) {
                    throw new IllegalArgumentException("The number of 'heading' parameters must be <= 1 "
                            + "or equal to the number of points (" + requestPoints.size() + ")");
                }

                FlagEncoder algoVehicle = hopper.getEncodingManager().getEncoder(vehicleStr);
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

                initHints(request, httpReq.getParameterMap());
                request.setVehicle(algoVehicle.toString()).
                        setWeighting(weighting).
                        setAlgorithm(algoStr).
                        setLocale(localeStr).
                        getHints().
                        put("calcPoints", calcPoints).
                        put("instructions", enableInstructions).
                        put("wayPointMaxDistance", minPathPrecision);

                ghRsp = hopper.route(request);
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

        if (rerouteRequested && alternatives > 1) {
            ghRsp.addError(new IllegalArgumentException("Alternatives and rerouting is currently not yet supported"));
        }

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

        RerouteResult rerouteResult = RerouteResult.NONE;


        // clip the current route against the origin (so it doesn't include already traversed portion of the route)
        if (rerouteRequested) {
            DistanceCalc distCalc = Helper.DIST_EARTH;
            GHPoint origin = ghRsp.getBest().getWaypoints().toGHPoint(0);
            int minIndex = -1;
            double minDist = Double.MAX_VALUE;

            for (int i = 1; i < currentRoutePoints.size(); ++i) {
                GHPoint3D prevPoint = currentRoutePoints.toGHPoint(i - 1);
                GHPoint3D currPoint = currentRoutePoints.toGHPoint(i);

                double dist = Double.MAX_VALUE;
                int index = i;
                // calculate distance from origin to each segment of the current route
                if (distCalc.validEdgeDistance(origin.getLat(), origin.getLon(), currPoint.getLat(), currPoint.getLon(), prevPoint.getLat(), prevPoint.getLon())) {
                    dist = distCalc.calcDenormalizedDist(distCalc.calcNormalizedEdgeDistance(origin.getLat(), origin.getLon(), currPoint.getLat(), currPoint.getLon(), prevPoint.getLat(), prevPoint.getLon()));
                } else {
                    double dist1 = distCalc.calcDist(origin.getLat(), origin.getLon(), prevPoint.getLat(), prevPoint.getLon());
                    double dist2 = distCalc.calcDist(origin.getLat(), origin.getLon(), currPoint.getLat(), currPoint.getLon());

                    // origin is closer to the prevPoint
                    if (dist1 < dist2) {
                        dist = dist1;
                        index = i;
                    }
                    else { // origin is closer to currPoint
                        dist = dist2;
                        index = i + 1;
                    }
                }
                if (dist < minDist) {
                    minIndex = i - 1;
                    minDist = dist;
                }

            }
            
            if (minIndex == -1) {
                ghRsp.addError(new IllegalArgumentException("Rerouting: origin point is not on route"));
            } else {
                PointList trimmedCurrentRoutePoints = new PointList(currentRoutePoints.size(), currentRoutePoints.is3D());
                trimmedCurrentRoutePoints.add(origin.getLat(), origin.getLon());
                for (int i = minIndex + 1; i < currentRoutePoints.size(); ++i) {
                    trimmedCurrentRoutePoints.add(currentRoutePoints.toGHPoint(i));
                }

                if (hausdorffDistance(ghRsp.getBest().getPoints(), trimmedCurrentRoutePoints) > 50) { // meters
                    logger.info("Rerouting due to distance threshold");
                    // TODO: need to walk currentRoute and see if it goes over any construction events so that this reason is legit
                    rerouteResult = RerouteResult.CLOSURE;
                }
            }
        }

        if (writeGPX) {
            logger.warn("exporting GPX per request, limited functionality (e.g. no rerouting support)");
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

            if (rerouteRequested) {
                ArrayList<Map<String, Object>> pathsArr = (ArrayList<Map<String, Object>>)map.get("paths");
                Map<String, Object> path = pathsArr.get(0);
                path.put("reroute_result", rerouteResult);

                if (eventImageRequested && rerouteResult != RerouteResult.NONE) {
                    // use test closure base64 image for now
                    path.put("event_image", this.testClosureBase64);
                }
            }
         
            Object infoMap = map.get("info");
            if (infoMap != null)
                ((Map) infoMap).put("took", Math.round(took * 1000));

            if (ghRsp.hasErrors())
                writeJsonError(httpRes, SC_BAD_REQUEST, new JSONObject(map));
            else {
                writeJson(httpReq, httpRes, new JSONObject(map));
            }
        }
    }

    /**
     * Computes the Hausdorff distance between two polylines. O(N^2) complexity!!
     * https://en.wikipedia.org/wiki/Hausdorff_distance
     * @param poly1 - polyline 1
     * @param poly2 - polyline 2
     * @return Hausdorff distance
     */
    private double hausdorffDistance(PointList poly1, PointList poly2) {
        DistanceCalc distCalc = Helper.DIST_EARTH;

        double longestShortestDistance = 0;
        for (GHPoint3D p1 : poly1) {
            double shortestP1DistanceOverPoly2 = Double.MAX_VALUE;
            for (GHPoint3D p2 : poly2) {
                double dist = distCalc.calcDist(p1.getLat(), p1.getLon(), p2.getLat(), p2.getLon());
                if (dist < shortestP1DistanceOverPoly2) {
                    shortestP1DistanceOverPoly2 = dist;
                }
            }
            if (shortestP1DistanceOverPoly2 > longestShortestDistance) {
                longestShortestDistance = shortestP1DistanceOverPoly2;
            }
        }
        return longestShortestDistance;
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

    protected PointList getCurrentRoutePoints(HttpServletRequest req, String key, PointList defaultValue) {
        String encodedRoutePoints = getParam(req, "current_route_points", null);
        if(encodedRoutePoints == null || encodedRoutePoints.isEmpty()) {
            return defaultValue;
        }

        return WebHelper.decodePolyline(encodedRoutePoints, 100, false);
    }

    protected void initHints(GHRequest request, Map<String, String[]> parameterMap) {
        HintsMap m = request.getHints();
        for (Entry<String, String[]> e : parameterMap.entrySet()) {
            if (e.getValue().length == 1)
                m.put(e.getKey(), e.getValue()[0]);
        }
    }

    public static byte[] loadFileAsBytesArray(String fileName) throws Exception {

        File file = new File(fileName);
        int length = (int) file.length();
        BufferedInputStream reader = new BufferedInputStream(new FileInputStream(file));
        byte[] bytes = new byte[length];
        reader.read(bytes, 0, length);
        reader.close();
        return bytes;

    }
}
