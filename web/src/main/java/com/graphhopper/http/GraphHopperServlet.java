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
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import static javax.servlet.http.HttpServletResponse.*;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Servlet to use GraphHopper in a remote application (mobile or browser). Attention: If type is
 * json it returns the points in GeoJson format (longitude,latitude) unlike the format "lat,lon"
 * used otherwise.
 * <p/>
 * @author Peter Karich
 */
public class GraphHopperServlet extends HttpServlet
{
    private Logger logger = LoggerFactory.getLogger(getClass());
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
        // we can reduce the path length based on the maximum differences to the original coordinates
        double minPathPrecision = getDoubleParam(req, "minPathPrecision", 1d);
        boolean enableInstructions = getBooleanParam(req, "instructions", false);
        String vehicleStr = getParam(req, "vehicle", "CAR");
        Locale locale = Helper.getLocale(getParam(req, "locale", "en"));
        FlagEncoder algoVehicle = hopper.getEncodingManager().getEncoder(vehicleStr.toUpperCase());
        WeightCalculation algoType = new FastestCalc(algoVehicle);
        if ("shortest".equalsIgnoreCase(getParam(req, "algoType", null)))
        {
            algoType = new ShortestCalc();
        }

        String algoStr = getParam(req, "algorithm", defaultAlgorithm);
        try
        {
            sw = new StopWatch().start();
            GHResponse rsp = hopper.route(new GHRequest(start, end).
                    setVehicle(algoVehicle.toString()).
                    setType(algoType).
                    setAlgorithm(algoStr).
                    putHint("instructions", enableInstructions).
                    putHint("douglas.minprecision", minPathPrecision));
            if (rsp.hasError())
            {
                JSONBuilder builder = new JSONBuilder().startObject("info");
                List<Map<String, String>> list = new ArrayList<Map<String, String>>();
                for (Throwable t : rsp.getErrors())
                {
                    Map<String, String> map = new HashMap<String, String>();
                    map.put("message", t.getMessage());
                    map.put("details", t.getClass().getName());
                    list.add(map);
                }
                builder = builder.object("errors", list).endObject();
                writeJson(req, res, builder.build());
                return;
            }

            float took = sw.stop().getSeconds();
            String infoStr = req.getRemoteAddr() + " " + req.getLocale() + " " + req.getHeader("User-Agent");
            PointList points = rsp.getPoints();

            double distInKM = rsp.getDistance() / 1000;
            boolean encodedPolylineParam = getBooleanParam(req, "encodedPolyline", true);

            JSONBuilder builder = new JSONBuilder().
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
                    object("distance", distInKM).
                    object("time", rsp.getTime());

            if (enableInstructions)
            {
                InstructionList instructions = rsp.getInstructions();
                builder.startObject("instructions").
                        object("descriptions", instructions.createDescription(locale)).
                        object("distances", instructions.createDistances(locale)).
                        object("indications", instructions.createIndications()).
                        endObject();
            }
            if (points.getSize() > 2)
            {
                builder.object("bbox", rsp.calcRouteBBox(hopper.getGraph().getBounds()).toGeoJson());
            }
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

            writeJson(req, res, builder.build());
            logger.info(req.getQueryString() + " " + infoStr + " " + start + "->" + end
                    + ", distance: " + distInKM + ", time:" + Math.round(rsp.getTime() / 60f)
                    + "min, points:" + points.getSize() + ", took:" + took
                    + ", debug - " + rsp.getDebugInfo() + ", " + algoStr + ", "
                    + algoType + ", " + algoVehicle);
        } catch (Exception ex)
        {
            logger.error("Error while query:" + start + "->" + end, ex);
            writeError(res, SC_INTERNAL_SERVER_ERROR, "Problem occured:" + ex.getMessage());
        }
    }

    protected String getParam( HttpServletRequest req, String string, String _default )
    {
        String[] l = req.getParameterMap().get(string);
        if (l != null && l.length > 0)
        {
            return l[0];
        }
        return _default;
    }

    protected String[] getParams( HttpServletRequest req, String string )
    {
        String[] l = req.getParameterMap().get(string);
        if (l != null && l.length > 0)
        {
            return l;
        }
        return new String[0];
    }

    protected boolean getBooleanParam( HttpServletRequest req, String string, boolean _default )
    {
        try
        {
            return Boolean.parseBoolean(getParam(req, string, null));
        } catch (Exception ex)
        {
            return _default;
        }
    }

    protected double getDoubleParam( HttpServletRequest req, String string, double _default )
    {
        try
        {
            return Double.parseDouble(getParam(req, string, null));
        } catch (Exception ex)
        {
            return _default;
        }
    }

    public void writeError( HttpServletResponse res, int code, String str )
    {
        try
        {
            res.sendError(code, str);
        } catch (IOException ex)
        {
            logger.error("Cannot write error " + code + " message:" + str, ex);
        }
    }

    public void writeResponse( HttpServletResponse res, String str )
    {
        try
        {
            res.setStatus(SC_OK);
            res.getWriter().append(str);
        } catch (IOException ex)
        {
            logger.error("Cannot write message:" + str, ex);
        }
    }

    private void writeJson( HttpServletRequest req, HttpServletResponse res, JSONObject json ) throws JSONException
    {
        String type = getParam(req, "type", "json");
        res.setCharacterEncoding("UTF-8");
        boolean debug = getBooleanParam(req, "debug", false);
        if ("jsonp".equals(type))
        {
            res.setContentType("application/javascript");
            String callbackName = getParam(req, "callback", null);
            if (debug)
            {
                writeResponse(res, callbackName + "(" + json.toString(2) + ")");
            } else
            {
                writeResponse(res, callbackName + "(" + json.toString() + ")");
            }
        } else
        {
            res.setContentType("application/json");
            if (debug)
            {
                writeResponse(res, json.toString(2));
            } else
            {
                writeResponse(res, json.toString());
            }
        }
    }

    void returnError( HttpServletResponse res, String errorMessage ) throws IOException
    {
        res.sendError(SC_BAD_REQUEST, errorMessage);
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
            try
            {
                String[] fromStrs = str.split(",");
                if (fromStrs.length == 2)
                {
                    double fromLat = Double.parseDouble(fromStrs[0]);
                    double fromLon = Double.parseDouble(fromStrs[1]);
                    infoPoints.add(new GHPlace(fromLat, fromLon));
                    continue;
                }
            } catch (Exception ex)
            {
            }

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
