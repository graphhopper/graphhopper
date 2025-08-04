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

    public static String encodePolyline(PointList poly, boolean includeElevation, double multiplier) {
        if (multiplier < 1)
            throw new IllegalArgumentException("multiplier cannot be smaller than 1 but was " + multiplier + " for polyline");

        StringBuilder sb = new StringBuilder(Math.max(20, poly.size() * 3));
        int size = poly.size();
        int prevLat = 0;
        int prevLon = 0;
        int prevEle = 0;
        for (int i = 0; i < size; i++) {
            int num = (int) Math.round(poly.getLat(i) * multiplier);
            encodeNumber(sb, num - prevLat);
            prevLat = num;
            num = (int) Math.round(poly.getLon(i) * multiplier);
            encodeNumber(sb, num - prevLon);
            prevLon = num;
            if (includeElevation) {
                num = (int) Math.round(poly.getEle(i) * 100);
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

    public record Info(List<String> copyrights, long took, String roadDataTimestamp) {
    }

    public static ObjectNode jsonObject(GHResponse ghRsp, Info info, boolean enableInstructions,
                                        boolean calcPoints, boolean enableElevation, boolean pointsEncoded, double pointsMultiplier) {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
        json.putPOJO("hints", ghRsp.getHints().toMap());
        json.putPOJO("info", info);
        ArrayNode jsonPathList = json.putArray("paths");
        for (ResponsePath p : ghRsp.getAll()) {
            ObjectNode jsonPath = jsonPathList.addObject();
            jsonPath.put("distance", Helper.round(p.getDistance(), 3));
            jsonPath.put("weight", Helper.round6(p.getRouteWeight()));
            jsonPath.put("time", p.getTime());
            jsonPath.put("transfers", p.getNumChanges());
            jsonPath.putPOJO("legs", p.getLegs());
            if (!p.getDescription().isEmpty()) {
                jsonPath.putPOJO("description", p.getDescription());
            }

            // for points and snapped_waypoints:
            jsonPath.put("points_encoded", pointsEncoded);
            if (pointsEncoded) jsonPath.put("points_encoded_multiplier", pointsMultiplier);

            if (calcPoints) {
                jsonPath.putPOJO("bbox", p.calcBBox2D());
                jsonPath.putPOJO("points", pointsEncoded ? encodePolyline(p.getPoints(), enableElevation, pointsMultiplier) : p.getPoints().toLineString(enableElevation));
                if (enableInstructions) {
                    jsonPath.putPOJO("instructions", p.getInstructions());
                }
                jsonPath.putPOJO("details", p.getPathDetails());
                jsonPath.put("ascend", p.getAscend());
                jsonPath.put("descend", p.getDescend());
            }
            jsonPath.putPOJO("snapped_waypoints", pointsEncoded ? encodePolyline(p.getWaypoints(), enableElevation, pointsMultiplier) : p.getWaypoints().toLineString(enableElevation));
            if (p.getFare() != null) {
                jsonPath.put("fare", NumberFormat.getCurrencyInstance(Locale.ROOT).format(p.getFare()));
            }
        }
        return json;
    }
}
