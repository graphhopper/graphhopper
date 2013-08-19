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
import com.graphhopper.routing.util.FastestCalc;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.ShortestCalc;
import com.graphhopper.routing.util.WeightCalculation;
import com.graphhopper.util.*;
import com.graphhopper.util.TranslationMap.Translation;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.GHPlace;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
public class GraphHopperServlet extends GHServlet
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

    void writeInfos( HttpServletRequest req, HttpServletResponse res ) throws JSONException
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
            boolean enableInstructions = getBooleanParam(req, "instructions", true);
            String vehicleStr = getParam(req, "vehicle", "CAR").toUpperCase();
            Locale locale = Helper.getLocale(getParam(req, "locale", "en"));
            String algoTypeStr = getParam(req, "algoType", "fastest");
            String algoStr = getParam(req, "algorithm", defaultAlgorithm);
            boolean encodedPolylineParam = getBooleanParam(req, "encodedPolyline", true);

            sw = new StopWatch().start();
            GHResponse rsp;
            if (hopper.getEncodingManager().supports(vehicleStr))
            {
                FlagEncoder algoVehicle = hopper.getEncodingManager().getEncoder(vehicleStr);
                WeightCalculation algoType = new FastestCalc(algoVehicle);
                if ("shortest".equalsIgnoreCase(algoTypeStr))
                    algoType = new ShortestCalc();

                rsp = hopper.route(new GHRequest(start, end).
                        setVehicle(algoVehicle.toString()).
                        setType(algoType).
                        setAlgorithm(algoStr).
                        putHint("instructions", enableInstructions).
                        putHint("douglas.minprecision", minPathPrecision));
            } else
            {
                rsp = new GHResponse().addError(new IllegalArgumentException("Vehicle not supported: " + vehicleStr));
            }

            float took = sw.stop().getSeconds();
            String infoStr = req.getRemoteAddr() + " " + req.getLocale() + " " + req.getHeader("User-Agent");
            PointList points = rsp.getPoints();
            double distInMeter = rsp.getDistance();
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
                        object("distance", distInMeter).
                        object("time", rsp.getTime());

                if (enableInstructions)
                {
                    Translation tr = trMap.getWithFallBack(locale);
                    InstructionList instructions = rsp.getInstructions();
                    builder.startObject("instructions").
                            object("descriptions", instructions.createDescription(tr)).
                            object("distances", instructions.createDistances(locale)).
                            object("indications", instructions.createIndications()).
                            endObject();
                }

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
            String logStr = req.getQueryString() + " " + infoStr + " " + start + "->" + end
                    + ", distance: " + distInMeter + ", time:" + Math.round(rsp.getTime() / 60f)
                    + "min, points:" + points.getSize() + ", took:" + took
                    + ", debug - " + rsp.getDebugInfo() + ", " + algoStr + ", "
                    + algoTypeStr + ", " + vehicleStr;
            if (rsp.hasErrors())
                logger.error(logStr + ", errors:" + rsp.getErrors());
            else
                logger.info(logStr);

        } catch (Exception ex)
        {
            logger.error("Error while query:" + start + "->" + end, ex);
            writeError(res, SC_INTERNAL_SERVER_ERROR, "Problem occured:" + ex.getMessage());
        }
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
