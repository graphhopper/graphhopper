/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
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
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.WeightingMap;
import com.graphhopper.util.StopWatch;
import com.graphhopper.util.shapes.GHPoint;
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
import java.util.Map.Entry;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;

/**
 * Servlet to use GraphHopper in a remote client application like mobile or browser. Note: If type
 * is json it returns the points in GeoJson format (longitude,latitude) unlike the format "lat,lon"
 * used otherwise. See the full API response format in docs/web/api-doc.md
 * <p/>
 *
 * @author Peter Karich
 */
public class GraphHopperServlet extends GHBaseServlet
{
    @Inject
    private GraphHopper hopper;
    @Inject
    private RouteSerializer routeSerializer;

    @Override
    public void doGet( HttpServletRequest httpReq, HttpServletResponse httpRes ) throws ServletException, IOException
    {
        List<GHPoint> infoPoints = getPoints(httpReq, "point");
        GHResponse ghRsp = new GHResponse();

        // we can reduce the path length based on the maximum differences to the original coordinates
        double minPathPrecision = getDoubleParam(httpReq, "way_point_max_distance", 1d);
        boolean writeGPX = "gpx".equalsIgnoreCase(getParam(httpReq, "type", "json"));
        boolean enableInstructions = writeGPX || getBooleanParam(httpReq, "instructions", true);
        boolean calcPoints = getBooleanParam(httpReq, "calc_points", true);
        boolean enableElevation = getBooleanParam(httpReq, "elevation", false);
        boolean pointsEncoded = getBooleanParam(httpReq, "points_encoded", true);

        String vehicleStr = getParam(httpReq, "vehicle", "car");
        String weighting = getParam(httpReq, "weighting", "fastest");
        String algoStr = getParam(httpReq, "algorithm", "");
        String localeStr = getParam(httpReq, "locale", "en");
        List<Double> favoredHeadings = Collections.EMPTY_LIST;
        try
        {
            favoredHeadings = getDoubleParamList(httpReq, "heading");

        } catch (java.lang.NumberFormatException e)
        {
            throw new RuntimeException(e);
        }

        StopWatch sw = new StopWatch().start();
        if (!hopper.getEncodingManager().supports(vehicleStr))
        {
            ghRsp.addError(new IllegalArgumentException("Vehicle not supported: " + vehicleStr));
        } else if (enableElevation && !hopper.hasElevation())
        {
            ghRsp.addError(new IllegalArgumentException("Elevation not supported!"));
        } else if (favoredHeadings.size() > 1 && favoredHeadings.size() != infoPoints.size())
        {
            ghRsp.addError(new IllegalArgumentException("number of headings must be <= 1 or equal number of points"));
        }
        if (!ghRsp.hasErrors())
        {
            FlagEncoder algoVehicle = hopper.getEncodingManager().getEncoder(vehicleStr);

            GHRequest request;
            if (favoredHeadings.size() > 0)
            {
                // if only one favored heading is specified take as start heading
                if (favoredHeadings.size() == 1)
                {
                    List<Double> paddedHeadings = new ArrayList<Double>(Collections.nCopies(infoPoints.size(),
                            Double.NaN));
                    paddedHeadings.set(0, favoredHeadings.get(0));
                    request = new GHRequest(infoPoints, paddedHeadings);
                } else
                {
                    request = new GHRequest(infoPoints, favoredHeadings);
                }
            } else
            {
                request = new GHRequest(infoPoints);
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
        }

        float took = sw.stop().getSeconds();
        String infoStr = httpReq.getRemoteAddr() + " " + httpReq.getLocale() + " " + httpReq.getHeader("User-Agent");
        String logStr = httpReq.getQueryString() + " " + infoStr + " " + infoPoints + ", took:"
                + took + ", " + algoStr + ", " + weighting + ", " + vehicleStr;
        httpRes.setHeader("X-GH-Took", "" + Math.round(took * 1000));

        if (ghRsp.hasErrors())
            logger.error(logStr + ", errors:" + ghRsp.getErrors());
        else
            logger.info(logStr + ", distance: " + ghRsp.getDistance()
                    + ", time:" + Math.round(ghRsp.getTime() / 60000f)
                    + "min, points:" + ghRsp.getPoints().getSize() + ", debug - " + ghRsp.getDebugInfo());

        if (writeGPX)
        {
            String xml = createGPXString(httpReq, httpRes, ghRsp);
            if (ghRsp.hasErrors())
            {
                httpRes.setStatus(SC_BAD_REQUEST);
                httpRes.getWriter().append(xml);
            } else
            {
                writeResponse(httpRes, xml);
            }
        } else
        {
            Map<String, Object> map = routeSerializer.toJSON(ghRsp, calcPoints, pointsEncoded,
                    enableElevation, enableInstructions);

            // deprecated - remove in 0.5
            Object infoMap = map.get("info");
            if (infoMap != null)
                ((Map) infoMap).put("took", Math.round(took * 1000));

            if (ghRsp.hasErrors())
                writeJsonError(httpRes, SC_BAD_REQUEST, new JSONObject(map));
            else
                writeJson(httpReq, httpRes, new JSONObject(map));
        }
    }

    protected String createGPXString( HttpServletRequest req, HttpServletResponse res, GHResponse rsp )
    {
        boolean includeElevation = getBooleanParam(req, "elevation", false);
        res.setCharacterEncoding("UTF-8");
        res.setContentType("application/xml");
        String trackName = getParam(req, "track", "GraphHopper Track");
        res.setHeader("Content-Disposition", "attachment;filename=" + "GraphHopper.gpx");
        long time = getLongParam(req, "millis", System.currentTimeMillis());
        if (rsp.hasErrors())
            return errorsToXML(rsp.getErrors());
        else
            return rsp.getInstructions().createGPX(trackName, time, includeElevation);
    }

    String errorsToXML( List<Throwable> list )
    {
        try
        {
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
            messageElement.setTextContent(list.get(0).getMessage());

            Element hintsElement = doc.createElement("hints");
            extensionsElement.appendChild(hintsElement);

            for (Throwable t : list)
            {
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
        } catch (Exception ex)
        {
            throw new RuntimeException(ex);
        }
    }

    protected List<GHPoint> getPoints( HttpServletRequest req, String key )
    {
        String[] pointsAsStr = getParams(req, key);
        final List<GHPoint> infoPoints = new ArrayList<GHPoint>(pointsAsStr.length);
        for (String str : pointsAsStr)
        {
            String[] fromStrs = str.split(",");
            if (fromStrs.length == 2)
            {
                GHPoint point = GHPoint.parse(str);
                if (point != null)
                    infoPoints.add(point);
            }
        }

        return infoPoints;
    }

    protected void initHints( GHRequest request, Map<String, String[]> parameterMap )
    {
        WeightingMap m = request.getHints();
        for (Entry<String, String[]> e : parameterMap.entrySet())
        {
            if (e.getValue().length == 1)
                m.put(e.getKey(), e.getValue()[0]);
        }
    }
}
