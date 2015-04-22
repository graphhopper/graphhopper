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

import com.graphhopper.GraphHopper;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.shapes.GHPoint;
import com.graphhopper.util.shapes.GHPoint3D;
import java.io.IOException;
import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * @author svantulden
 */
public class NearestServlet extends GHBaseServlet
{
    @Inject
    private GraphHopper hopper;

    @Override
    public void doGet( HttpServletRequest req, HttpServletResponse res ) throws ServletException, IOException
    {
        try
        {
            writeNearest(req, res);
        } catch (IllegalArgumentException ex)
        {
            writeError(res, SC_BAD_REQUEST, ex.getMessage());
        } catch (Exception ex)
        {
            logger.error("Error while executing request: " + req.getQueryString(), ex);
            writeError(res, SC_INTERNAL_SERVER_ERROR, "Problem occured:" + ex.getMessage());
        }
    }

    void writeNearest( HttpServletRequest req, HttpServletResponse res ) throws Exception
    {
        String pointStr = getParam(req, "point", null);
        boolean enableElevation = getBooleanParam(req, "elevation", false);
        
        JSONObject result = new JSONObject();
        if (pointStr != null && !pointStr.equalsIgnoreCase("")) {
            GHPoint place = GHPoint.parse(pointStr);
            
            LocationIndex index = hopper.getLocationIndex();
            QueryResult qr = index.findClosest( place.lat, place.lon, EdgeFilter.ALL_EDGES );
            
            GHPoint3D snappedPoint = null;
            try {
                snappedPoint = qr.getSnappedPoint();
            } catch (IllegalStateException ex) {
                result.put("error", "Nearest point cannot be found!");
            }
            
            if (snappedPoint != null) {
                result.put("type", "Point");
                
                JSONArray coord = new JSONArray();
                coord.put(snappedPoint.lon);
                coord.put(snappedPoint.lat);
                
                if (hopper.hasElevation() && enableElevation) {
                    coord.put(snappedPoint.ele);
                }

                result.put("coordinates", coord);
                
                DistanceCalcEarth calc = new DistanceCalcEarth();
                double distance = calc.calcDist(place.lat, place.lon, snappedPoint.lat, snappedPoint.lon);
                
                // Distance from input to snapped point in meters
                result.put("distance", distance);
            }
        } else {
            result.put("error", "No lat/lon specified!");
        }  
        
        writeJson(req, res, result);
    }
}
