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
package com.graphhopper.navigation;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphhopper.GHResponse;
import com.graphhopper.ResponsePath;
import com.graphhopper.http.WebHelper;
import com.graphhopper.util.*;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class NavigateResponseConverter {

    private static final int VOICE_INSTRUCTION_MERGE_TRESHHOLD = 100;

    /**
     * Converts a GHResponse into a json that follows the Mapbox API specification
     */
    public static ObjectNode convertFromGHResponse(GHResponse ghResponse, TranslationMap translationMap, Locale locale, DistanceConfig distanceConfig) {
        ObjectNode json = JsonNodeFactory.instance.objectNode();

        if (ghResponse.hasErrors())
            throw new IllegalStateException("If the response has errors, you should use the method NavigateResponseConverter#convertFromGHResponseError");

        PointList waypoints = ghResponse.getBest().getWaypoints();

        final ArrayNode routesJson = json.putArray("routes");

        List<ResponsePath> paths = ghResponse.getAll();

        for (int i = 0; i < paths.size(); i++) {
            ResponsePath path = paths.get(i);
            ObjectNode pathJson = routesJson.addObject();

            putRouteInformation(pathJson, path, i, translationMap, locale, distanceConfig);
        }

        final ArrayNode waypointsJson = json.putArray("waypoints");
        for (int i = 0; i < waypoints.size(); i++) {
            ObjectNode waypointJson = waypointsJson.addObject();
            // TODO get names
            waypointJson.put("name", "");
            putLocation(waypoints.getLat(i), waypoints.getLon(i), waypointJson);
        }

        json.put("code", "Ok");
        // TODO: Maybe we need a different format... uuid: "cji4ja4f8004o6xrsta8w4p4h"
        json.put("uuid", UUID.randomUUID().toString().replaceAll("-", ""));

        return json;
    }

    private static void putRouteInformation(ObjectNode pathJson, ResponsePath path, int routeNr, TranslationMap translationMap, Locale locale, DistanceConfig distanceConfig) {
        InstructionList instructions = path.getInstructions();

        pathJson.put("geometry", WebHelper.encodePolyline(path.getPoints(), false, 1e6));
        ArrayNode legsJson = pathJson.putArray("legs");

        ObjectNode legJson = legsJson.addObject();
        ArrayNode steps = legJson.putArray("steps");

        long time = 0;
        double distance = 0;
        boolean isFirstInstructionOfLeg = true;

        for (int i = 0; i < instructions.size(); i++) {
            ObjectNode instructionJson = steps.addObject();
            putInstruction(instructions, i, locale, translationMap, instructionJson, isFirstInstructionOfLeg, distanceConfig);
            Instruction instruction = instructions.get(i);
            time += instruction.getTime();
            distance += instruction.getDistance();
            isFirstInstructionOfLeg = false;
            if (instruction.getSign() == Instruction.REACHED_VIA || instruction.getSign() == Instruction.FINISH) {
                putLegInformation(legJson, path, routeNr, time, distance);
                isFirstInstructionOfLeg = true;
                time = 0;
                distance = 0;

                if (instruction.getSign() == Instruction.REACHED_VIA) {
                    // Create new leg and steps after a via points
                    legJson = legsJson.addObject();
                    steps = legJson.putArray("steps");
                }
            }
        }

        pathJson.put("weight_name", "routability");
        pathJson.put("weight", Helper.round(path.getRouteWeight(), 1));
        pathJson.put("duration", convertToSeconds(path.getTime()));
        pathJson.put("distance", Helper.round(path.getDistance(), 1));
        pathJson.put("voiceLocale", locale.toLanguageTag());
    }

    private static void putLegInformation(ObjectNode legJson, ResponsePath path, int i, long time, double distance) {
        // TODO: Improve path descriptions, so that every path has a description, not just alternative routes
        String summary;
        if (!path.getDescription().isEmpty())
            summary = String.join(",", path.getDescription());
        else
            summary = "GraphHopper Route " + i;
        legJson.put("summary", summary);

        // TODO there is no weight per instruction, let's use time
        legJson.put("weight", convertToSeconds(time));
        legJson.put("duration", convertToSeconds(time));
        legJson.put("distance", Helper.round(distance, 1));
    }

    private static ObjectNode putInstruction(InstructionList instructions, int index, Locale locale, TranslationMap translationMap,
                                             ObjectNode instructionJson, boolean isFirstInstructionOfLeg, DistanceConfig distanceConfig) {
        Instruction instruction = instructions.get(index);
        ArrayNode intersections = instructionJson.putArray("intersections");
        ObjectNode intersection = intersections.addObject();
        intersection.putArray("entry");
        intersection.putArray("bearings");
        //Make pointList mutable
        PointList pointList = instruction.getPoints().clone(false);

        if (index + 2 < instructions.size()) {
            // Add the first point of the next instruction
            PointList nextPoints = instructions.get(index + 1).getPoints();
            pointList.add(nextPoints.getLat(0), nextPoints.getLon(0), nextPoints.getEle(0));
        } else if (pointList.size() == 1) {
            // Duplicate the last point in the arrive instruction, if the size is 1
            pointList.add(pointList.getLat(0), pointList.getLon(0), pointList.getEle(0));
        }

        putLocation(pointList.getLat(0), pointList.getLon(0), intersection);

        instructionJson.put("driving_side", "right");

        // Does not include elevation
        instructionJson.put("geometry", WebHelper.encodePolyline(pointList, false, 1e6));

        // TODO: how about other modes?
        instructionJson.put("mode", "driving");

        putManeuver(instruction, instructionJson, locale, translationMap, isFirstInstructionOfLeg);

        // TODO distance = weight, is weight even important?
        double distance = Helper.round(instruction.getDistance(), 1);
        instructionJson.put("weight", distance);
        instructionJson.put("duration", convertToSeconds(instruction.getTime()));
        instructionJson.put("name", instruction.getName());
        instructionJson.put("distance", distance);

        ArrayNode voiceInstructions = instructionJson.putArray("voiceInstructions");
        ArrayNode bannerInstructions = instructionJson.putArray("bannerInstructions");

        // Voice and banner instructions are empty for the last element
        if (index + 1 < instructions.size()) {
            putVoiceInstructions(instructions, distance, index, locale, translationMap, voiceInstructions, distanceConfig);
            putBannerInstructions(instructions, distance, index, locale, translationMap, bannerInstructions);
        }

        return instructionJson;
    }

    private static void putVoiceInstructions(InstructionList instructions, double distance, int index, Locale locale, TranslationMap translationMap,
                                             ArrayNode voiceInstructions, DistanceConfig distanceConfig) {
        /*
            A VoiceInstruction Object looks like this
            {
                distanceAlongGeometry: 40.9,
                announcement: "Exit the traffic circle",
                ssmlAnnouncement: "<speak><amazon:effect name="drc"><prosody rate="1.08">Exit the traffic circle</prosody></amazon:effect></speak>",
            }
        */
        Instruction nextInstruction = instructions.get(index + 1);
        String turnDescription = nextInstruction.getTurnDescription(translationMap.getWithFallBack(locale));

        String thenVoiceInstruction = getThenVoiceInstructionpart(instructions, index, locale, translationMap);

        List<VoiceInstructionConfig.VoiceInstructionValue> voiceValues = distanceConfig.getVoiceInstructionsForDistance(distance, turnDescription, thenVoiceInstruction);

        for (VoiceInstructionConfig.VoiceInstructionValue voiceValue : voiceValues) {
            putSingleVoiceInstruction(voiceValue.spokenDistance, voiceValue.turnDescription, voiceInstructions);
        }

        // Speak 80m instructions 80 before the turn
        // Note: distanceAlongGeometry: "how far from the upcoming maneuver the voice instruction should begin"
        double distanceAlongGeometry = Helper.round(Math.min(distance, 80), 1);

        // Special case for the arrive instruction
        if (index + 2 == instructions.size())
            distanceAlongGeometry = Helper.round(Math.min(distance, 25), 1);

        putSingleVoiceInstruction(distanceAlongGeometry, turnDescription + thenVoiceInstruction, voiceInstructions);
    }

    private static void putSingleVoiceInstruction(double distanceAlongGeometry, String turnDescription, ArrayNode voiceInstructions) {
        ObjectNode voiceInstruction = voiceInstructions.addObject();
        voiceInstruction.put("distanceAlongGeometry", distanceAlongGeometry);
        //TODO: ideally, we would even generate instructions including the instructions after the next like turn left **then** turn right
        voiceInstruction.put("announcement", turnDescription);
        voiceInstruction.put("ssmlAnnouncement", "<speak><amazon:effect name=\"drc\"><prosody rate=\"1.08\">" + turnDescription + "</prosody></amazon:effect></speak>");
    }

    /**
     * For close turns, it is important to announce the next turn in the earlier instruction.
     * e.g.: instruction i+1= turn right, instruction i+2=turn left, with instruction i+1 distance < VOICE_INSTRUCTION_MERGE_TRESHHOLD
     * The voice instruction should be like "turn right, then turn left"
     * <p>
     * For instruction i+1 distance > VOICE_INSTRUCTION_MERGE_TRESHHOLD an empty String will be returned
     */
    private static String getThenVoiceInstructionpart(InstructionList instructions, int index, Locale locale, TranslationMap translationMap) {
        if (instructions.size() > index + 2) {
            Instruction firstInstruction = instructions.get(index + 1);
            if (firstInstruction.getDistance() < VOICE_INSTRUCTION_MERGE_TRESHHOLD) {
                Instruction secondInstruction = instructions.get(index + 2);
                if (secondInstruction.getSign() != Instruction.REACHED_VIA)
                    return ", " + translationMap.getWithFallBack(locale).tr("navigate.then") + " " + secondInstruction.getTurnDescription(translationMap.getWithFallBack(locale));
            }
        }

        return "";
    }

    /**
     * Banner instructions are the turn instructions that are shown to the user in the top bar.
     * <p>
     * Between two instructions we can show multiple banner instructions, you can control when they pop up using distanceAlongGeometry.
     */
    private static void putBannerInstructions(InstructionList instructions, double distance, int index, Locale locale, TranslationMap translationMap, ArrayNode bannerInstructions) {
        /*
        A BannerInstruction looks like this
        distanceAlongGeometry: 107,
        primary: {
            text: "Lichtensteinstraße",
            components: [
            {
                text: "Lichtensteinstraße",
                type: "text",
            }
            ],
            type: "turn",
            modifier: "right",
        },
        secondary: null,
         */

        ObjectNode bannerInstruction = bannerInstructions.addObject();

        //Show from the beginning
        bannerInstruction.put("distanceAlongGeometry", distance);

        ObjectNode primary = bannerInstruction.putObject("primary");
        putSingleBannerInstruction(instructions.get(index + 1), locale, translationMap, primary);

        bannerInstruction.putNull("secondary");

        if (instructions.size() > index + 2 && instructions.get(index + 2).getSign() != Instruction.REACHED_VIA) {
            // Sub shows the instruction after the current one
            ObjectNode sub = bannerInstruction.putObject("sub");
            putSingleBannerInstruction(instructions.get(index + 2), locale, translationMap, sub);
        }
    }

    private static void putSingleBannerInstruction(Instruction instruction, Locale locale, TranslationMap translationMap, ObjectNode singleBannerInstruction) {
        String bannerInstructionName = instruction.getName();
        if (bannerInstructionName == null || bannerInstructionName.isEmpty()) {
            // Fix for final instruction and for instructions without name
            bannerInstructionName = instruction.getTurnDescription(translationMap.getWithFallBack(locale));

            // Uppercase first letter
            // TODO: should we do this for all cases? Then we might change the spelling of street names though
            bannerInstructionName = Helper.firstBig(bannerInstructionName);
        }

        singleBannerInstruction.put("text", bannerInstructionName);

        ArrayNode components = singleBannerInstruction.putArray("components");
        ObjectNode component = components.addObject();
        component.put("text", bannerInstructionName);
        component.put("type", "text");

        singleBannerInstruction.put("type", getTurnType(instruction, false));
        String modifier = getModifier(instruction);
        if (modifier != null)
            singleBannerInstruction.put("modifier", modifier);

        if (instruction.getSign() == Instruction.USE_ROUNDABOUT) {
            if (instruction instanceof RoundaboutInstruction) {
                double turnAngle = ((RoundaboutInstruction) instruction).getTurnAngle();
                if (Double.isNaN(turnAngle)) {
                    singleBannerInstruction.putNull("degrees");
                } else {
                    double degree = (Math.abs(turnAngle) * 180) / Math.PI;
                    singleBannerInstruction.put("degrees", degree);
                }
            }
        }
    }

    private static void putManeuver(Instruction instruction, ObjectNode instructionJson, Locale locale, TranslationMap translationMap, boolean isFirstInstructionOfLeg) {
        ObjectNode maneuver = instructionJson.putObject("maneuver");
        maneuver.put("bearing_after", 0);
        maneuver.put("bearing_before", 0);

        PointList points = instruction.getPoints();
        putLocation(points.getLat(0), points.getLon(0), maneuver);

        String modifier = getModifier(instruction);
        if (modifier != null)
            maneuver.put("modifier", modifier);

        maneuver.put("type", getTurnType(instruction, isFirstInstructionOfLeg));
        // exit number
        if (instruction instanceof RoundaboutInstruction)
            maneuver.put("exit", ((RoundaboutInstruction) instruction).getExitNumber());

        maneuver.put("instruction", instruction.getTurnDescription(translationMap.getWithFallBack(locale)));

    }

    /**
     * Relevant maneuver types are:
     * depart (firs instruction)
     * turn (regular turns)
     * roundabout (enter roundabout, maneuver contains also the exit number)
     * arrive (last instruction and waypoints)
     * <p>
     * You can find all maneuver types at: https://www.mapbox.com/api-documentation/#maneuver-types
     */
    private static String getTurnType(Instruction instruction, boolean isFirstInstructionOfLeg) {
        if (isFirstInstructionOfLeg) {
            return "depart";
        } else {
            switch (instruction.getSign()) {
                case Instruction.FINISH:
                case Instruction.REACHED_VIA:
                    return "arrive";
                case Instruction.USE_ROUNDABOUT:
                    return "roundabout";
                default:
                    return "turn";
            }
        }
    }

    /**
     * No modifier values for arrive and depart
     * <p>
     * Find modifier values here: https://www.mapbox.com/api-documentation/#stepmaneuver-object
     */
    private static String getModifier(Instruction instruction) {
        switch (instruction.getSign()) {
            case Instruction.CONTINUE_ON_STREET:
                return "straight";
            case Instruction.U_TURN_LEFT:
            case Instruction.U_TURN_RIGHT:
            case Instruction.U_TURN_UNKNOWN:
                return "uturn";
            case Instruction.KEEP_LEFT:
            case Instruction.TURN_SLIGHT_LEFT:
                return "slight left";
            case Instruction.TURN_LEFT:
                return "left";
            case Instruction.TURN_SHARP_LEFT:
                return "sharp left";
            case Instruction.KEEP_RIGHT:
            case Instruction.TURN_SLIGHT_RIGHT:
                return "slight right";
            case Instruction.TURN_RIGHT:
                return "right";
            case Instruction.TURN_SHARP_RIGHT:
                return "sharp right";
            case Instruction.USE_ROUNDABOUT:
                // TODO: This might be an issue in left-handed traffic, because there it schould be left
                return "right";
            default:
                return null;
        }
    }

    /**
     * Puts a location array in GeoJson format into the node
     */
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
        return Helper.round(milliSeconds / 1000, 1);
    }

    public static ObjectNode convertFromGHResponseError(GHResponse ghResponse) {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
        // TODO we could make this more fine grained
        json.put("code", "InvalidInput");
        json.put("message", ghResponse.getErrors().get(0).getMessage());
        return json;
    }
}
