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

package com.graphhopper.jackson;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphhopper.GHResponse;
import com.graphhopper.ResponsePath;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PointList;

import java.text.NumberFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Code which constructs the JSON response of the routing API, including polyline encoding.
 * <p>
 * The necessary information for polyline encoding is in this answer:
 * http://stackoverflow.com/a/24510799/194609 with a link to official Java sources as well as to a
 * good explanation.
 * <p>
 *
 * @author Peter Karich
 */
public class ResponsePathSerializer {

    /**
     * This includes the required attribution for OpenStreetMap.
     * Do not hesitate to  mention us and link us in your about page
     * https://support.graphhopper.com/support/search/solutions?term=attribution
     */
    public static final List<String> COPYRIGHTS = Arrays.asList("GraphHopper", "OpenStreetMap contributors");

    public static String encodePolyline(PointList poly, boolean includeElevation, double precision) {
        StringBuilder sb = new StringBuilder(Math.max(20, poly.size() * 3));
        int size = poly.size();
        int prevLat = 0;
        int prevLon = 0;
        int prevEle = 0;
        for (int i = 0; i < size; i++) {
            int num = (int) Math.floor(poly.getLat(i) * precision);
            encodeNumber(sb, num - prevLat);
            prevLat = num;
            num = (int) Math.floor(poly.getLon(i) * precision);
            encodeNumber(sb, num - prevLon);
            prevLon = num;
            if (includeElevation) {
                num = (int) Math.floor(poly.getEle(i) * 100);
                encodeNumber(sb, num - prevEle);
                prevEle = num;
            }
        }
        return sb.toString();
    }

    private static void encodeNumber(StringBuilder sb, int num) {
        num = num << 1;
        if (num < 0) {
            num = ~num;
        }
        while (num >= 0x20) {
            int nextValue = (0x20 | (num & 0x1f)) + 63;
            sb.append((char) (nextValue));
            num >>= 5;
        }
        num += 63;
        sb.append((char) (num));
    }

    public static ObjectNode jsonObject(GHResponse ghRsp, boolean enableInstructions, boolean calcPoints, boolean enableElevation, boolean pointsEncoded, double took) {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
        json.putPOJO("hints", ghRsp.getHints().toMap());
        final ObjectNode info = json.putObject("info");
        info.putPOJO("copyrights", COPYRIGHTS);
        info.put("took", Math.round(took));
        ArrayNode jsonPathList = json.putArray("paths");
        for (ResponsePath p : ghRsp.getAll()) {
            ObjectNode jsonPath = jsonPathList.addObject();
            jsonPath.put("distance", Helper.round(p.getDistance(), 3));
            jsonPath.put("weight", Helper.round6(p.getRouteWeight()));
            jsonPath.put("time", p.getTime());
            jsonPath.put("transfers", p.getNumChanges());
            if (!p.getDescription().isEmpty()) {
                jsonPath.putPOJO("description", p.getDescription());
            }
            if (calcPoints) {
                jsonPath.put("points_encoded", pointsEncoded);
                jsonPath.putPOJO("bbox", p.calcBBox2D());
                jsonPath.putPOJO("points", pointsEncoded ? encodePolyline(p.getPoints(), enableElevation, 1e5) : p.getPoints().toLineString(enableElevation));
                jsonPath.putPOJO("waypoint_indices", getWaypointIndices(p.getWaypointIntervals()));
                if (enableInstructions) {
                    jsonPath.putPOJO("instructions", p.getInstructions());
                }
                jsonPath.putPOJO("legs", p.getLegs());
                jsonPath.putPOJO("details", p.getPathDetails());
                jsonPath.put("ascend", p.getAscend());
                jsonPath.put("descend", p.getDescend());
            }
            jsonPath.putPOJO("snapped_waypoints", pointsEncoded ? encodePolyline(p.getWaypoints(), enableElevation, 1e5) : p.getWaypoints().toLineString(enableElevation));
            if (p.getFare() != null) {
                jsonPath.put("fare", NumberFormat.getCurrencyInstance(Locale.ROOT).format(p.getFare()));
            }
        }
        return json;
    }

    public static int[] getWaypointIndices(List<ResponsePath.Interval> waypointIntervals) {
        if (waypointIntervals.isEmpty()) return new int[0];
        int[] result = new int[waypointIntervals.size() + 1];
        result[0] = waypointIntervals.get(0).start;
        for (int i = 0; i < waypointIntervals.size(); i++)
            result[i + 1] = waypointIntervals.get(i).end;
        return result;
    }
}
