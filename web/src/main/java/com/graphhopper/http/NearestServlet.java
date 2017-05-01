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

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.Helper;
import com.graphhopper.util.shapes.GHPoint;
import com.graphhopper.util.shapes.GHPoint3D;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author svantulden
 */
public class NearestServlet extends GHBaseServlet {
    private final DistanceCalc calc = Helper.DIST_EARTH;
    @Inject
    private LocationIndex index;
    @Inject
    @Named("hasElevation")
    private boolean hasElevation;

    @Override
    public void doGet(HttpServletRequest httpReq, HttpServletResponse httpRes) throws ServletException, IOException {
        String pointStr = getParam(httpReq, "point", null);
        boolean enabledElevation = getBooleanParam(httpReq, "elevation", false);

        ObjectNode result = jsonNodeFactory.objectNode();
        if (pointStr != null && !pointStr.equalsIgnoreCase("")) {
            GHPoint place = GHPoint.parse(pointStr);
            QueryResult qr = index.findClosest(place.lat, place.lon, EdgeFilter.ALL_EDGES);

            if (!qr.isValid()) {
                result.put("error", "Nearest point cannot be found!");
            } else {
                GHPoint3D snappedPoint = qr.getSnappedPoint();
                result.put("type", "Point");

                ArrayNode coord = result.putArray("coordinates");
                coord.add(snappedPoint.lon);
                coord.add(snappedPoint.lat);

                if (hasElevation && enabledElevation)
                    coord.add(snappedPoint.ele);

                // Distance from input to snapped point in meters
                result.put("distance", calc.calcDist(place.lat, place.lon, snappedPoint.lat, snappedPoint.lon));
            }
        } else {
            result.put("error", "No lat/lon specified!");
        }

        writeJson(httpReq, httpRes, result);
    }
}
