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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.ResponsePath;
import com.graphhopper.util.*;
import com.graphhopper.util.details.PathDetail;
import com.graphhopper.util.exceptions.*;
import org.locationtech.jts.geom.LineString;

import java.util.*;

public class ResponsePathDeserializerHelper {

    public static ResponsePath createResponsePath(ObjectMapper objectMapper, JsonNode path, boolean hasElevation, boolean turnDescription) {
        ResponsePath responsePath = new ResponsePath();
        responsePath.addErrors(readErrors(objectMapper, path));
        if (responsePath.hasErrors())
            return responsePath;

        // Read multiplier from JSON to properly decode the "points" array.
        // Note, in earlier versions points_encoded was missing in JSON for calc_points == false and still required for snapped_waypoints
        double multiplier = 1e5;
        if (path.has("points_encoded") && path.get("points_encoded").asBoolean() && path.has("points_encoded_multiplier"))
            multiplier = path.get("points_encoded_multiplier").asDouble();

        if (path.has("snapped_waypoints")) {
            JsonNode snappedWaypoints = path.get("snapped_waypoints");
            PointList snappedPoints = deserializePointList(objectMapper, snappedWaypoints, hasElevation, multiplier);
            responsePath.setWaypoints(snappedPoints);
        }

        if (path.has("ascend")) {
            responsePath.setAscend(path.get("ascend").asDouble());
        }
        if (path.has("descend")) {
            responsePath.setDescend(path.get("descend").asDouble());
        }
        if (path.has("weight")) {
            responsePath.setRouteWeight(path.get("weight").asDouble());
        }
        if (path.has("description")) {
            JsonNode descriptionNode = path.get("description");
            if (descriptionNode.isArray()) {
                List<String> description = new ArrayList<>(descriptionNode.size());
                for (JsonNode descNode : descriptionNode) {
                    description.add(descNode.asText());
                }
                responsePath.setDescription(description);
            } else {
                throw new IllegalStateException("Description has to be an array");
            }
        }

        if (path.has("points")) {
            final PointList pointList = deserializePointList(objectMapper, path.get("points"), hasElevation, multiplier);
            responsePath.setPoints(pointList);

            if (path.has("instructions")) {
                JsonNode instrArr = path.get("instructions");

                InstructionList il = new InstructionList(null);
                int viaCount = 1;
                for (JsonNode jsonObj : instrArr) {
                    double instDist = jsonObj.get("distance").asDouble();
                    String text = turnDescription ? jsonObj.get("text").asText() : jsonObj.get(Parameters.Details.STREET_NAME).asText();
                    long instTime = jsonObj.get("time").asLong();
                    int sign = jsonObj.get("sign").asInt();
                    JsonNode iv = jsonObj.get("interval");
                    int from = iv.get(0).asInt();
                    int to = iv.get(1).asInt();
                    PointList instPL = new PointList(to - from, hasElevation);
                    for (int j = from; j <= to; j++) {
                        instPL.add(pointList, j);
                    }

                    Instruction instr;
                    if (sign == Instruction.USE_ROUNDABOUT || sign == Instruction.LEAVE_ROUNDABOUT) {
                        RoundaboutInstruction ri = new RoundaboutInstruction(sign, text, instPL);

                        if (jsonObj.has("exit_number")) {
                            ri.setExitNumber(jsonObj.get("exit_number").asInt());
                        }

                        if (jsonObj.has("exited")) {
                            if (jsonObj.get("exited").asBoolean())
                                ri.setExited();
                        }

                        if (jsonObj.has("turn_angle")) {
                            // TODO provide setTurnAngle setter
                            double angle = jsonObj.get("turn_angle").asDouble();
                            ri.setDirOfRotation(angle);
                            ri.setRadian((angle < 0 ? -Math.PI : Math.PI) - angle);
                        }

                        instr = ri;
                    } else if (sign == Instruction.REACHED_VIA) {
                        ViaInstruction tmpInstr = new ViaInstruction(text, instPL);
                        tmpInstr.setViaCount(viaCount);
                        viaCount++;
                        instr = tmpInstr;
                    } else if (sign == Instruction.FINISH) {
                        instr = new FinishInstruction(text, instPL, 0);
                    } else {
                        instr = new Instruction(sign, text, instPL);
                        if (sign == Instruction.CONTINUE_ON_STREET) {
                            if (jsonObj.has("heading")) {
                                instr.setExtraInfo("heading", jsonObj.get("heading").asDouble());
                            }
                        }
                    }

                    // Usually, the translation is done from the routing service so just use the provided string
                    // instead of creating a combination with sign and name etc.
                    // This is called the turn description.
                    // This can be changed by passing <code>turn_description=false</code>.
                    if (turnDescription)
                        instr.setUseRawName();

                    instr.setDistance(instDist).setTime(instTime);
                    il.add(instr);
                }
                responsePath.setInstructions(il);
            }

            if (path.has("details")) {
                JsonNode details = path.get("details");
                Map<String, List<PathDetail>> pathDetails = new HashMap<>(details.size());
                Iterator<Map.Entry<String, JsonNode>> detailIterator = details.fields();
                while (detailIterator.hasNext()) {
                    Map.Entry<String, JsonNode> detailEntry = detailIterator.next();
                    List<PathDetail> pathDetailList = new ArrayList<>();
                    for (JsonNode pathDetail : detailEntry.getValue()) {
                        PathDetail pd = objectMapper.convertValue(pathDetail, PathDetail.class);
                        pathDetailList.add(pd);
                    }
                    pathDetails.put(detailEntry.getKey(), pathDetailList);
                }
                responsePath.addPathDetails(pathDetails);
            }
        }

        if (path.has("points_order")) {
            responsePath.setPointsOrder((List<Integer>) objectMapper.convertValue(path.get("points_order"), List.class));
        } else {
            List<Integer> list = new ArrayList<>(responsePath.getWaypoints().size());
            for (int i = 0; i < responsePath.getWaypoints().size(); i++) {
                list.add(i);
            }
            responsePath.setPointsOrder(list);
        }

        double distance = path.get("distance").asDouble();
        long time = path.get("time").asLong();
        responsePath.setDistance(distance).setTime(time);
        return responsePath;
    }

    private static PointList deserializePointList(ObjectMapper objectMapper, JsonNode jsonNode, boolean hasElevation, double multiplier) {
        PointList snappedPoints;
        if (jsonNode.isTextual()) {
            snappedPoints = decodePolyline(jsonNode.asText(), Math.max(10, jsonNode.asText().length() / 4), hasElevation, multiplier);
        } else {
            LineString lineString = objectMapper.convertValue(jsonNode, LineString.class);
            snappedPoints = PointList.fromLineString(lineString);
        }
        return snappedPoints;
    }

    public static PointList decodePolyline(String encoded, int initCap, boolean is3D, double multiplier) {
        if (multiplier < 1)
            throw new IllegalArgumentException("multiplier cannot be smaller than 1 but was " + multiplier + " for polyline " + encoded);

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

            // longitude
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
                poly.add((double) lat / multiplier, (double) lng / multiplier, (double) ele / 100);
            } else
                poly.add((double) lat / multiplier, (double) lng / multiplier);
        }
        return poly;
    }

    public static List<Throwable> readErrors(ObjectMapper objectMapper, JsonNode json) {
        List<Throwable> errors = new ArrayList<>();
        JsonNode errorJson;

        if (json.has("message")) {
            if (json.has("hints")) {
                errorJson = json.get("hints");
            } else {
                // should not happen
                errors.add(new RuntimeException(json.get("message").asText()));
                return errors;
            }
        } else
            return errors;

        for (JsonNode error : errorJson) {
            String exClass = "";
            if (error.has("details"))
                exClass = error.get("details").asText();

            String exMessage = error.get("message").asText();

            if (exClass.equals(UnsupportedOperationException.class.getName()))
                errors.add(new UnsupportedOperationException(exMessage));
            else if (exClass.equals(IllegalStateException.class.getName()))
                errors.add(new IllegalStateException(exMessage));
            else if (exClass.equals(RuntimeException.class.getName()))
                errors.add(new DetailedRuntimeException(exMessage, toMap(objectMapper, error)));
            else if (exClass.equals(IllegalArgumentException.class.getName()))
                errors.add(new DetailedIllegalArgumentException(exMessage, toMap(objectMapper, error)));
            else if (exClass.equals(ConnectionNotFoundException.class.getName())) {
                errors.add(new ConnectionNotFoundException(exMessage, toMap(objectMapper, error)));
            } else if (exClass.equals(MaximumNodesExceededException.class.getName())) {
                int maxVisitedNodes = error.get(MaximumNodesExceededException.NODES_KEY).asInt();
                errors.add(new MaximumNodesExceededException(exMessage, maxVisitedNodes));
            } else if (exClass.equals(PointNotFoundException.class.getName())) {
                int pointIndex = error.get(PointNotFoundException.INDEX_KEY).asInt();
                errors.add(new PointNotFoundException(exMessage, pointIndex));
            } else if (exClass.equals(PointOutOfBoundsException.class.getName())) {
                int pointIndex = error.get(PointNotFoundException.INDEX_KEY).asInt();
                errors.add(new PointOutOfBoundsException(exMessage, pointIndex));
            } else if (exClass.isEmpty())
                errors.add(new DetailedRuntimeException(exMessage, toMap(objectMapper, error)));
            else
                errors.add(new DetailedRuntimeException(exClass + " " + exMessage, toMap(objectMapper, error)));
        }

        if (json.has("message") && errors.isEmpty())
            errors.add(new RuntimeException(json.get("message").asText()));

        return errors;
    }

    // Credits to: http://stackoverflow.com/a/24012023/194609
    private static Map<String, Object> toMap(ObjectMapper objectMapper, JsonNode object) {
        return objectMapper.convertValue(object, new TypeReference<Map<String, Object>>() {
        });
    }

}
