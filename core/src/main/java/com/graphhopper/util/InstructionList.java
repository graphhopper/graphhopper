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
package com.graphhopper.util;

import com.fasterxml.jackson.annotation.JsonValue;

import java.awt.*;
import java.text.DateFormat;
import java.util.*;
import java.util.List;

/**
 * List of instructions.
 */
public class InstructionList extends AbstractList<Instruction> {

    static String simpleXMLEscape(String str) {
        // We could even use the 'more flexible' CDATA section but for now do the following. The 'and' could be important sometimes:
        return str.replaceAll("&", "&amp;").
                // but do not care for:
                        replaceAll("[\\<\\>]", "_");
    }

    private final List<Instruction> instructions;
    private PointList points;
    private final Translation tr;
    private static final AngleCalc AC = Helper.ANGLE_CALC;

    public InstructionList(Translation tr) {
        this(10, tr);
    }

    public InstructionList(int cap, Translation tr) {
        instructions = new ArrayList<>(cap);
        this.tr = tr;
    }

    @Override
    public int size() {
        return instructions.size();
    }

    @Override
    public Instruction get(int index) {
        return instructions.get(index);
    }

    @Override
    public Instruction set(int index, Instruction element) {
        return instructions.set(index, element);
    }

    @Override
    public void add(int index, Instruction element) {
        instructions.add(index, element);
    }

    @Override
    public Instruction remove(int index) {
        return instructions.remove(index);
    }

    public void replaceLast(Instruction instr) {
        if (instructions.isEmpty())
            throw new IllegalStateException("Cannot replace last instruction as list is empty");

        instructions.set(instructions.size() - 1, instr);
    }

    @JsonValue
    public List<Map<String, Object>> createJson() {
        List<Map<String, Object>> instrList = new ArrayList<>(instructions.size());
        int pointsIndex = 0;
        int counter = 0;
        for (Instruction instruction : instructions) {
            Map<String, Object> instrJson = new HashMap<>();
            instrList.add(instrJson);

            InstructionAnnotation ia = instruction.getAnnotation();
            String text = instruction.getTurnDescription(tr);
            if (Helper.isEmpty(text))
                text = ia.getMessage();
            instrJson.put("text", Helper.firstBig(text));
            if (!ia.isEmpty()) {
                instrJson.put("annotation_text", ia.getMessage());
                instrJson.put("annotation_importance", ia.getImportance());
            }

            instrJson.put("street_name", instruction.getName());
            instrJson.put("time", instruction.getTime());
            instrJson.put("distance", Helper.round(instruction.getDistance(), 3));
            instrJson.put("sign", instruction.getSign());
            instrJson.putAll(instruction.getExtraInfoJSON());

            instrJson.put("interval", Arrays.asList(instruction.getFirst(), instruction.getLast()));

            counter++;
        }
        return instrList;
    }

    /**
     * @return This method returns a list of gpx entries where the time (in millis) is relative to
     * the first which is 0.
     * <p>
     */
    public List<GPXEntry> createGPXList() {
        if (isEmpty())
            return Collections.emptyList();

        List<GPXEntry> gpxList = new ArrayList<>();
        long timeOffset = 0;
        for (int i = 0; i < size() - 1; i++) {
            Instruction prevInstr = (i > 0) ? get(i - 1) : null;
            boolean instrIsFirst = prevInstr == null;
            Instruction nextInstr = get(i + 1);
            nextInstr.checkOne();
            // current instruction does not contain last point which is equals to first point of next instruction:
            timeOffset = fillGPXList(gpxList, timeOffset, get(i), nextInstr);
        }
        Instruction lastI = get(size() - 1);
        if (lastI.getLength() != 0)
            throw new IllegalStateException("Last instruction must have exactly one point but was " + lastI.getLength());
        double lastLat = getFirstLat(lastI), lastLon = getFirstLon(lastI),
                lastEle = getPoints().is3D() ? getFirstEle(lastI) : Double.NaN;
        gpxList.add(new GPXEntry(lastLat, lastLon, lastEle, timeOffset));
        return gpxList;
    }

    /**
     * Latitude of the location where this instruction should take place.
     */
    double getFirstLat(Instruction instruction) {
        return getPoints(instruction).getLatitude(0);
    }

    /**
     * Longitude of the location where this instruction should take place.
     */
    double getFirstLon(Instruction instruction) {
        return getPoints(instruction).getLongitude(0);
    }

    double getFirstEle(Instruction instruction) {
        return getPoints(instruction).getElevation(0);
    }

    /**
     * This method returns a list of gpx entries where the time (in time) is relative to the first
     * which is 0. It does NOT contain the last point which is the first of the next instruction.
     *
     * @return the time offset to add for the next instruction
     */
    long fillGPXList(List<GPXEntry> list, long time, Instruction currentInstruciton, Instruction nextInstr) {
        currentInstruciton.checkOne();
        PointList points = getPoints(currentInstruciton);
        int len = points.size();
        long prevTime = time;
        double lat = points.getLatitude(0);
        double lon = points.getLongitude(0);
        double ele = Double.NaN;
        boolean is3D = points.is3D();
        if (is3D)
            ele = points.getElevation(0);

        for (int i = 0; i < len; i++) {
            list.add(new GPXEntry(lat, lon, ele, prevTime));

            boolean last = i + 1 == len;
            double nextLat = last ? getFirstLat(nextInstr) : points.getLatitude(i + 1);
            double nextLon = last ? getFirstLon(nextInstr) : points.getLongitude(i + 1);
            double nextEle = is3D ? (last ? getFirstEle(nextInstr) : points.getElevation(i + 1)) : Double.NaN;
            if (is3D)
                prevTime = Math.round(prevTime + currentInstruciton.time * Helper.DIST_3D.calcDist(nextLat, nextLon, nextEle, lat, lon, ele) / currentInstruciton.distance);
            else
                prevTime = Math.round(prevTime + currentInstruciton.time * Helper.DIST_3D.calcDist(nextLat, nextLon, lat, lon) / currentInstruciton.distance);

            lat = nextLat;
            lon = nextLon;
            ele = nextEle;
        }
        return time + currentInstruciton.time;
    }


    /**
     * Creates the standard GPX string out of the points according to the schema found here:
     * https://graphhopper.com/public/schema/gpx-1.1.xsd
     * <p>
     *
     * @return string to be stored as gpx file
     */
    public String createGPX() {
        return createGPX("GraphHopper", new Date().getTime());
    }

    public String createGPX(String trackName, long startTimeMillis) {
        boolean includeElevation = size() > 0 && getPoints().is3D();
        return createGPX(trackName, startTimeMillis, includeElevation, true, true, true);
    }

    private void createWayPointBlock(StringBuilder output, Instruction instruction) {
        output.append("\n<wpt ");
        output.append("lat=\"").append(Helper.round6(getFirstLat(instruction)));
        output.append("\" lon=\"").append(Helper.round6(getFirstLon(instruction))).append("\">");
        String name;
        if (instruction.getName().isEmpty())
            name = instruction.getTurnDescription(tr);
        else
            name = instruction.getName();

        output.append(" <name>").append(simpleXMLEscape(name)).append("</name>");
        output.append("</wpt>");
    }

    public String createGPX(String trackName, long startTimeMillis, boolean includeElevation, boolean withRoute, boolean withTrack, boolean withWayPoints) {
        DateFormat formatter = Helper.createFormatter();

        String header = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\" ?>"
                + "<gpx xmlns=\"http://www.topografix.com/GPX/1/1\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
                + " creator=\"Graphhopper version " + Constants.VERSION + "\" version=\"1.1\""
                // This xmlns:gh acts only as ID, no valid URL necessary.
                // Use a separate namespace for custom extensions to make basecamp happy.
                + " xmlns:gh=\"https://graphhopper.com/public/schema/gpx/1.1\">"
                + "\n<metadata>"
                + "<copyright author=\"OpenStreetMap contributors\"/>"
                + "<link href=\"http://graphhopper.com\">"
                + "<text>GraphHopper GPX</text>"
                + "</link>"
                + "<time>" + formatter.format(startTimeMillis) + "</time>"
                + "</metadata>";
        StringBuilder gpxOutput = new StringBuilder(header);
        if (!isEmpty()) {
            if (withWayPoints) {
                createWayPointBlock(gpxOutput, instructions.get(0));   // Start 
                for (Instruction currInstr : instructions) {
                    if ((currInstr.getSign() == Instruction.REACHED_VIA) // Via
                            || (currInstr.getSign() == Instruction.FINISH)) // End
                    {
                        createWayPointBlock(gpxOutput, currInstr);
                    }
                }
            }
            if (withRoute) {
                gpxOutput.append("\n<rte>");
                Instruction nextInstr = null;
                for (Instruction currInstr : instructions) {
                    if (null != nextInstr)
                        createRteptBlock(gpxOutput, nextInstr, currInstr);

                    nextInstr = currInstr;
                }
                createRteptBlock(gpxOutput, nextInstr, null);
                gpxOutput.append("\n</rte>");
            }
        }
        if (withTrack) {
            gpxOutput.append("\n<trk><name>").append(trackName).append("</name>");

            gpxOutput.append("<trkseg>");
            for (GPXEntry entry : createGPXList()) {
                gpxOutput.append("\n<trkpt lat=\"").append(Helper.round6(entry.getLat()));
                gpxOutput.append("\" lon=\"").append(Helper.round6(entry.getLon())).append("\">");
                if (includeElevation)
                    gpxOutput.append("<ele>").append(Helper.round2(entry.getEle())).append("</ele>");
                gpxOutput.append("<time>").append(formatter.format(startTimeMillis + entry.getTime())).append("</time>");
                gpxOutput.append("</trkpt>");
            }
            gpxOutput.append("\n</trkseg>");
            gpxOutput.append("\n</trk>");
        }

        // we could now use 'wpt' for via points
        gpxOutput.append("\n</gpx>");
        return gpxOutput.toString();
    }

    public void createRteptBlock(StringBuilder output, Instruction instruction, Instruction nextI) {
        output.append("\n<rtept lat=\"").append(Helper.round6(getFirstLat(instruction))).
                append("\" lon=\"").append(Helper.round6(getFirstLon(instruction))).append("\">");

        if (!instruction.getName().isEmpty())
            output.append("<desc>").append(simpleXMLEscape(instruction.getTurnDescription(tr))).append("</desc>");

        output.append("<extensions>");
        output.append("<gh:distance>").append(Helper.round(instruction.getDistance(), 1)).append("</gh:distance>");
        output.append("<gh:time>").append(instruction.getTime()).append("</gh:time>");

        String direction = calcDirection(instruction, nextI);
        if (!direction.isEmpty())
            output.append("<gh:direction>").append(direction).append("</gh:direction>");

        double azimuth = calcAzimuth(instruction, nextI);
        if (!Double.isNaN(azimuth))
            output.append("<gh:azimuth>").append(Helper.round2(azimuth)).append("</gh:azimuth>");

        output.append("<gh:sign>").append(instruction.getSign()).append("</gh:sign>");
        output.append("</extensions>");
        output.append("</rtept>");
    }

    /**
     * Return the direction like 'NE' based on the first tracksegment of the instruction. If
     * Instruction does not contain enough coordinate points, an empty string will be returned.
     */
    private String calcDirection(Instruction currentI, Instruction nextI) {
        double azimuth = calcAzimuth(currentI, nextI);
        if (Double.isNaN(azimuth))
            return "";

        return AC.azimuth2compassPoint(azimuth);
    }

    /**
     * Return the azimuth in degree based on the first tracksegment of this instruction. If this
     * instruction contains less than 2 points then NaN will be returned or the specified
     * instruction will be used if that is the finish instruction.
     */
    private double calcAzimuth(Instruction currentI, Instruction nextI) {
        double nextLat;
        double nextLon;
        PointList points = getPoints(currentI);
        PointList nextPoints = getPoints(nextI);

        if (points.getSize() >= 2) {
            nextLat = points.getLatitude(1);
            nextLon = points.getLongitude(1);
        } else if (nextI != null && points.getSize() == 1) {
            nextLat = nextPoints.getLatitude(0);
            nextLon = nextPoints.getLongitude(0);
        } else {
            return Double.NaN;
        }

        double lat = points.getLatitude(0);
        double lon = points.getLongitude(0);
        return AC.calcAzimuth(lat, lon, nextLat, nextLon);
    }

    /**
     * @return list of lat lon
     */
    List<List<Double>> createStartPoints() {
        List<List<Double>> res = new ArrayList<>(instructions.size());
        for (Instruction instruction : instructions) {
            res.add(Arrays.asList(getFirstLat(instruction), getFirstLon(instruction)));
        }
        return res;
    }

    /**
     * This method is useful for navigation devices to find the next instruction for the specified
     * coordinate (e.g. the current position).
     * <p>
     *
     * @param maxDistance the maximum acceptable distance to the instruction (in meter)
     * @return the next Instruction or null if too far away.
     */
    public Instruction find(double lat, double lon, double maxDistance) {
        // handle special cases
        if (size() == 0 || getPoints().getSize() == 0) {
            return null;
        }
        PointList points = getPoints(get(0));
        double prevLat = points.getLatitude(0);
        double prevLon = points.getLongitude(0);
        DistanceCalc distCalc = Helper.DIST_EARTH;
        double foundMinDistance = distCalc.calcNormalizedDist(lat, lon, prevLat, prevLon);
        int foundPointIndex = 0;

        // Search the closest edge to the query point
        if (size() > 1) {
            points = getPoints();
            for (int pointIndex = 1; pointIndex < points.size(); pointIndex++) {
                double currLat = points.getLatitude(pointIndex);
                double currLon = points.getLongitude(pointIndex);

                // calculate the distance from the point to the edge
                double distance;
                if (distCalc.validEdgeDistance(lat, lon, currLat, currLon, prevLat, prevLon)) {
                    distance = distCalc.calcNormalizedEdgeDistance(lat, lon, currLat, currLon, prevLat, prevLon);
                } else {
                    distance = distCalc.calcNormalizedDist(lat, lon, currLat, currLon);
                }

                if (distance < foundMinDistance) {
                    foundMinDistance = distance;
                    foundPointIndex = pointIndex;
                }
                prevLat = currLat;
                prevLon = currLon;
            }
        }

        if (distCalc.calcDenormalizedDist(foundMinDistance) > maxDistance)
            return null;

        // Finish Instruction
        if (foundPointIndex == getPoints().getSize() - 1)
            return get(size() - 1);

        int foundInstruction = -1;

        for (int instructionIndex = 0; instructionIndex < size(); instructionIndex++) {
            Instruction instruction = get(instructionIndex);
            if (instruction.getFirst() <= foundPointIndex && instruction.getLast() > foundPointIndex) {
                foundInstruction = instructionIndex;
                break;
            }
        }

        if (foundInstruction < 0) {
            return null;
        }

        return get(foundInstruction);
    }

    public void setPoints(PointList points) {
        this.points = points;
    }

    public PointList getPoints(Instruction instruction) {
        if (instruction == null)
            return PointList.EMPTY;
        if (instruction.getLength() == 0)
            // Copy does not include the last point
            return this.points.copy(instruction.getFirst(), instruction.getLast() + 1);
        return this.points.copy(instruction.getFirst(), instruction.getLast());
    }

    public PointList getPoints() {
        return this.points;
    }

    /**
     * Appends the insturctionList to this InstructionList, from instruction index 0 to toIndex
     */
    public void append(InstructionList instructionList, int toIndex) {
        if (toIndex >= instructionList.size())
            throw new IllegalArgumentException("Not allowed to pass a toIndex that is bigger than the number of instruction in the InstructionList, you passed " + toIndex + " but is only " + instructionList.size());

        checkConsistency(this);
        checkConsistency(instructionList);

        if (toIndex < instructionList.size() - 1) {
            int toPointRef = instructionList.get(toIndex).getLast();
            this.getPoints().add(instructionList.getPoints(), 0, toPointRef);
        } else {
            this.getPoints().add(instructionList.getPoints());
        }

        int pointIndex = get(size() - 1).getLast();
        for (Instruction instruction : instructionList) {
            instruction.setFirst(pointIndex);
            pointIndex += instruction.getLength();
            instruction.setLast(pointIndex);
            add(instruction);
        }
    }

    private void checkConsistency(InstructionList instructionList) {
        int lastPointIndex = instructionList.getPoints().size();
        int lastPointer = instructionList.get(instructionList.size() - 1).getLast();
        if (lastPointIndex != lastPointer)
            throw new IllegalStateException("InstructionList is inconsistent, it contains " + lastPointIndex + " points, but the last Instruction points to " + lastPointer);
    }

    public void append(InstructionList instructionList) {
        this.append(instructionList, instructionList.size() - 1);
    }
}
