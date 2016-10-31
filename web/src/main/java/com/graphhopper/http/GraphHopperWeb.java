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

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopperAPI;
import com.graphhopper.PathWrapper;
import com.graphhopper.util.*;
import com.graphhopper.util.exceptions.ConnectionNotFoundException;
import com.graphhopper.util.exceptions.DetailedIllegalArgumentException;
import com.graphhopper.util.exceptions.DetailedRuntimeException;
import com.graphhopper.util.exceptions.PointNotFoundException;
import com.graphhopper.util.exceptions.PointOutOfBoundsException;
import com.graphhopper.util.shapes.GHPoint;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.json.JSONException;

/**
 * Main wrapper of the GraphHopper Directions API for a simple and efficient usage.
 * <p>
 *
 * @author Peter Karich
 */
public class GraphHopperWeb implements GraphHopperAPI {
    private final Set<String> ignoreSet;
    private Downloader downloader = new Downloader("GraphHopper Java Client");
    private String routeServiceUrl = "https://graphhopper.com/api/1/route";
    private String key = "";
    private boolean instructions = true;
    private boolean calcPoints = true;
    private boolean elevation = false;

    public GraphHopperWeb() {
        // some parameters are supported directly via Java API so ignore them when writing the getHints map
        ignoreSet = new HashSet<String>();
        ignoreSet.add("calc_points");
        ignoreSet.add("calcpoints");
        ignoreSet.add("instructions");
        ignoreSet.add("elevation");
        ignoreSet.add("key");

        // some parameters are in the request:
        ignoreSet.add("algorithm");
        ignoreSet.add("locale");
        ignoreSet.add("point");
        ignoreSet.add("vehicle");

        // some are special and need to be avoided
        ignoreSet.add("points_encoded");
        ignoreSet.add("pointsencoded");
        ignoreSet.add("type");
    }

    public static PathWrapper createPathWrapper(JSONObject path,
                                                boolean tmpCalcPoints, boolean tmpInstructions, boolean tmpElevation) {
        PathWrapper pathWrapper = new PathWrapper();
        pathWrapper.addErrors(readErrors(path));
        if (pathWrapper.hasErrors())
            return pathWrapper;

        if (path.has("snapped_waypoints")) {
            String snappedPointStr = path.getString("snapped_waypoints");
            PointList snappedPoints = WebHelper.decodePolyline(snappedPointStr, 5, tmpElevation);
            pathWrapper.setWaypoints(snappedPoints);
        }

        if (tmpCalcPoints) {
            String pointStr = path.getString("points");
            PointList pointList = WebHelper.decodePolyline(pointStr, 100, tmpElevation);
            pathWrapper.setPoints(pointList);

            if (tmpInstructions) {
                JSONArray instrArr = path.getJSONArray("instructions");

                InstructionList il = new InstructionList(null);
                int viaCount = 1;
                for (int instrIndex = 0; instrIndex < instrArr.length(); instrIndex++) {
                    JSONObject jsonObj = instrArr.getJSONObject(instrIndex);
                    double instDist = jsonObj.getDouble("distance");
                    String text = jsonObj.getString("text");
                    long instTime = jsonObj.getLong("time");
                    int sign = jsonObj.getInt("sign");
                    JSONArray iv = jsonObj.getJSONArray("interval");
                    int from = iv.getInt(0);
                    int to = iv.getInt(1);
                    PointList instPL = new PointList(to - from, tmpElevation);
                    for (int j = from; j <= to; j++) {
                        instPL.add(pointList, j);
                    }

                    InstructionAnnotation ia = InstructionAnnotation.EMPTY;
                    if (jsonObj.has("annotation_importance") && jsonObj.has("annotation_text")) {
                        ia = new InstructionAnnotation(jsonObj.getInt("annotation_importance"), jsonObj.getString("annotation_text"));
                    }

                    Instruction instr;
                    if (sign == Instruction.USE_ROUNDABOUT || sign == Instruction.LEAVE_ROUNDABOUT) {
                        RoundaboutInstruction ri = new RoundaboutInstruction(sign, text, ia, instPL);

                        if (jsonObj.has("exit_number")) {
                            ri.setExitNumber(jsonObj.getInt("exit_number"));
                        }

                        if (jsonObj.has("turn_angle")) {
                            // TODO provide setTurnAngle setter
                            double angle = jsonObj.getDouble("turn_angle");
                            ri.setDirOfRotation(angle);
                            ri.setRadian((angle < 0 ? -Math.PI : Math.PI) - angle);
                        }

                        instr = ri;
                    } else if (sign == Instruction.REACHED_VIA) {
                        ViaInstruction tmpInstr = new ViaInstruction(text, ia, instPL);
                        tmpInstr.setViaCount(viaCount);
                        viaCount++;
                        instr = tmpInstr;
                    } else if (sign == Instruction.FINISH) {
                        instr = new FinishInstruction(instPL, 0);
                    } else {
                        instr = new Instruction(sign, text, ia, instPL);
                    }

                    // The translation is done from the routing service so just use the provided string
                    // instead of creating a combination with sign and name etc
                    instr.setUseRawName();

                    instr.setDistance(instDist).setTime(instTime);
                    il.add(instr);
                }
                pathWrapper.setInstructions(il);
            }
        }

        double distance = path.getDouble("distance");
        long time = path.getLong("time");
        pathWrapper.setDistance(distance).setTime(time);
        return pathWrapper;
    }

    // Credits to: http://stackoverflow.com/a/24012023/194609
    private static Map<String, Object> toMap(JSONObject object) throws JSONException {
        Map<String, Object> map = new HashMap<>(object.keySet().size());
        for (String key : object.keySet()) {
            Object value = object.get(key);
            if (value instanceof JSONArray) {
                value = toList((JSONArray) value);
            } else if (value instanceof JSONObject) {
                value = toMap((JSONObject) value);
            }
            map.put(key, value);
        }
        return map;
    }

    private static List<Object> toList(JSONArray array) throws JSONException {
        List<Object> list = new ArrayList<>();
        for (Object value : array) {
            if (value instanceof JSONArray) {
                value = toList((JSONArray) value);
            } else if (value instanceof JSONObject) {
                value = toMap((JSONObject) value);
            }
            list.add(value);
        }
        return list;
    }

    public static List<Throwable> readErrors(JSONObject json) {
        List<Throwable> errors = new ArrayList<>();
        JSONArray errorJson;

        if (json.has("message")) {
            if (json.has("hints")) {
                errorJson = json.getJSONArray("hints");
            } else {
                // should not happen
                errors.add(new RuntimeException(json.getString("message")));
                return errors;
            }
        } else
            return errors;

        for (int i = 0; i < errorJson.length(); i++) {
            JSONObject error = errorJson.getJSONObject(i);
            String exClass = "";
            if (error.has("details"))
                exClass = error.getString("details");

            String exMessage = error.getString("message");

            if (exClass.equals(UnsupportedOperationException.class.getName()))
                errors.add(new UnsupportedOperationException(exMessage));
            else if (exClass.equals(IllegalStateException.class.getName()))
                errors.add(new IllegalStateException(exMessage));
            else if (exClass.equals(RuntimeException.class.getName()))
                errors.add(new DetailedRuntimeException(exMessage, toMap(error)));
            else if (exClass.equals(IllegalArgumentException.class.getName()))
                errors.add(new DetailedIllegalArgumentException(exMessage, toMap(error)));
            else if (exClass.equals(ConnectionNotFoundException.class.getName())) {
                errors.add(new ConnectionNotFoundException(exMessage, toMap(error)));
            } else if (exClass.equals(PointNotFoundException.class.getName())) {
                int pointIndex = error.getInt("point_index");
                errors.add(new PointNotFoundException(exMessage, pointIndex));
            } else if (exClass.equals(PointOutOfBoundsException.class.getName())) {
                int pointIndex = error.getInt("point_index");
                errors.add(new PointOutOfBoundsException(exMessage, pointIndex));
            } else if (exClass.isEmpty())
                errors.add(new DetailedRuntimeException(exMessage, toMap(error)));
            else
                errors.add(new DetailedRuntimeException(exClass + " " + exMessage, toMap(error)));
        }

        if (json.has("message") && errors.isEmpty())
            errors.add(new RuntimeException(json.getString("message")));

        return errors;
    }

    public GraphHopperWeb setDownloader(Downloader downloader) {
        this.downloader = downloader;
        return this;
    }

    @Override
    public boolean load(String serviceUrl) {
        this.routeServiceUrl = serviceUrl;
        return true;
    }

    public GraphHopperWeb setKey(String key) {
        if (key == null || key.isEmpty())
            throw new IllegalStateException("Key cannot be empty");

        this.key = key;
        return this;
    }

    public GraphHopperWeb setCalcPoints(boolean calcPoints) {
        this.calcPoints = calcPoints;
        return this;
    }

    public GraphHopperWeb setInstructions(boolean b) {
        instructions = b;
        return this;
    }

    public GraphHopperWeb setElevation(boolean withElevation) {
        this.elevation = withElevation;
        return this;
    }

    @Override
    public GHResponse route(GHRequest request) {
        try {
            String places = "";
            for (GHPoint p : request.getPoints()) {
                places += "point=" + p.lat + "," + p.lon + "&";
            }

            boolean tmpInstructions = request.getHints().getBool("instructions", instructions);
            boolean tmpCalcPoints = request.getHints().getBool("calc_points", calcPoints);

            if (tmpInstructions && !tmpCalcPoints)
                throw new IllegalStateException("Cannot calculate instructions without points (only points without instructions). "
                        + "Use calc_points=false and instructions=false to disable point and instruction calculation");

            boolean tmpElevation = request.getHints().getBool("elevation", elevation);

            String url = routeServiceUrl
                    + "?"
                    + places
                    + "&type=json"
                    + "&instructions=" + tmpInstructions
                    + "&points_encoded=true"
                    + "&calc_points=" + tmpCalcPoints
                    + "&algorithm=" + request.getAlgorithm()
                    + "&locale=" + request.getLocale().toString()
                    + "&elevation=" + tmpElevation;

            if (!request.getVehicle().isEmpty())
                url += "&vehicle=" + request.getVehicle();

            if (!key.isEmpty())
                url += "&key=" + key;

            for (Entry<String, String> entry : request.getHints().toMap().entrySet()) {
                String urlKey = entry.getKey();
                String urlValue = entry.getValue();

                // use lower case conversion for check only!
                if (ignoreSet.contains(urlKey.toLowerCase()))
                    continue;

                if (urlValue != null && !urlValue.isEmpty())
                    url += "&" + WebHelper.encodeURL(urlKey) + "=" + WebHelper.encodeURL(urlValue);
            }

            String str = downloader.downloadAsString(url, true);
            JSONObject json = new JSONObject(str);

            GHResponse res = new GHResponse();
            res.addErrors(readErrors(json));
            if (res.hasErrors())
                return res;

            JSONArray paths = json.getJSONArray("paths");
            for (int index = 0; index < paths.length(); index++) {
                JSONObject path = paths.getJSONObject(index);
                PathWrapper altRsp = createPathWrapper(path, tmpCalcPoints, tmpInstructions, tmpElevation);
                res.add(altRsp);
            }

            return res;

        } catch (Exception ex) {
            throw new RuntimeException("Problem while fetching path " + request.getPoints() + ": " + ex.getMessage(), ex);
        }
    }
}
