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
import com.graphhopper.jackson.ResponsePathSerializer;
import com.graphhopper.routing.ev.MaxSpeed;
import com.graphhopper.util.*;
import com.graphhopper.util.details.IntersectionValues;
import com.graphhopper.util.details.PathDetail;
import com.graphhopper.util.shapes.GHPoint3D;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.graphhopper.util.Parameters.Details.INTERSECTION;

enum ManeuverType {
    ARRIVE,
    DEPART,
    TURN,
    ROUNDABOUT
} ;

public class NavigateResponseConverter {
    private static final Logger LOGGER = LoggerFactory.getLogger(NavigateResponseConverter.class);
    private static final int VOICE_INSTRUCTION_MERGE_TRESHHOLD = 100;

    /**
     * Converts a GHResponse into a json that follows the Mapbox API specification
     */
    public static ObjectNode convertFromGHResponse(GHResponse ghResponse, TranslationMap translationMap, Locale locale,
                                                   DistanceConfig distanceConfig) {
        ObjectNode json = JsonNodeFactory.instance.objectNode();

        if (ghResponse.hasErrors())
            throw new IllegalStateException(
                    "If the response has errors, you should use the method NavigateResponseConverter#convertFromGHResponseError");

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

    private static void putRouteInformation(ObjectNode pathJson, ResponsePath path, int routeNr,
                                            TranslationMap translationMap, Locale locale, DistanceConfig distanceConfig) {
        InstructionList instructions = path.getInstructions();

        pathJson.put("geometry", ResponsePathSerializer.encodePolyline(path.getPoints(), false, 1e6));
        ArrayNode legsJson = pathJson.putArray("legs");

        ObjectNode legJson = legsJson.addObject();
        ArrayNode steps = legJson.putArray("steps");

        long time = 0;
        double distance = 0;
        boolean isDepartInstruction = true;
        int pointIndexFrom = 0;

        Map<String, List<PathDetail>> pathDetails = path.getPathDetails();
        List<PathDetail> intersectionDetails = pathDetails.getOrDefault(INTERSECTION, Collections.emptyList());

        ObjectNode annotation = null;
        ArrayNode maxSpeedArray = null;
        if (pathDetails.containsKey(MaxSpeed.KEY)) {
            annotation = legJson.putObject("annotation");
            maxSpeedArray = annotation.putArray("maxspeed");
        }

        for (int i = 0; i < instructions.size(); i++) {
            ObjectNode stepJson = steps.addObject();
            Instruction instruction = instructions.get(i);
            // pointIndexTo is the same as ShallowCopy of the path Points toPoint member
            int pointIndexTo = pointIndexFrom + instruction.getPoints().size();

            ManeuverType maneuverType;
            if (isDepartInstruction) {
                maneuverType = ManeuverType.DEPART;
                fixDepartIntersectionDetail(intersectionDetails, i);
            } else {
                switch (instruction.getSign()) {
                    case Instruction.REACHED_VIA, Instruction.FINISH:
                        maneuverType = ManeuverType.ARRIVE;
                        break;
                    case Instruction.USE_ROUNDABOUT :
                        maneuverType = ManeuverType.ROUNDABOUT;
                        break;
                    default :
                        maneuverType = ManeuverType.TURN;
                }
            }
            if (annotation != null)
                putAnnotation(maxSpeedArray, pathDetails, pointIndexFrom, pointIndexTo, distanceConfig.unit);
            putInstruction(path.getPoints(), instructions, i, locale, translationMap, stepJson,
                    maneuverType, distanceConfig, intersectionDetails, pointIndexFrom, pointIndexTo);
            pointIndexFrom = pointIndexTo;
            time += instruction.getTime();
            distance += instruction.getDistance();
            isDepartInstruction = false;
            if (maneuverType == ManeuverType.ARRIVE) {
                putLegInformation(legJson, path, routeNr, time, distance);
                if (instruction.getSign() == Instruction.REACHED_VIA) {
                    // Create new leg and steps after a via points
                    legJson = legsJson.addObject();
                    steps = legJson.putArray("steps");
                    if (annotation != null) {
                        annotation = legJson.putObject("annotation");
                        maxSpeedArray = annotation.putArray("maxspeed");
                    }
                    isDepartInstruction = true;
                    time = 0;
                    distance = 0;
                }
            }
        }

        pathJson.put("weight_name", "routability");
        pathJson.put("weight", Helper.round(path.getRouteWeight(), 1));
        pathJson.put("duration", convertToSeconds(path.getTime()));
        pathJson.put("distance", Helper.round(path.getDistance(), 1));
        pathJson.put("voiceLocale", locale.toLanguageTag());
    }

    private static void putAnnotation(ArrayNode maxSpeedArray, Map<String, List<PathDetail>> pathDetails,
                                      final int fromIdx, final int toIdx, DistanceUtils.Unit metric) {

        List<PathDetail> maxSpeeds = pathDetails.get(MaxSpeed.KEY);
        String unitValue = metric == DistanceUtils.Unit.METRIC ? "km/h" : "mph";

        // loop through indices to ensure that number of entries in maxSpeedArray are exactly the same
        int nextPDIdx = 0;
        if (!maxSpeeds.isEmpty())
            for (int idx = fromIdx; idx < toIdx; ) {
                for (; nextPDIdx < maxSpeeds.size(); nextPDIdx++) {
                    PathDetail pd = maxSpeeds.get(nextPDIdx);
                    if (idx >= pd.getFirst() && idx <= pd.getLast()) break;
                }
                if (nextPDIdx >= maxSpeeds.size()) break; // should not happen

                PathDetail pd = maxSpeeds.get(nextPDIdx);
                long value = pd.getValue() == null ? Math.round(MaxSpeed.MAXSPEED_150)
                        : (metric == DistanceUtils.Unit.METRIC
                        ? Math.round(((Number) pd.getValue()).doubleValue())
                        : Math.round(((Number) pd.getValue()).doubleValue() / DistanceCalcEarth.KM_MILE));

                // one entry for every point
                for (; idx <= Math.min(toIdx, pd.getLast()); idx++) {
                    ObjectNode object = maxSpeedArray.addObject();
                    object.put("speed", value);
                    object.put("unit", unitValue);
                }
            }


        // TODO what purpose?
//        "speed":[24.7, 24.7, 24.7, 24.7, 24.7, 24.7, 24.7, 24.7, 24.7],
//        "distance":[23.6, 14.9, 9.6, 13.2, 25, 28.1, 38.1, 41.6, 90],
//        "duration":[0.956, 0.603, 0.387, 0.535, 1.011, 1.135, 1.539, 1.683, 3.641]
    }

    private static void putLegInformation(ObjectNode legJson, ResponsePath path, int i, long time, double distance) {
        // TODO: Improve path descriptions, so that every path has a description, not
        // just alternative routes
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

    /**
     * fix the first IntersectionDetail which is an Depart
     * <p>
     * Departs should only have one "bearings" and one
     * "out" entry
     */
    private static void fixDepartIntersectionDetail(List<PathDetail> intersectionDetails, int position) {

        if (intersectionDetails.size() < position + 2) {
            // Can happen if start and stop are at the same spot and other edge cases
            return;
        }

        final Map<String, Object> departIntersectionMap = (Map<String, Object>) intersectionDetails.get(position).getValue();

        int out = (int) departIntersectionMap.get("out");
        departIntersectionMap.put("out", 0);

        // bearings
        List<Integer> oldBearings = (List<Integer>) departIntersectionMap.get("bearings");
        List<Integer> newBearings = new ArrayList<>();
        newBearings.add(oldBearings.get(out));
        departIntersectionMap.put("bearings", newBearings);

        // entries
        final List<Boolean> oldEntries = (List<Boolean>) departIntersectionMap.get("entries");
        List<Boolean> newEntries = new ArrayList<>();
        newEntries.add(oldEntries.get(out));
        departIntersectionMap.put("entries", newEntries);
    }

    /**
     * filter the IntersectionDetails.
     * <p>
     * first job is to find the interesting part in the interSectionDetails based on
     * pointIndexFrom and pointIndexTo.
     * <p>
     * Next job is to eleminate intersections colocated in the same point
     * since Mapbox chokes on geometries with intersections lying ontop of
     * each other.
     * <p>
     * These type of intersections is used for barrier nodes
     * <p>
     * We look for intersections in the lists and merge these adjacent, colocated
     * intersection into each other taking the edges from both intersections and
     * removing the connecting zero length edge.
     * Care has to be taken that the result is sorted by bearing
     */
    private static List<PathDetail> filterIntersectionDetails(PointList points, List<PathDetail> intersectionDetails,
                                                              int pointIndexFrom, int pointIndexTo) {
        List<PathDetail> list = new ArrayList<>();

        // job1: find out the interesting part of the intersectionDetails
        for (PathDetail intersectionDetail : intersectionDetails) {
            int first = intersectionDetail.getFirst();
            if (first >= pointIndexTo) {
                break;
            }
            if (first >= pointIndexFrom) {
                list.add(intersectionDetail);
            }
        }
        // nothing to be done for job 2. Either no entry or only one
        if (list.size() < 2) {
            return list;
        }

        // Now look for adjacent intersections colocated
        GHPoint3D intersectionPoint = points.get(list.get(0).getFirst());
        List<Integer> duplicates = new ArrayList<>();
        for (int i = 1; i < list.size(); i++) {
            GHPoint3D currentIntersectionPoint = points.get(list.get(i).getFirst());
            if (intersectionPoint.equals(currentIntersectionPoint)) {
                duplicates.add(i - 1); // store the first index of the duplicate
            }
            intersectionPoint = currentIntersectionPoint;
        }

        // now iterate backwards over all duplicates, since we will remove entries from
        // list
        for (int dup = duplicates.size() - 1; dup >= 0; dup--) {
            int i = duplicates.get(dup);
            // member i and i+1 are on the same point
            // out edge of (i) points to in edge of (i+1)
            // ... -------> intersection[i].out --------> intersection[i+1].in ----------->
            //
            // Create a new PathDetail for merging both intersections into one
            // ... -------> intersection[i] ------>
            try {
                final Map<String, Object> intersectionMap = (Map<String, Object>) list.get(i).getValue();
                final List<IntersectionValues> intersectionValueList = IntersectionValues.createList(intersectionMap);

                final Map<String, Object> nextIntersectionMap = (Map<String, Object>) list.get(i + 1).getValue();
                final List<IntersectionValues> nextIntersectionValueList = IntersectionValues
                        .createList(nextIntersectionMap);

                // merge both Lists while
                final List<IntersectionValues> mergedInterSectionValueList = Stream.concat(
                                // removing out from Intersection
                                intersectionValueList.stream().filter(x -> !x.out),
                                // removing in from nextIntersection
                                nextIntersectionValueList.stream().filter(x -> !x.in)).
                        // sort the merged list by bearing
                                sorted((x, y) -> Integer.compare(x.bearing, y.bearing)).
                        // create the result list
                                collect(Collectors.toList());

                // remove the duplicate Intersection from the Path (we are at "i" currently)
                list.remove(i + 1);

                Map<String, Object> mergedIntersection = IntersectionValues
                        .createIntersection(mergedInterSectionValueList);
                PathDetail mergedPathDetail = new PathDetail(mergedIntersection);
                mergedPathDetail.setFirst(list.get(i).getFirst());
                // and replace the intersection with the merged one
                list.set(i, mergedPathDetail);
            } catch (ClassCastException e) {
                LOGGER.warn("Exception :" + e);
                continue;
            }
        }

        return list;
    }

    private static void putInstruction(PointList points, InstructionList instructions, int instructionIndex,
                                       Locale locale,
                                       TranslationMap translationMap, ObjectNode stepJson, ManeuverType maneuverType,
                                       DistanceConfig distanceConfig, List<PathDetail> intersectionDetails, int pointIndexFrom,
                                       int pointIndexTo) {
        Instruction instruction = instructions.get(instructionIndex);
        ArrayNode intersections = stepJson.putArray("intersections");

        // make pointList writeable
        PointList pointList = instruction.getPoints().clone(false);

        if (maneuverType != ManeuverType.ARRIVE && instructionIndex + 1 < instructions.size()) {
            // modify pointlist to include the first point of the next instruction
            // for all instructions but the arrival#
            // but not for instructions with an DEPART and ARRIVAL at the same last point
            PointList nextPoints = instructions.get(instructionIndex + 1).getPoints();
            pointList.add(nextPoints.getLat(0), nextPoints.getLon(0), nextPoints.getEle(0));
        } else {
            // we are at the arrival (or via point arrival instruction)
            // Duplicate the last point in the arrival instruction, which does has only one
            // point
            pointList.add(pointList.getLat(0), pointList.getLon(0), pointList.getEle(0));

            // Add an arrival intersection with only one enty
            ObjectNode intersection = intersections.addObject();
            ArrayNode entryArray = intersection.putArray("entry");
            entryArray.add(true);

            // copy the bearing from the previous instruction
            ArrayNode bearingsArray = intersection.putArray("bearings");
            bearingsArray.add(0);

            // add the in tag
            intersection.put("in", 0);
            putLocation(pointList.getLat(0), pointList.getLon(0), intersection);
        }

        // preprocess intersectionDetails
        List<PathDetail> filteredIntersectionDetails = filterIntersectionDetails(points, intersectionDetails,
                pointIndexFrom, pointIndexTo);

        for (PathDetail intersectionDetail : filteredIntersectionDetails) {
            ObjectNode intersection = intersections.addObject();
            Map<String, Object> intersectionValue = (Map<String, Object>) intersectionDetail.getValue();
            // Location
            ArrayNode locationArray = intersection.putArray("location");
            locationArray.add(Helper.round6(points.getLon(intersectionDetail.getFirst())));
            locationArray.add(Helper.round6(points.getLat(intersectionDetail.getFirst())));
            // Entry
            List<Boolean> entries = (List<Boolean>) intersectionValue.getOrDefault("entries", Collections.emptyList());
            ArrayNode entryArray = intersection.putArray("entry");
            for (Boolean entry : entries) {
                entryArray.add(entry);
            }
            // Bearings
            List<Integer> bearingsList = (List<Integer>) intersectionValue.getOrDefault("bearings",
                    Collections.emptyList());
            ArrayNode bearingsrray = intersection.putArray("bearings");
            for (Integer bearing : bearingsList) {
                bearingsrray.add(bearing);
            }
            // in
            if (intersectionValue.containsKey("in")) {
                intersection.put("in", (int) intersectionValue.get("in"));
            }
            // out
            if (intersectionValue.containsKey("out")) {
                intersection.put("out", (int) intersectionValue.get("out"));
            }
        }

        stepJson.put("driving_side", "right");

        // Does not include elevation
        stepJson.put("geometry", ResponsePathSerializer.encodePolyline(pointList, false, 1e6));

        stepJson.put("mode", instruction.getSign() == Instruction.FERRY ? "ferry" : distanceConfig.getMode());

        putManeuver(instruction, stepJson, locale, translationMap, maneuverType);

        // TODO distance = weight, is weight even important?
        double distance = Helper.round(instruction.getDistance(), 1);
        stepJson.put("weight", distance);
        stepJson.put("duration", convertToSeconds(instruction.getTime()));
        stepJson.put("name", instruction.getName());
        stepJson.put("distance", distance);

        ArrayNode voiceInstructions = stepJson.putArray("voiceInstructions");
        ArrayNode bannerInstructions = stepJson.putArray("bannerInstructions");

        // Voice and banner instructions are empty for the last element
        if (instructionIndex + 1 < instructions.size()) {
            putVoiceInstructions(instructions, distance, instructionIndex, locale, translationMap, voiceInstructions,
                    distanceConfig);
            putBannerInstructions(instructions, distance, instructionIndex, locale, translationMap, bannerInstructions);
        }
    }

    private static void putVoiceInstructions(InstructionList instructions, double distance, int index,
                                             Locale locale, TranslationMap translationMap,
                                             ArrayNode voiceInstructions, DistanceConfig distanceConfig) {
        /*
         * A VoiceInstruction Object looks like this
         * {
         * distanceAlongGeometry: 40.9,
         * announcement: "Exit the traffic circle",
         * ssmlAnnouncement: "<speak><amazon:effect name="drc"><prosody rate="1.
         * 08">Exit the traffic circle</prosody></amazon:effect></speak>",
         * }
         */
        Instruction nextInstruction = instructions.get(index + 1);
        String turnDescription = nextInstruction.getTurnDescription(translationMap.getWithFallBack(locale));

        String thenVoiceInstruction = getThenVoiceInstructionpart(instructions, index, locale, translationMap);

        List<VoiceInstructionConfig.VoiceInstructionValue> voiceValues = distanceConfig
                .getVoiceInstructionsForDistance(distance, turnDescription, thenVoiceInstruction);

        for (VoiceInstructionConfig.VoiceInstructionValue voiceValue : voiceValues) {
            putSingleVoiceInstruction(voiceValue.spokenDistance, voiceValue.turnDescription, voiceInstructions);
        }

        // Speak 80m instructions 80 before the turn
        // Note: distanceAlongGeometry: "how far from the upcoming maneuver the voice
        // instruction should begin"
        double distanceAlongGeometry = Helper.round(Math.min(distance, 80), 1);

        // Special case for the arrive instruction
        if (index + 2 == instructions.size())
            distanceAlongGeometry = Helper.round(Math.min(distance, 25), 1);

        putSingleVoiceInstruction(distanceAlongGeometry, turnDescription + thenVoiceInstruction, voiceInstructions);
    }

    private static void putSingleVoiceInstruction(double distanceAlongGeometry, String turnDescription,
                                                  ArrayNode voiceInstructions) {
        ObjectNode voiceInstruction = voiceInstructions.addObject();
        voiceInstruction.put("distanceAlongGeometry", distanceAlongGeometry);
        // TODO: ideally, we would even generate instructions including the instructions
        // after the next like turn left **then** turn right
        voiceInstruction.put("announcement", turnDescription);
        voiceInstruction.put("ssmlAnnouncement", "<speak><amazon:effect name=\"drc\"><prosody rate=\"1.08\">"
                + turnDescription + "</prosody></amazon:effect></speak>");
    }

    /**
     * For close turns, it is important to announce the next turn in the earlier
     * instruction.
     * e.g.: instruction i+1= turn right, instruction i+2=turn left, with
     * instruction i+1 distance < VOICE_INSTRUCTION_MERGE_TRESHHOLD
     * The voice instruction should be like "turn right, then turn left"
     * <p>
     * For instruction i+1 distance > VOICE_INSTRUCTION_MERGE_TRESHHOLD an empty
     * String will be returned
     */
    private static String getThenVoiceInstructionpart(InstructionList instructions, int index, Locale locale,
                                                      TranslationMap translationMap) {
        if (instructions.size() > index + 2) {
            Instruction firstInstruction = instructions.get(index + 1);
            if (firstInstruction.getDistance() < VOICE_INSTRUCTION_MERGE_TRESHHOLD) {
                Instruction secondInstruction = instructions.get(index + 2);
                if (secondInstruction.getSign() != Instruction.REACHED_VIA)
                    return ", " + translationMap.getWithFallBack(locale).tr("navigate.then") + " "
                            + secondInstruction.getTurnDescription(translationMap.getWithFallBack(locale));
            }
        }

        return "";
    }

    /**
     * Banner instructions are the turn instructions that are shown to the user in
     * the top bar.
     * <p>
     * Between two instructions we can show multiple banner instructions, you can
     * control when they pop up using distanceAlongGeometry.
     */
    private static void putBannerInstructions(InstructionList instructions, double distance, int index, Locale locale,
                                              TranslationMap translationMap, ArrayNode bannerInstructions) {
        /*
         * A BannerInstruction looks like this
         * distanceAlongGeometry: 107,
         * primary: {
         * text: "Lichtensteinstraße",
         * components: [
         * {
         * text: "Lichtensteinstraße",
         * type: "text",
         * }
         * ],
         * type: "turn",
         * modifier: "right",
         * },
         * secondary: null,
         */

        ObjectNode bannerInstruction = bannerInstructions.addObject();

        // Show from the beginning
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

    private static void putSingleBannerInstruction(Instruction instruction, Locale locale,
                                                   TranslationMap translationMap, ObjectNode singleBannerInstruction) {
        String bannerInstructionName = instruction.getName();
        if (bannerInstructionName.isEmpty()) {
            // Fix for final instruction and for instructions without name
            bannerInstructionName = instruction.getTurnDescription(translationMap.getWithFallBack(locale));

            // Uppercase first letter
            // TODO: should we do this for all cases? Then we might change the spelling of
            // street names though
            bannerInstructionName = Helper.firstBig(bannerInstructionName);
        }

        singleBannerInstruction.put("text", bannerInstructionName);

        ArrayNode components = singleBannerInstruction.putArray("components");
        ObjectNode component = components.addObject();
        component.put("text", bannerInstructionName);
        component.put("type", "text");

        singleBannerInstruction.put("type", getTurnType(instruction));
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
                    singleBannerInstruction.put("degrees", Math.round(degree));
                }
            }
        }
    }

    private static void putManeuver(Instruction instruction, ObjectNode instructionJson, Locale locale,
                                    TranslationMap translationMap, ManeuverType maneuverType) {
        ObjectNode maneuver = instructionJson.putObject("maneuver");
        maneuver.put("bearing_after", 0);
        maneuver.put("bearing_before", 0);

        PointList points = instruction.getPoints();
        putLocation(points.getLat(0), points.getLon(0), maneuver);

        // see https://docs.mapbox.com/api/navigation/directions/#maneuver-types
        switch (maneuverType) {
            case ARRIVE:
                maneuver.put("type", "arrive");
                break;
            case DEPART:
                maneuver.put("type", "depart");
                break;
            case ROUNDABOUT:
                maneuver.put("type", "roundabout");
                maneuver.put("exit", ((RoundaboutInstruction) instruction).getExitNumber());
                break;
            default: // i.e. ManeuverType.TURN:
                maneuver.put("type", "turn");
        }
        String modifier = getModifier(instruction);
        if (modifier != null)
            maneuver.put("modifier", modifier);
        maneuver.put("instruction", instruction.getTurnDescription(translationMap.getWithFallBack(locale)));

    }
    /**
     * Relevant turn types for banners are:
     * turn (regular turns)
     * roundabout (enter roundabout, maneuver contains also the exit number)
     * arrive (last instruction and waypoints)
     * <p>
     * You can find all turn types at:
     * https://docs.mapbox.com/api/navigation/directions/#banner-instruction-object
     */
    private static String getTurnType(Instruction instruction) {
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

    /**
     * No modifier values for arrive and depart
     * <p>
     * Find modifier values here:
     * https://www.mapbox.com/api-documentation/#stepmaneuver-object
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
                // TODO: This might be an issue in left-handed traffic, because there it schould
                // be left
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

