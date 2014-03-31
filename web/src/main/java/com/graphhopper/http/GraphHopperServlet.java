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
import com.graphhopper.GraphHopper;
import com.graphhopper.GHResponse;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.StorableProperties;
import com.graphhopper.util.*;
import com.graphhopper.util.TranslationMap.Translation;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.GHPlace;
import java.io.IOException;
import java.util.*;
import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import static javax.servlet.http.HttpServletResponse.*;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Servlet to use GraphHopper in a remote application (mobile or browser). Attention: If type is
 * json it returns the points in GeoJson format (longitude,latitude) unlike the format "lat,lon"
 * used otherwise.
 * <p/>
 * @author Peter Karich
 */
public class GraphHopperServlet extends GHBaseServlet
{

    @Inject
    private GraphHopper hopper;
    @Inject
    @Named("defaultAlgorithm")
    private String defaultAlgorithm;
    @Inject
    private TranslationMap trMap;

    @Override
    public void doGet( HttpServletRequest req, HttpServletResponse res ) throws ServletException, IOException
    {
        try
        {
            if ("/info".equals(req.getPathInfo()))
            {
                writeInfos(req, res);
            } else if ("/route".equals(req.getPathInfo()))
            {
                writePath(req, res);
            }
        } catch (IllegalArgumentException ex)
        {
            writeError(res, SC_BAD_REQUEST, ex.getMessage());
        } catch (Exception ex)
        {
            logger.error("Error while executing request: " + req.getQueryString(), ex);
            writeError(res, SC_INTERNAL_SERVER_ERROR, "Problem occured:" + ex.getMessage());
        }
    }

    void writeInfos( HttpServletRequest req, HttpServletResponse res ) throws Exception
    {
        BBox bb = hopper.getGraph().getBounds();
        List<Double> list = new ArrayList<Double>(4);
        list.add(bb.minLon);
        list.add(bb.minLat);
        list.add(bb.maxLon);
        list.add(bb.maxLat);

        JSONObject json = new JSONObject();
        json.put("bbox", list);
        json.put("supported_vehicles", hopper.getGraph().getEncodingManager().toString().split(","));
        json.put("version", Constants.VERSION);
        json.put("build_date", Constants.BUILD_DATE);

        StorableProperties props = hopper.getGraph().getProperties();
        json.put("import_date", props.get("osmreader.import.date"));

        if (!Helper.isEmpty(props.get("prepare.date")))
            json.put("prepare_date", props.get("prepare.date"));

        writeJson(req, res, json);
    }

    void writePath( HttpServletRequest req, HttpServletResponse res ) throws Exception
    {
        List<GHPlace> infoPoints = getPoints(req);

        // we can reduce the path length based on the maximum differences to the original coordinates
        double minPathPrecision = getDoubleParam(req, "min_path_precision", 1d);
        boolean writeGPX = "gpx".equalsIgnoreCase(getParam(req, "type", "json"));
        boolean enableInstructions = writeGPX || getBooleanParam(req, "instructions", true);
        boolean calcPoints = getBooleanParam(req, "calc_points", true);
        String vehicleStr = getParam(req, "vehicle", "CAR").toUpperCase();
        String weighting = getParam(req, "weighting", "fastest");
        String algoStr = getParam(req, "algorithm", defaultAlgorithm);

        StopWatch sw = new StopWatch().start();
        GHResponse rsp;
        if (hopper.getEncodingManager().supports(vehicleStr))
        {
            FlagEncoder algoVehicle = hopper.getEncodingManager().getEncoder(vehicleStr);
            rsp = hopper.route(new GHRequest(infoPoints).
                    setVehicle(algoVehicle.toString()).
                    setWeighting(weighting).
                    setAlgorithm(algoStr).
                    putHint("calcPoints", calcPoints).
                    putHint("instructions", enableInstructions).
                    putHint("douglas.minprecision", minPathPrecision));
        } else
        {
            rsp = new GHResponse().addError(new IllegalArgumentException("Vehicle not supported: " + vehicleStr));
        }

        float took = sw.stop().getSeconds();
        String infoStr = req.getRemoteAddr() + " " + req.getLocale() + " " + req.getHeader("User-Agent");
        PointList points = rsp.getPoints();
        String logStr = req.getQueryString() + " " + infoStr + " " + infoPoints
                + ", distance: " + rsp.getDistance() + ", time:" + Math.round(rsp.getMillis() / 60000f)
                + "min, points:" + points.getSize() + ", took:" + took
                + ", debug - " + rsp.getDebugInfo() + ", " + algoStr + ", "
                + weighting + ", " + vehicleStr;

        if (rsp.hasErrors())
            logger.error(logStr + ", errors:" + rsp.getErrors());
        else
            logger.info(logStr);

        if (writeGPX)
            writeGPX(req, res, rsp);
        else
            writeJson(req, res, rsp, took);
    }

    private void writeGPX( HttpServletRequest req, HttpServletResponse res, GHResponse rsp )
    {
        res.setCharacterEncoding("UTF-8");
        res.setContentType("application/xml");
        String trackName = getParam(req, "track", "GraphHopper Track");
        res.setHeader("Content-Disposition", "attachment;filename=" + "GraphHopper.gpx");
        String timeZone = getParam(req, "timezone", "GMT");
        long time = getLongParam(req, "millis", System.currentTimeMillis());
        writeResponse(res, rsp.getInstructions().createGPX(trackName, time, timeZone));
    }

    private void writeJson( HttpServletRequest req, HttpServletResponse res,
            GHResponse rsp, float took ) throws JSONException, IOException
    {
        boolean enableInstructions = getBooleanParam(req, "instructions", true);
        Locale locale = Helper.getLocale(getParam(req, "locale", "en"));
        boolean pointsEncoded = getBooleanParam(req, "points_encoded", true);
        boolean calcPoints = getBooleanParam(req, "calc_points", true);
        JSONObject json = new JSONObject();

        JSONObject jsonPath = new JSONObject();
        json.put("paths", Collections.singletonList(jsonPath));

        JSONObject jsonInfo = new JSONObject();
        json.put("info", jsonInfo);

        if (rsp.hasErrors())
        {
            List<Map<String, String>> list = new ArrayList<Map<String, String>>();
            for (Throwable t : rsp.getErrors())
            {
                Map<String, String> map = new HashMap<String, String>();
                map.put("message", t.getMessage());
                map.put("details", t.getClass().getName());
                list.add(map);
            }
            jsonInfo.put("errors", list);
        } else if (!rsp.isFound())
        {
            Map<String, String> map = new HashMap<String, String>();
            map.put("message", "Not found");
            map.put("details", "");
            jsonInfo.put("errors", Collections.singletonList(map));
        } else
        {
            jsonInfo.put("took", took);
            jsonPath.put("distance", rsp.getDistance());
            jsonPath.put("time", rsp.getMillis());

            if (calcPoints)
            {
                jsonPath.put("points_encoded", pointsEncoded);

                PointList points = rsp.getPoints();
                if (points.getSize() >= 2)
                    jsonPath.put("bbox", rsp.calcRouteBBox(hopper.getGraph().getBounds()).toGeoJson());

                jsonPath.put("points", createPoints(points, pointsEncoded));
                jsonPath.put("points_dimension", points.getDimension());

                if (enableInstructions)
                {
                    Translation tr = trMap.getWithFallBack(locale);
                    InstructionList instructions = rsp.getInstructions();
                    jsonPath.put("instructions", instructions.createJson(tr));
                }
            }
        }

        writeJson(req, res, json);
    }

    Object createPoints( PointList points, boolean pointsEncoded ) throws JSONException
    {
        if (pointsEncoded)
            return WebHelper.encodePolyline(points);

        JSONObject jsonPoints = new JSONObject();
        jsonPoints.put("type", "LineString");
        jsonPoints.put("coordinates", points.toGeoJson());
        return jsonPoints;
    }

    private List<GHPlace> getPoints( HttpServletRequest req ) throws IOException
    {
        String[] pointsAsStr = getParams(req, "point");
        final List<GHPlace> infoPoints = new ArrayList<GHPlace>();
        for (int pointNo = 0; pointNo < pointsAsStr.length; pointNo++)
        {
            final String str = pointsAsStr[pointNo];
            String[] fromStrs = str.split(",");
            if (fromStrs.length == 2)
            {
                GHPlace place = GHPlace.parse(str);
                if (place != null)
                    infoPoints.add(place);
            }
        }

        return infoPoints;
    }
}
