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

package com.graphhopper.util.gpx;

import com.graphhopper.util.*;
import com.graphhopper.util.shapes.GHPoint3D;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GpxFromInstructions {

    static String simpleXMLEscape(String str) {
        // We could even use the 'more flexible' CDATA section but for now do the following. The 'and' could be important sometimes:
        return str.replaceAll("&", "&amp;").
                // but do not care for:
                        replaceAll("[\\<\\>]", "_");
    }

    public static List<GPXEntry> createGPXList(InstructionList instructions) {
        List<GPXEntry> gpxList = new ArrayList<>();
        long timeOffset = 0;
        for (Instruction instruction : instructions) {
            int i = 0;
            for (GHPoint3D point : instruction.getPoints()) {
                GPXEntry gpxEntry;
                if (i == 0) {
                    gpxEntry = new GPXEntry(point, timeOffset);
                } else {
                    // We don't have timestamps for pillar nodes
                    gpxEntry = new GPXEntry(point);
                }
                gpxList.add(gpxEntry);
                i++;
            }
            timeOffset = timeOffset + instruction.getTime();
        }
        return gpxList;
    }

    private static void createWayPointBlock(StringBuilder output, Instruction instruction, DecimalFormat decimalFormat, Translation tr) {
        output.append("\n<wpt ");
        output.append("lat=\"").append(decimalFormat.format(instruction.getPoints().getLatitude(0)));
        output.append("\" lon=\"").append(decimalFormat.format(instruction.getPoints().getLongitude(0))).append("\">");
        String name;
        if (instruction.getName().isEmpty())
            name = instruction.getTurnDescription(tr);
        else
            name = instruction.getName();

        output.append(" <name>").append(simpleXMLEscape(name)).append("</name>");
        output.append("</wpt>");
    }

    public static String createGPX(InstructionList instructions, String trackName, long startTimeMillis, boolean includeElevation, boolean withRoute, boolean withTrack, boolean withWayPoints, String version, Translation tr) {
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
        if (!instructions.isEmpty()) {
            if (withWayPoints) {
                createWayPointBlock(gpxOutput, instructions.get(0), decimalFormat, tr);   // Start
                for (Instruction currInstr : instructions) {
                    if ((currInstr.getSign() == Instruction.REACHED_VIA) // Via
                            || (currInstr.getSign() == Instruction.FINISH)) // End
                    {
                        createWayPointBlock(gpxOutput, currInstr, decimalFormat, tr);
                    }
                }
            }
            if (withRoute) {
                gpxOutput.append("\n<rte>");
                Instruction nextInstr = null;
                for (Instruction currInstr : instructions) {
                    if (null != nextInstr)
                        createRteptBlock(gpxOutput, nextInstr, currInstr, decimalFormat, tr);

                    nextInstr = currInstr;
                }
                createRteptBlock(gpxOutput, nextInstr, null, decimalFormat, tr);
                gpxOutput.append("\n</rte>");
            }
        }
        if (withTrack) {
            gpxOutput.append("\n<trk><name>").append(trackName).append("</name>");

            gpxOutput.append("<trkseg>");
            for (GPXEntry entry : createGPXList(instructions)) {
                gpxOutput.append("\n<trkpt lat=\"").append(decimalFormat.format(entry.getPoint().getLat()));
                gpxOutput.append("\" lon=\"").append(decimalFormat.format(entry.getPoint().getLon())).append("\">");
                if (includeElevation)
                    gpxOutput.append("<ele>").append(Helper.round2(((GHPoint3D) entry.getPoint()).getEle())).append("</ele>");
                if (entry.getTime() != null)
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

    private static void createRteptBlock(StringBuilder output, Instruction instruction, Instruction nextI, DecimalFormat decimalFormat, Translation tr) {
        output.append("\n<rtept lat=\"").append(decimalFormat.format(instruction.getPoints().getLatitude(0))).
                append("\" lon=\"").append(decimalFormat.format(instruction.getPoints().getLongitude(0))).append("\">");

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

}
