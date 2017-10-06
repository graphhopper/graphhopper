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
package com.graphhopper.api;

import com.graphhopper.util.PointList;

import java.net.URLEncoder;

/**
 * Code which handles polyline encoding and other web stuff.
 * <p>
 *
 * @author Peter Karich
 */
public class WebHelper {

    public static String encodeURL(String str) {
        try {
            return URLEncoder.encode(str, "UTF-8");
        } catch (Exception _ignore) {
            return str;
        }
    }

    public static PointList decodePolyline(String encoded, int initCap, boolean is3D) {
        PointList poly = new PointList(initCap, is3D);
        int index = 0;
        int len = encoded.length();
        int lat = 0, lng = 0, ele = 0;
        while (index < len) {
            // latitude
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int deltaLatitude = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += deltaLatitude;

            // longitute
            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int deltaLongitude = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += deltaLongitude;

            if (is3D) {
                // elevation
                shift = 0;
                result = 0;
                do {
                    b = encoded.charAt(index++) - 63;
                    result |= (b & 0x1f) << shift;
                    shift += 5;
                } while (b >= 0x20);
                int deltaElevation = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
                ele += deltaElevation;
                poly.add((double) lat / 1e5, (double) lng / 1e5, (double) ele / 100);
            } else {
                poly.add((double) lat / 1e5, (double) lng / 1e5);
            }
        }
        return poly;
    }
}
