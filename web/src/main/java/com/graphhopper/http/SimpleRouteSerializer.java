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

import com.graphhopper.GHResponse;
import com.graphhopper.util.Helper;
import com.graphhopper.util.InstructionList;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.BBox;
import java.util.*;

/**
 *
 * @author Peter Karich
 */
public class SimpleRouteSerializer implements RouteSerializer
{
    private final BBox maxBounds;

    public SimpleRouteSerializer( BBox maxBounds )
    {
        this.maxBounds = maxBounds;
    }

    @Override
    public Map<String, Object> toJSON( GHResponse rsp,
                                       boolean calcPoints, boolean pointsEncoded,
                                       boolean includeElevation, boolean enableInstructions )
    {
        Map<String, Object> json = new HashMap<String, Object>();

        if (rsp.hasErrors())
        {
            json.put("message", rsp.getErrors().get(0).getMessage());
            List<Map<String, String>> errorHintList = new ArrayList<Map<String, String>>();
            for (Throwable t : rsp.getErrors())
            {
                Map<String, String> map = new HashMap<String, String>();
                map.put("message", t.getMessage());
                map.put("details", t.getClass().getName());
                errorHintList.add(map);
            }
            json.put("hints", errorHintList);
        } else
        {
            Map<String, Object> jsonInfo = new HashMap<String, Object>();
            json.put("info", jsonInfo);
            json.put("hints", rsp.getHints().toMap());
            jsonInfo.put("copyrights", Arrays.asList("GraphHopper", "OpenStreetMap contributors"));
            Map<String, Object> jsonPath = new HashMap<String, Object>();
            jsonPath.put("distance", Helper.round(rsp.getDistance(), 3));
            jsonPath.put("weight", Helper.round6(rsp.getDistance()));
            jsonPath.put("time", rsp.getTime());

            if (calcPoints)
            {
                jsonPath.put("points_encoded", pointsEncoded);

                PointList points = rsp.getPoints();
                if (points.getSize() >= 2)
                {
                    BBox maxBounds2D = new BBox(maxBounds.minLon, maxBounds.maxLon, maxBounds.minLat, maxBounds.maxLat);
                    jsonPath.put("bbox", rsp.calcRouteBBox(maxBounds2D).toGeoJson());
                }

                jsonPath.put("points", createPoints(points, pointsEncoded, includeElevation));

                if (enableInstructions)
                {
                    InstructionList instructions = rsp.getInstructions();
                    jsonPath.put("instructions", instructions.createJson());
                }
            }
            json.put("paths", Collections.singletonList(jsonPath));
        }
        return json;
    }

    @Override
    public Object createPoints( PointList points, boolean pointsEncoded, boolean includeElevation )
    {
        if (pointsEncoded)
            return WebHelper.encodePolyline(points, includeElevation);

        Map<String, Object> jsonPoints = new HashMap<String, Object>();
        jsonPoints.put("type", "LineString");
        jsonPoints.put("coordinates", points.toGeoJson(includeElevation));
        return jsonPoints;
    }
}
