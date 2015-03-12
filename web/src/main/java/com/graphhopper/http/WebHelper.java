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
import com.graphhopper.wrapper.*;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Code which handles polyline encoding and other web stuff.
 * <p>
 * The necessary information for polyline encoding is in this answer:
 * http://stackoverflow.com/a/24510799/194609 with a link to official Java sources as well as to a
 * good explanation.
 * <p>
 * @author Peter Karich
 */
public class WebHelper
{
    public static String encodeURL( String str )
    {
        try
        {
            return URLEncoder.encode(str, "UTF-8");
        } catch (Exception _ignore)
        {
            return str;
        }
    }

    public static PointList decodePolyline( String encoded, int initCap, boolean is3D )
    {
        PointList poly = new PointList(initCap, is3D);
        int index = 0;
        int len = encoded.length();
        int lat = 0, lng = 0, ele = 0;
        while (index < len)
        {
            // latitude
            int b, shift = 0, result = 0;
            do
            {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int deltaLatitude = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += deltaLatitude;

            // longitute
            shift = 0;
            result = 0;
            do
            {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int deltaLongitude = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += deltaLongitude;

            if (is3D)
            {
                // elevation
                shift = 0;
                result = 0;
                do
                {
                    b = encoded.charAt(index++) - 63;
                    result |= (b & 0x1f) << shift;
                    shift += 5;
                } while (b >= 0x20);
                int deltaElevation = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
                ele += deltaElevation;
                poly.add((double) lat / 1e5, (double) lng / 1e5, (double) ele / 100);
            } else
                poly.add((double) lat / 1e5, (double) lng / 1e5);
        }
        return poly;
    }

    public static String encodePolyline( PointList poly )
    {
        if (poly.isEmpty())
            return "";

        return encodePolyline(poly, poly.is3D());
    }

    public static String encodePolyline( PointList poly, boolean includeElevation )
    {
        StringBuilder sb = new StringBuilder();
        int size = poly.getSize();
        int prevLat = 0;
        int prevLon = 0;
        int prevEle = 0;
        for (int i = 0; i < size; i++)
        {
            int num = (int) Math.floor(poly.getLatitude(i) * 1e5);
            encodeNumber(sb, num - prevLat);
            prevLat = num;
            num = (int) Math.floor(poly.getLongitude(i) * 1e5);
            encodeNumber(sb, num - prevLon);
            prevLon = num;
            if (includeElevation)
            {
                num = (int) Math.floor(poly.getElevation(i) * 100);
                encodeNumber(sb, num - prevEle);
                prevEle = num;
            }
        }
        return sb.toString();
    }

    private static void encodeNumber( StringBuilder sb, int num )
    {
        num = num << 1;
        if (num < 0)
        {
            num = ~num;
        }
        while (num >= 0x20)
        {
            int nextValue = (0x20 | (num & 0x1f)) + 63;
            sb.append((char) (nextValue));
            num >>= 5;
        }
        num += 63;
        sb.append((char) (num));
    }

    public static String readString( InputStream inputStream ) throws IOException
    {
        String encoding = "UTF-8";
        InputStream in = new BufferedInputStream(inputStream, 4096);
        try
        {
            byte[] buffer = new byte[4096];
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            int numRead;
            while ((numRead = in.read(buffer)) != -1)
            {
                output.write(buffer, 0, numRead);
            }
            return output.toString(encoding);
        } finally
        {
            in.close();
        }
    }

    public static GHRestResponse wrapResponse(GHResponse response, boolean pointsEncoded, boolean includeElevation) {
        GHRestResponse restResponse = new GHRestResponse();

        // Note: info.took is established from the request, therefore here is always set to -1
        restResponse.setInfo(new InfoBean());

        // Info.Errors[]
        if(response.hasErrors()) {
            restResponse.getInfo().setErrors(new ArrayList<ErrorBean>());
            for (Throwable throwable : response.getErrors()) {
                ErrorBean error = new ErrorBean();
                error.setDetails(throwable.getClass().getName());
                error.setMessage(throwable.getMessage());
                restResponse.getInfo().getErrors().add(error);
            }
        } else {
            PathsBean paths = new PathsBean();
            restResponse.setPaths(paths);

            paths.setDistance(Helper.round(response.getDistance(), 3));
            paths.setWeight(Helper.round6(response.getDistance()));
            paths.setTime(response.getMillis());

            // Paths.Points[]
            PointList responsePoints = response.getPoints();
            if (responsePoints != null && !responsePoints.isEmpty()) {

                // Paths.Bbox
                if (responsePoints.getSize() >= 2) {
                    // Note: The calcRouteBBox fallback is actually ignored ('cause the wrapping is GraphHopperAPI unaware).
                    paths.setBbox(response.calcRouteBBox(new BBox(0, 0, 0, 0)).toGeoJson());
                }

                // Paths.Points Encoding
                if (pointsEncoded) {
                    // Note: original points_encoded is a boolean, here it containts the polyline string directly
                    paths.setPointsEncoded(WebHelper.encodePolyline(response.getPoints(), includeElevation));
                } else {
                    paths.setPoints(new PointsBean());
                    paths.getPoints().setType("LineString");
                    paths.getPoints().setCoordinates(response.getPoints().toGeoJson(includeElevation));
                }

                // Paths.Instructions[]
                InstructionList responseInstructions = response.getInstructions();
                if (responseInstructions != null && !responseInstructions.isEmpty()) {
                    paths.setInstructions(new ArrayList<InstructionBean>(responseInstructions.size()));

                    for (Map<String, Object> jsonInstruction : responseInstructions.createJson()) {
                        InstructionBean instruction = new InstructionBean();
                        instruction.setDistance((Double) jsonInstruction.get("distance"));
                        instruction.setSign((Integer) jsonInstruction.get("sign"));
                        instruction.setTime((Long) jsonInstruction.get("time"));
                        instruction.setText((String) jsonInstruction.get("text"));

                        //noinspection unchecked
                        instruction.setInterval((List<Integer>) jsonInstruction.get("interval"));

                        if (jsonInstruction.containsKey("annotation_text") && jsonInstruction.containsKey("annotation_importance")) {
                            instruction.setAnnotationText((String) jsonInstruction.get("annotation_text"));
                            instruction.setAnnotationImportance((Integer) jsonInstruction.get("annotation_importance"));
                        }

                        paths.getInstructions().add(instruction);
                    }
                }
            }
        }

        return restResponse;
    }
}
