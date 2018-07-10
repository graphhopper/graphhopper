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
package com.graphhopper.resources;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphhopper.GHResponse;
import com.graphhopper.PathWrapper;
import com.graphhopper.http.WebHelper;
import com.graphhopper.util.*;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class MapboxResponseConverter {

    // TODO: this is already in GraphHopper, but getting it from there is not possible easily?
    private static final TranslationMap trMap = new TranslationMap().doImport();


    /**
     * Converts a GHResponse into Mapbox compatible json
     */
    public static ObjectNode convertFromGHResponse(GHResponse ghResponse, Locale locale) {
        ObjectNode json = JsonNodeFactory.instance.objectNode();

        if (ghResponse.hasErrors()) {

        } else {

            PointList waypoints = ghResponse.getBest().getWaypoints();

            final ArrayNode routesJson = json.putArray("routes");

            List<PathWrapper> paths = ghResponse.getAll();

            for (int i = 0; i < paths.size(); i++) {
                PathWrapper path = paths.get(i);
                ObjectNode pathJson = routesJson.addObject();

                pathJson.put("geometry", WebHelper.encodePolyline(path.getPoints(), false, 1e6));
                ArrayNode legsJson = pathJson.putArray("legs");

                //TODO: leg support, multiple waypoints...
                ObjectNode legJson = legsJson.addObject();

                legJson.put("summary", "GraphHopper Route " + i);

                // Copied from below
                legJson.put("weight", path.getRouteWeight());
                legJson.put("duration", convertToSeconds(path.getTime()));
                legJson.put("distance", Helper.round2(path.getDistance()));

                ArrayNode steps = legJson.putArray("steps");
                InstructionList instructions = path.getInstructions();

                for (int j = 0; j < instructions.size(); j++) {
                    Instruction instruction = instructions.get(j);
                    ObjectNode instructionJson = steps.addObject();
                    instructionJson = fillInstruction(instruction, instructionJson, j, locale);
                }

                pathJson.put("weight_name", "routability");
                pathJson.put("weight", path.getRouteWeight());
                pathJson.put("duration", convertToSeconds(path.getTime()));
                pathJson.put("distance", Helper.round2(path.getDistance()));
            }

            final ArrayNode waypointsJson = json.putArray("waypoints");
            for (int i = 0; i < waypoints.size(); i++) {
                ObjectNode waypointJson = waypointsJson.addObject();
                // TODO get names?
                waypointJson.put("name", "");
                putLocation(waypoints.getLat(i), waypoints.getLon(i), waypointJson);
            }

            json.put("code", "Ok");
            // TODO: Maybe we need a different format... uuid: "cji4ja4f8004o6xrsta8w4p4h"
            json.put("uuid", UUID.randomUUID().toString().replaceAll("-", ""));
        }

        return json;
    }

    private static ObjectNode fillInstruction(Instruction instruction, ObjectNode instructionJson, int index, Locale locale) {
        ArrayNode intersections = instructionJson.putArray("intersections");
        ObjectNode intersection = intersections.addObject();
        intersection.put("out", 0);
        intersection.putArray("entry");
        intersection.putArray("bearings");
        PointList pointList = instruction.getPoints();
        putLocation(pointList.getLat(0), pointList.getLon(0), intersection);

        instructionJson.put("driving_side", "right");

        // Does not include elevation
        instructionJson.put("geometry", WebHelper.encodePolyline(pointList, false, 1e6));

        // TODO: how about other modes?
        instructionJson.put("mode", "driving");

        putManeuver(instruction, instructionJson, index, locale);

        // TODO distance = weight, is weight even important?
        double distance = Helper.round2(instruction.getDistance());
        instructionJson.put("weight", distance);
        instructionJson.put("duration", convertToSeconds(instruction.getTime()));
        instructionJson.put("name", instruction.getName());
        instructionJson.put("distance", distance);

        // TODO pass empty array, works but is not very nice
        ArrayNode voiceInstructions = instructionJson.putArray("voiceInstructions");
        ObjectNode voiceInstruction = voiceInstructions.addObject();

        /*
        {
            distanceAlongGeometry: 40.9,
            announcement: "Exit the traffic circle",
            ssmlAnnouncement: "<speak><amazon:effect name="drc"><prosody rate="1.08">Exit the traffic circle</prosody></amazon:effect></speak>",
        }
         */

        // Either speak 50m before the instruction or at distance/2 whatever is further down the road
        voiceInstruction.put("distanceAlongGeometry", Helper.round2(Math.max(distance / 2, distance - 50)));
        String turnDescription = instruction.getTurnDescription(trMap.getWithFallBack(locale));
        voiceInstruction.put("announcement", turnDescription);
        voiceInstruction.put("ssmlAnnouncement", "<speak><amazon:effect name=\"drc\"><prosody rate=\"1.08\">" + turnDescription + "</prosody></amazon:effect></speak>");

        return instructionJson;
    }

    /**
     * Relevant maneuver types are (find all here: https://www.mapbox.com/api-documentation/#maneuver-types):
     * depart (firs instruction)
     * turn (regular turns)
     * roundabout (enter roundabout, maneuver contains also the exit number)
     * exit roundabout (maneuver contains also the exit number)
     * arrive (last instruction and waypoints)
     *
     * No modifier values for arrive and depart
     *
     * Find modifier values here: https://www.mapbox.com/api-documentation/#stepmaneuver-object
     *
     */
    private static void putManeuver(Instruction instruction, ObjectNode instructionJson, int index, Locale locale) {
        ObjectNode maneuver = instructionJson.putObject("maneuver");
        maneuver.put("bearing_after", 0);
        maneuver.put("bearing_before", 0);

        PointList points = instruction.getPoints();
        putLocation(points.getLat(0), points.getLon(0), maneuver);

        // modifier
        switch (instruction.getSign()) {
            case Instruction.CONTINUE_ON_STREET:
                // TODO: might break mapbox for first instruction?
                maneuver.put("modifier", "straight");
                break;
            case Instruction.U_TURN_LEFT:
            case Instruction.U_TURN_RIGHT:
            case Instruction.U_TURN_UNKNOWN:
                maneuver.put("modifier", "uturn");
                break;
            case Instruction.KEEP_LEFT:
            case Instruction.TURN_SLIGHT_LEFT:
                maneuver.put("modifier", "slight left");
                break;
            case Instruction.TURN_LEFT:
                maneuver.put("modifier", "left");
                break;
            case Instruction.TURN_SHARP_LEFT:
                maneuver.put("modifier", "sharp left");
                break;
            case Instruction.KEEP_RIGHT:
            case Instruction.TURN_SLIGHT_RIGHT:
                maneuver.put("modifier", "slight right");
                break;
            case Instruction.TURN_RIGHT:
                maneuver.put("modifier", "right");
                break;
            case Instruction.TURN_SHARP_RIGHT:
                maneuver.put("modifier", "sharp right");
                break;
            case Instruction.USE_ROUNDABOUT:
            case Instruction.LEAVE_ROUNDABOUT:
                // TODO: We might want to calculate this (maybe via angle?)
                maneuver.put("modifier", "straight");
                break;
            default:
                break;
        }


        // type
        if (index == 0) {
            maneuver.put("type", "depart");
        } else {
            switch (instruction.getSign()) {
                case Instruction.FINISH:
                case Instruction.REACHED_VIA:
                    maneuver.put("type", "arrive");
                    break;
                case Instruction.USE_ROUNDABOUT:
                case Instruction.LEAVE_ROUNDABOUT:
                    // TODO: We don't use leave roundabout instructions in GraphHopper, this might break mapbox?
                    maneuver.put("type", "roundabout");
                    // exit number
                    if (instruction instanceof RoundaboutInstruction)
                        maneuver.put("exit", ((RoundaboutInstruction) instruction).getExitNumber());
                    break;
                default:
                    maneuver.put("type", "turn");
            }
        }

        // TODO: do we need the turn description here? If yes, get translation map
        maneuver.put("instruction", instruction.getTurnDescription(trMap.getWithFallBack(locale)));

    }

    private static ObjectNode putLocation(double lat, double lon, ObjectNode node) {
        ArrayNode location = node.putArray("location");
        // GeoJson lon,lat
        location.add(Helper.round6(lon));
        location.add(Helper.round6(lat));
        return node;
    }

    /**
     * Mapbox uses seconds instead of milliSeconds
     */
    private static double convertToSeconds(double milliSeconds) {
        return milliSeconds / 1000;
    }
}
