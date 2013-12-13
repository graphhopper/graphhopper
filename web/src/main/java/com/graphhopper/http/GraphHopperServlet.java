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

import com.graphhopper.search.Geocoding;
import com.graphhopper.GHRequest;
import com.graphhopper.GraphHopper;
import com.graphhopper.GHResponse;
import com.graphhopper.routing.util.FlagEncoder;
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
    private Geocoding geocoding;
    @Inject
    @Named("defaultAlgorithm")
    private String defaultAlgorithm;
    @Inject
    @Named("timeout")
    private Long timeOutInMillis;
    @Inject
    private GHThreadPool threadPool;
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
        JSONBuilder json = new JSONBuilder().
                object("bbox", list).
                object("supportedVehicles", hopper.getEncodingManager()).
                object("version", Constants.VERSION).
                object("buildDate", Constants.BUILD_DATE);
        writeJson(req, res, json.build());
    }

    void writePath( HttpServletRequest req, HttpServletResponse res ) throws Exception
    {
        StopWatch sw = new StopWatch().start();
        List<GHPlace> infoPoints = getPoints(req);
        float tookGeocoding = sw.stop().getSeconds();
        GHPlace start = infoPoints.get(0);
        GHPlace end = infoPoints.get(1);
        try
        {
            // we can reduce the path length based on the maximum differences to the original coordinates
            double minPathPrecision = getDoubleParam(req, "minPathPrecision", 1d);
            boolean writeGPX = "gpx".equalsIgnoreCase(getParam(req, "type", "json"));
            boolean enableInstructions = writeGPX || getBooleanParam(req, "instructions", true);
            boolean calcPoints = getBooleanParam(req, "calcPoints", true);
            String vehicleStr = getParam(req, "vehicle", "CAR").toUpperCase();
            String weighting = getParam(req, "weighting", "fastest");
            // REMOVE_IN 0.3
            if (req.getParameterMap().containsKey("algoType"))
                weighting = getParam(req, "algoType", "fastest");

            String algoStr = getParam(req, "algorithm", defaultAlgorithm);

            sw = new StopWatch().start();
            GHResponse rsp;
            if (hopper.getEncodingManager().supports(vehicleStr))
            {
                FlagEncoder algoVehicle = hopper.getEncodingManager().getEncoder(vehicleStr);
                rsp = hopper.route(new GHRequest(start, end).
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
            String logStr = req.getQueryString() + " " + infoStr + " " + start + "->" + end
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
                writeJson(req, res, rsp, start, end, tookGeocoding, took);

        } catch (Exception ex)
        {
            logger.error("Error while query:" + start + "->" + end, ex);
            writeError(res, SC_INTERNAL_SERVER_ERROR, "Problem occured:" + ex.getMessage());
        }
    }

    private void writeGPX( HttpServletRequest req, HttpServletResponse res, GHResponse rsp )
    {
        res.setCharacterEncoding("UTF-8");
        res.setContentType("application/xml");
        String trackName = getParam(req, "track", "GraphHopper Track");
        String timeZone = getParam(req, "timezone", "GMT");
        long time = getLongParam(req, "millis", System.currentTimeMillis());
        writeResponse(res, rsp.getInstructions().createGPX(trackName, time, timeZone));
    }

    private void writeJson( HttpServletRequest req, HttpServletResponse res,
            GHResponse rsp, GHPlace start, GHPlace end,
            float tookGeocoding, float took ) throws JSONException
    {
        boolean enableInstructions = getBooleanParam(req, "instructions", true);
        boolean useMiles = getBooleanParam(req, "useMiles", false);
        Locale locale = Helper.getLocale(getParam(req, "locale", "en"));
        boolean encodedPolylineParam = getBooleanParam(req, "encodedPolyline", true);
        JSONBuilder builder;
        if (rsp.hasErrors())
        {
            builder = new JSONBuilder().startObject("info");
            List<Map<String, String>> list = new ArrayList<Map<String, String>>();
            for (Throwable t : rsp.getErrors())
            {
                Map<String, String> map = new HashMap<String, String>();
                map.put("message", t.getMessage());
                map.put("details", t.getClass().getName());
                list.add(map);
            }
            builder = builder.object("errors", list).endObject();
        } else
        {
            builder = new JSONBuilder().
                    startObject("info").
                    object("routeFound", rsp.isFound()).
                    object("took", took).
                    object("tookGeocoding", tookGeocoding).
                    endObject();
            builder = builder.startObject("route").
                    object("from", new Double[]
                            {
                                start.lon, start.lat
                    }).
                    object("to", new Double[]
                            {
                                end.lon, end.lat
                    }).
                    object("distance", rsp.getDistance()).
                    object("time", rsp.getMillis());

            if (enableInstructions)
            {
                Translation tr = trMap.getWithFallBack(locale);
                InstructionList instructions = rsp.getInstructions();
                builder.startObject("instructions").
                        object("descriptions", instructions.createDescription(tr)).
                        object("distances", instructions.createDistances(tr, useMiles)).
                        object("indications", instructions.createIndications()).
                        object("times", instructions.createTimes(tr)).
                        object("latLngs", instructions.createSegmentStartPoints()).
                        endObject();
            }

            PointList points = rsp.getPoints();
            if (points.getSize() >= 2)
                builder.object("bbox", rsp.calcRouteBBox(hopper.getGraph().getBounds()).toGeoJson());

            if (encodedPolylineParam)
            {
                String encodedPolyline = WebHelper.encodePolyline(points);
                builder.object("coordinates", encodedPolyline);
            } else
            {
                builder.startObject("data").
                        object("type", "LineString").
                        object("coordinates", points.toGeoJson()).
                        endObject();
            }
            // end route
            builder = builder.endObject();
        }

        writeJson(req, res, builder.build());
    }

    private List<GHPlace> getPoints( HttpServletRequest req ) throws IOException
    {
        String[] pointsAsStr = getParams(req, "point");
        // allow two formats
        if (pointsAsStr.length == 0)
        {
            String from = getParam(req, "from", "");
            String to = getParam(req, "to", "");
            if (!Helper.isEmpty(from) && !Helper.isEmpty(to))
            {
                pointsAsStr = new String[]
                {
                    from, to
                };
            }
        }

        final List<GHPlace> infoPoints = new ArrayList<GHPlace>();
        List<GHThreadPool.GHWorker> workers = new ArrayList<GHThreadPool.GHWorker>();
        for (int pointNo = 0; pointNo < pointsAsStr.length; pointNo++)
        {
            final String str = pointsAsStr[pointNo];
            String[] fromStrs = str.split(",");
            if (fromStrs.length == 2)
            {
                GHPlace place = GHPlace.parse(str);
                if (place != null)
                    infoPoints.add(place);
                continue;
            }

            // now it is not a coordinate and we need to call geo resolver
            final int index = infoPoints.size();
            infoPoints.add(new GHPlace(Double.NaN, Double.NaN).setName(str));
            GHThreadPool.GHWorker worker = new GHThreadPool.GHWorker(timeOutInMillis)
            {
                @Override
                public String getName()
                {
                    return "geocoding search " + str;
                }

                @Override
                public void run()
                {
                    List<GHPlace> tmpPoints = geocoding.name2point(new GHPlace(str));
                    if (!tmpPoints.isEmpty())
                    {
                        infoPoints.set(index, tmpPoints.get(0));
                    }
                }
            };
            workers.add(worker);
            threadPool.enqueue(worker);
        }
        threadPool.waitFor(workers, timeOutInMillis);
        for (GHPlace p : infoPoints)
        {
            if (Double.isNaN(p.lat))
            {
                throw new IllegalArgumentException("[nominatim] Not all points could be resolved! " + infoPoints);
            }
        }

        // TODO resolve name in a thread if only lat,lon is given but limit to a certain timeout
        if (infoPoints == null || infoPoints.size() < 2)
        {
            throw new IllegalArgumentException("Did you specify point=<from>&point=<to> ? Use at least 2 points! " + infoPoints);
        }

        // TODO execute algorithm multiple times!
        if (infoPoints.size() != 2)
        {
            throw new IllegalArgumentException("TODO! At the moment only 2 points can be specified");
        }

        return infoPoints;
    }
}
