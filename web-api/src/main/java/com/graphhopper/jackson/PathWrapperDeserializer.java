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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.PathWrapper;
import com.graphhopper.http.WebHelper;
import com.graphhopper.util.*;
import com.graphhopper.util.details.PathDetail;
import com.graphhopper.util.exceptions.*;
import org.locationtech.jts.geom.LineString;

import java.io.IOException;
import java.util.*;

public class PathWrapperDeserializer extends JsonDeserializer<PathWrapper> {
    @Override
    public PathWrapper deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        return createPathWrapper((ObjectMapper) p.getCodec(), p.readValueAsTree(), false, true);
    }

    public static PathWrapper createPathWrapper(ObjectMapper objectMapper, JsonNode path, boolean hasElevation, boolean turnDescription) {
        PathWrapper pathWrapper = new PathWrapper();
        pathWrapper.addErrors(readErrors(objectMapper, path));
        if (pathWrapper.hasErrors())
            return pathWrapper;

        if (path.has("snapped_waypoints")) {
            JsonNode snappedWaypoints = path.get("snapped_waypoints");
            PointList snappedPoints = deserializePointList(objectMapper, snappedWaypoints, hasElevation);
            pathWrapper.setWaypoints(snappedPoints);
        }

        if (path.has("ascend")) {
            pathWrapper.setAscend(path.get("ascend").asDouble());
        }
        if (path.has("descend")) {
            pathWrapper.setDescend(path.get("descend").asDouble());
        }
        if (path.has("weight")) {
            pathWrapper.setRouteWeight(path.get("weight").asDouble());
        }
        if (path.has("description")) {
            JsonNode descriptionNode = path.get("description");
            if (descriptionNode.isArray()) {
                List<String> description = new ArrayList<>(descriptionNode.size());
                for (JsonNode descNode : descriptionNode) {
                    description.add(descNode.asText());
                }
                pathWrapper.setDescription(description);
            } else {
                throw new IllegalStateException("Description has to be an array");
            }
        }

        if (path.has("points")) {
            final PointList pointList = deserializePointList(objectMapper, path.get("points"), hasElevation);
            pathWrapper.setPoints(pointList);

            if (path.has("instructions")) {
                JsonNode instrArr = path.get("instructions");

                InstructionList il = new InstructionList(null);
                int viaCount = 1;
                for (JsonNode jsonObj : instrArr) {
                    double instDist = jsonObj.get("distance").asDouble();
                    String text = turnDescription ? jsonObj.get("text").asText() : jsonObj.get("street_name").asText();
                    long instTime = jsonObj.get("time").asLong();
                    int sign = jsonObj.get("sign").asInt();
                    JsonNode iv = jsonObj.get("interval");
                    int from = iv.get(0).asInt();
                    int to = iv.get(1).asInt();
                    PointList instPL = new PointList(to - from, hasElevation);
                    for (int j = from; j <= to; j++) {
                        instPL.add(pointList, j);
                    }

                    InstructionAnnotation ia = InstructionAnnotation.EMPTY;
                    if (jsonObj.has("annotation_importance") && jsonObj.has("annotation_text")) {
                        ia = new InstructionAnnotation(jsonObj.get("annotation_importance").asInt(), jsonObj.get("annotation_text").asText());
                    }

                    Instruction instr;
                    if (sign == Instruction.USE_ROUNDABOUT || sign == Instruction.LEAVE_ROUNDABOUT) {
                        RoundaboutInstruction ri = new RoundaboutInstruction(sign, text, ia, instPL);

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
                        ViaInstruction tmpInstr = new ViaInstruction(text, ia, instPL);
                        tmpInstr.setViaCount(viaCount);
                        viaCount++;
                        instr = tmpInstr;
                    } else if (sign == Instruction.FINISH) {
                        instr = new FinishInstruction(text, instPL, 0);
                    } else {
                        instr = new Instruction(sign, text, ia, instPL);
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
                pathWrapper.setInstructions(il);
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
                pathWrapper.addPathDetails(pathDetails);
            }
        }

        double distance = path.get("distance").asDouble();
        long time = path.get("time").asLong();
        pathWrapper.setDistance(distance).setTime(time);
        return pathWrapper;
    }

    private static PointList deserializePointList(ObjectMapper objectMapper, JsonNode jsonNode, boolean hasElevation) {
        PointList snappedPoints;
        if (jsonNode.isTextual()) {
            snappedPoints = WebHelper.decodePolyline(jsonNode.asText(), 5, hasElevation);
        } else {
            LineString lineString = objectMapper.convertValue(jsonNode, LineString.class);
            snappedPoints = PointList.fromLineString(lineString);
        }
        return snappedPoints;
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
            } else if (exClass.equals(PointNotFoundException.class.getName())) {
                int pointIndex = error.get("point_index").asInt();
                errors.add(new PointNotFoundException(exMessage, pointIndex));
            } else if (exClass.equals(PointOutOfBoundsException.class.getName())) {
                int pointIndex = error.get("point_index").asInt();
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
