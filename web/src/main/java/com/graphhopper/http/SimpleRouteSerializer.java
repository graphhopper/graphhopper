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

import com.graphhopper.GHResponse;
import com.graphhopper.PathWrapper;
import com.graphhopper.util.Helper;
import com.graphhopper.util.InstructionList;
import com.graphhopper.util.PointList;
import com.graphhopper.util.exceptions.GHException;
import com.graphhopper.util.shapes.BBox;

import java.text.NumberFormat;
import java.util.*;

/**
 * @author Peter Karich
 */
public class SimpleRouteSerializer implements RouteSerializer {
    private final BBox maxBounds;

    public SimpleRouteSerializer(BBox maxBounds) {
        this.maxBounds = maxBounds;
    }

    private String getMessage(Throwable t) {
        if (t.getMessage() == null)
            return t.getClass().getSimpleName();
        else
            return t.getMessage();
    }

    @Override
    public Map<String, Object> toJSON(GHResponse rsp,
                                      boolean calcPoints, boolean pointsEncoded,
                                      boolean includeElevation, boolean enableInstructions) {
        Map<String, Object> json = new HashMap<String, Object>();

        if (rsp.hasErrors()) {
            json.put("message", getMessage(rsp.getErrors().get(0)));
            List<Map<String, Object>> errorHintList = new ArrayList<>();
            for (Throwable t : rsp.getErrors()) {
                Map<String, Object> map = new HashMap<>();
                map.put("message", getMessage(t));
                map.put("details", t.getClass().getName());
                if (t instanceof GHException) {
                    map.putAll(((GHException) t).getDetails());
                }
                errorHintList.add(map);
            }
            json.put("hints", errorHintList);
        } else {
            Map<String, Object> jsonInfo = new HashMap<String, Object>();
            json.put("info", jsonInfo);
            json.put("hints", rsp.getHints().toMap());
            // If you replace GraphHopper with your own brand name, this is fine. 
            // Still it would be highly appreciated if you mention us in your about page!
            jsonInfo.put("copyrights", Arrays.asList("GraphHopper", "OpenStreetMap contributors"));

            List<Map<String, Object>> jsonPathList = new ArrayList<Map<String, Object>>();
            for (PathWrapper ar : rsp.getAll()) {
                Map<String, Object> jsonPath = new HashMap<String, Object>();
                jsonPath.put("distance", Helper.round(ar.getDistance(), 3));
                jsonPath.put("weight", Helper.round6(ar.getRouteWeight()));
                jsonPath.put("time", ar.getTime());
                jsonPath.put("transfers", ar.getNumChanges());
                if (!ar.getDescription().isEmpty())
                    jsonPath.put("description", ar.getDescription());

                if (calcPoints) {
                    jsonPath.put("points_encoded", pointsEncoded);

                    PointList points = ar.getPoints();
                    if (points.getSize() >= 2) {
                        BBox maxBounds2D = new BBox(maxBounds.minLon, maxBounds.maxLon, maxBounds.minLat, maxBounds.maxLat);
                        jsonPath.put("bbox", ar.calcRouteBBox(maxBounds2D).toGeoJson());
                    }

                    jsonPath.put("points", createPoints(points, pointsEncoded, includeElevation));

                    if (enableInstructions) {
                        InstructionList instructions = ar.getInstructions();
                        jsonPath.put("instructions", instructions.createJson());
                    }

                    jsonPath.put("legs", ar.getLegs());

                    jsonPath.put("ascend", ar.getAscend());
                    jsonPath.put("descend", ar.getDescend());
                }

                jsonPath.put("snapped_waypoints", createPoints(ar.getWaypoints(), pointsEncoded, includeElevation));
                if (ar.getFare() != null) {
                    jsonPath.put("fare", NumberFormat.getCurrencyInstance().format(ar.getFare()));
                }
                jsonPathList.add(jsonPath);
            }

            json.put("paths", jsonPathList);
        }
        return json;
    }

    @Override
    public Object createPoints(PointList points, boolean pointsEncoded, boolean includeElevation) {
        if (pointsEncoded)
            return WebHelper.encodePolyline(points, includeElevation);

        Map<String, Object> jsonPoints = new HashMap<String, Object>();
        jsonPoints.put("type", "LineString");
        jsonPoints.put("coordinates", points.toGeoJson(includeElevation));
        return jsonPoints;
    }
}
