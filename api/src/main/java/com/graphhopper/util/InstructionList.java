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

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;

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
    private final Translation tr;

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

            int tmpIndex = pointsIndex + instruction.getLength();
            instrJson.put("interval", Arrays.asList(pointsIndex, tmpIndex));
            pointsIndex = tmpIndex;

            counter++;
        }
        return instrList;
    }

    /**
     * @return This method returns a list of gpx entries where the time (in millis) is relative to
     * the first which is 0.
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
            timeOffset = get(i).fillGPXList(gpxList, timeOffset, prevInstr, nextInstr, instrIsFirst);
        }
        Instruction lastI = get(size() - 1);
        if (lastI.points.size() != 1)
            throw new IllegalStateException("Last instruction must have exactly one point but was " + lastI.points.size());
        double lastLat = lastI.getFirstLat(), lastLon = lastI.getFirstLon(),
                lastEle = lastI.getPoints().is3D() ? lastI.getFirstEle() : Double.NaN;
        gpxList.add(new GPXEntry(lastLat, lastLon, lastEle, timeOffset));
        return gpxList;
    }

    /**
     * Creates the standard GPX string out of the points according to the schema found here:
     * https://graphhopper.com/public/schema/gpx-1.1.xsd
     * <p>
     *
     * @return string to be stored as gpx file
     */
    public String createGPX(String version) {
        return createGPX("GraphHopper", new Date().getTime(), version);
    }

    public String createGPX(String trackName, long startTimeMillis, String version) {
        boolean includeElevation = size() > 0 && get(0).getPoints().is3D();
        return createGPX(trackName, startTimeMillis, includeElevation, true, true, true, version);
    }

    private void createWayPointBlock(StringBuilder output, Instruction instruction, DecimalFormat decimalFormat) {
        output.append("\n<wpt ");
        output.append("lat=\"").append(decimalFormat.format(instruction.getFirstLat()));
        output.append("\" lon=\"").append(decimalFormat.format(instruction.getFirstLon())).append("\">");
        String name;
        if (instruction.getName().isEmpty())
            name = instruction.getTurnDescription(tr);
        else
            name = instruction.getName();

        output.append(" <name>").append(simpleXMLEscape(name)).append("</name>");
        output.append("</wpt>");
    }

    public String createGPX(String trackName, long startTimeMillis, boolean includeElevation, boolean withRoute, boolean withTrack, boolean withWayPoints, String version) {
        DateFormat formatter = Helper.createFormatter();

        DecimalFormat decimalFormat = new DecimalFormat("#", DecimalFormatSymbols.getInstance(Locale.ROOT));
        decimalFormat.setMinimumFractionDigits(1);
        decimalFormat.setMaximumFractionDigits(6);
        decimalFormat.setMinimumIntegerDigits(1);

        String header = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\" ?>"
                + "<gpx xmlns=\"http://www.topografix.com/GPX/1/1\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
                + " creator=\"Graphhopper version " + version + "\" version=\"1.1\""
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
                createWayPointBlock(gpxOutput, instructions.get(0), decimalFormat);   // Start
                for (Instruction currInstr : instructions) {
                    if ((currInstr.getSign() == Instruction.REACHED_VIA) // Via
                            || (currInstr.getSign() == Instruction.FINISH)) // End
                    {
                        createWayPointBlock(gpxOutput, currInstr, decimalFormat);
                    }
                }
            }
            if (withRoute) {
                gpxOutput.append("\n<rte>");
                Instruction nextInstr = null;
                for (Instruction currInstr : instructions) {
                    if (null != nextInstr)
                        createRteptBlock(gpxOutput, nextInstr, currInstr, decimalFormat);

                    nextInstr = currInstr;
                }
                createRteptBlock(gpxOutput, nextInstr, null, decimalFormat);
                gpxOutput.append("\n</rte>");
            }
        }
        if (withTrack) {
            gpxOutput.append("\n<trk><name>").append(trackName).append("</name>");

            gpxOutput.append("<trkseg>");
            for (GPXEntry entry : createGPXList()) {
                gpxOutput.append("\n<trkpt lat=\"").append(decimalFormat.format(entry.getLat()));
                gpxOutput.append("\" lon=\"").append(decimalFormat.format(entry.getLon())).append("\">");
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

    public void createRteptBlock(StringBuilder output, Instruction instruction, Instruction nextI, DecimalFormat decimalFormat) {
        output.append("\n<rtept lat=\"").append(decimalFormat.format(instruction.getFirstLat())).
                append("\" lon=\"").append(decimalFormat.format(instruction.getFirstLon())).append("\">");

        if (!instruction.getName().isEmpty())
            output.append("<desc>").append(simpleXMLEscape(instruction.getTurnDescription(tr))).append("</desc>");

        output.append("<extensions>");
        output.append("<gh:distance>").append(Helper.round(instruction.getDistance(), 1)).append("</gh:distance>");
        output.append("<gh:time>").append(instruction.getTime()).append("</gh:time>");

        String direction = instruction.calcDirection(nextI);
        if (!direction.isEmpty())
            output.append("<gh:direction>").append(direction).append("</gh:direction>");

        double azimuth = instruction.calcAzimuth(nextI);
        if (!Double.isNaN(azimuth))
            output.append("<gh:azimuth>").append(Helper.round2(azimuth)).append("</gh:azimuth>");

        if (instruction instanceof RoundaboutInstruction) {
            RoundaboutInstruction ri = (RoundaboutInstruction) instruction;

            output.append("<gh:exit_number>").append(ri.getExitNumber()).append("</gh:exit_number>");
        }

        output.append("<gh:sign>").append(instruction.getSign()).append("</gh:sign>");
        output.append("</extensions>");
        output.append("</rtept>");
    }

    /**
     * @return list of lat lon
     */
    List<List<Double>> createStartPoints() {
        List<List<Double>> res = new ArrayList<>(instructions.size());
        for (Instruction instruction : instructions) {
            res.add(Arrays.asList(instruction.getFirstLat(), instruction.getFirstLon()));
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
        if (size() == 0) {
            return null;
        }
        PointList points = get(0).getPoints();
        double prevLat = points.getLatitude(0);
        double prevLon = points.getLongitude(0);
        DistanceCalc distCalc = Helper.DIST_EARTH;
        double foundMinDistance = distCalc.calcNormalizedDist(lat, lon, prevLat, prevLon);
        int foundInstruction = 0;

        // Search the closest edge to the query point
        if (size() > 1) {
            for (int instructionIndex = 0; instructionIndex < size(); instructionIndex++) {
                points = get(instructionIndex).getPoints();
                for (int pointIndex = 0; pointIndex < points.size(); pointIndex++) {
                    double currLat = points.getLatitude(pointIndex);
                    double currLon = points.getLongitude(pointIndex);

                    if (!(instructionIndex == 0 && pointIndex == 0)) {
                        // calculate the distance from the point to the edge
                        double distance;
                        int index = instructionIndex;
                        if (distCalc.validEdgeDistance(lat, lon, currLat, currLon, prevLat, prevLon)) {
                            distance = distCalc.calcNormalizedEdgeDistance(lat, lon, currLat, currLon, prevLat, prevLon);
                            if (pointIndex > 0)
                                index++;
                        } else {
                            distance = distCalc.calcNormalizedDist(lat, lon, currLat, currLon);
                            if (pointIndex > 0)
                                index++;
                        }

                        if (distance < foundMinDistance) {
                            foundMinDistance = distance;
                            foundInstruction = index;
                        }
                    }
                    prevLat = currLat;
                    prevLon = currLon;
                }
            }
        }

        if (distCalc.calcDenormalizedDist(foundMinDistance) > maxDistance)
            return null;

        // special case finish condition
        if (foundInstruction == size())
            foundInstruction--;

        return get(foundInstruction);
    }

}
