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
package com.graphhopper.util;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * List of instructions.
 */
public class InstructionList implements Iterable<Instruction>
{
    public static final InstructionList EMPTY = new InstructionList();
    private final List<Instruction> instructions;
    private final Translation tr;

    private InstructionList()
    {
        this(0, null);
    }

    public InstructionList( Translation tr )
    {
        this(10, tr);
    }

    public InstructionList( int cap, Translation tr )
    {
        instructions = new ArrayList<Instruction>(cap);
        this.tr = tr;
    }

    public void replaceLast( Instruction instr )
    {
        if (instructions.isEmpty())
            throw new IllegalStateException("Cannot replace last instruction as list is empty");

        instructions.set(instructions.size() - 1, instr);
    }

    public void add( Instruction instr )
    {
        instructions.add(instr);
    }

    public int getSize()
    {
        return instructions.size();
    }

    public int size()
    {
        return instructions.size();
    }

    public List<Map<String, Object>> createJson()
    {
        List<Map<String, Object>> instrList = new ArrayList<Map<String, Object>>(instructions.size());
        int pointsIndex = 0;
        int counter = 0;
        for (Instruction instruction : instructions)
        {
            Map<String, Object> instrJson = new HashMap<String, Object>();
            instrList.add(instrJson);

            InstructionAnnotation ia = instruction.getAnnotation();
            String str = instruction.getTurnDescription(tr);
            if (Helper.isEmpty(str))
                str = ia.getMessage();
            instrJson.put("text", Helper.firstBig(str));
            if (!ia.isEmpty())
            {
                instrJson.put("annotation_text", ia.getMessage());
                instrJson.put("annotation_importance", ia.getImportance());
            }

            instrJson.put("time", instruction.getTime());
            instrJson.put("distance", Helper.round(instruction.getDistance(), 3));
            instrJson.put("sign", instruction.getSign());
            instrJson.putAll(instruction.getExtraInfoJSON());

            int tmpIndex = pointsIndex + instruction.getPoints().size();
            // the last instruction should not point to the next instruction
            if (counter + 1 == instructions.size())
                tmpIndex--;

            instrJson.put("interval", Arrays.asList(pointsIndex, tmpIndex));
            pointsIndex = tmpIndex;

            counter++;
        }
        return instrList;
    }

    public boolean isEmpty()
    {
        return instructions.isEmpty();
    }

    @Override
    public Iterator<Instruction> iterator()
    {
        return instructions.iterator();
    }

    public Instruction get( int index )
    {
        return instructions.get(index);
    }

    @Override
    public String toString()
    {
        return instructions.toString();
    }

    /**
     * @return This method returns a list of gpx entries where the time (in millis) is relative to
     * the first which is 0.
     * <p>
     */
    public List<GPXEntry> createGPXList()
    {
        if (isEmpty())
            return Collections.emptyList();

        List<GPXEntry> gpxList = new ArrayList<GPXEntry>();
        long timeOffset = 0;
        for (int i = 0; i < size() - 1; i++)
        {
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
     * <p/>
     * @return string to be stored as gpx file
     */
    public String createGPX()
    {
        return createGPX("GraphHopper", new Date().getTime());
    }

    public String createGPX( String trackName, long startTimeMillis )
    {
        boolean includeElevation = getSize() > 0 ? get(0).getPoints().is3D() : false;
        return createGPX(trackName, startTimeMillis, includeElevation);
    }

    public String createGPX( String trackName, long startTimeMillis, boolean includeElevation )
    {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));

        String header = "<?xml version='1.0' encoding='UTF-8' standalone='no' ?>"
                + "<gpx xmlns='http://www.topografix.com/GPX/1/1' xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'"
                + " creator='Graphhopper' version='1.1'"
                // This xmlns:gh acts only as ID, no valid URL necessary.
                // Use a separate namespace for custom extensions to make basecamp happy.
                + " xmlns:gh='https://graphhopper.com/public/schema/gpx/1.1'>"
                + "\n<metadata>"
                + "<copyright author=\"OpenStreetMap contributors\"/>"
                + "<link href='http://graphhopper.com'>"
                + "<text>GraphHopper GPX</text>"
                + "</link>"
                + "<time>" + formatter.format(startTimeMillis) + "</time>"
                + "</metadata>";
        StringBuilder track = new StringBuilder(header);
        if (!isEmpty())
        {
            track.append("\n<rte>");
            Instruction nextInstr = null;
            for (Instruction currInstr : instructions)
            {
                if (null != nextInstr)
                    createRteptBlock(track, nextInstr, currInstr);

                nextInstr = currInstr;
            }
            createRteptBlock(track, nextInstr, null);
            track.append("</rte>");
        }

        track.append("\n<trk><name>").append(trackName).append("</name>");

        track.append("<trkseg>");
        for (GPXEntry entry : createGPXList())
        {
            track.append("\n<trkpt lat='").append(Helper.round6(entry.getLat()));
            track.append("' lon='").append(Helper.round6(entry.getLon())).append("'>");
            if (includeElevation)
                track.append("<ele>").append(Helper.round2(entry.getEle())).append("</ele>");
            track.append("<time>").append(formatter.format(startTimeMillis + entry.getTime())).append("</time>");
            track.append("</trkpt>");
        }
        track.append("</trkseg>");
        track.append("</trk>");

        // we could now use 'wpt' for via points
        track.append("</gpx>");
        return track.toString().replaceAll("\\'", "\"");
    }

    private void createRteptBlock( StringBuilder output, Instruction instruction, Instruction nextI )
    {
        output.append("\n<rtept lat=\"").append(Helper.round6(instruction.getFirstLat())).
                append("\" lon=\"").append(Helper.round6(instruction.getFirstLon())).append("\">");

        if (!instruction.getName().isEmpty())
            output.append("<desc>").append(instruction.getTurnDescription(tr)).append("</desc>");

        output.append("<extensions>");
        output.append("<gh:distance>").append(Helper.round(instruction.getDistance(), 1)).append("</gh:distance>");
        output.append("<gh:time>").append(instruction.getTime()).append("</gh:time>");

        String direction = instruction.calcDirection(nextI);
        if (!direction.isEmpty())
            output.append("<gh:direction>").append(direction).append("</gh:direction>");

        double azimuth = instruction.calcAzimuth(nextI);
        if (!Double.isNaN(azimuth))
            output.append("<gh:azimuth>").append(Helper.round2(azimuth)).append("</gh:azimuth>");

        output.append("<gh:sign>").append(instruction.getSign()).append("</gh:sign>");
        output.append("</extensions>");
        output.append("</rtept>");
    }

    /**
     * @return list of lat lon
     */
    List<List<Double>> createStartPoints()
    {
        List<List<Double>> res = new ArrayList<List<Double>>(instructions.size());
        for (Instruction instruction : instructions)
        {
            res.add(Arrays.asList(instruction.getFirstLat(), instruction.getFirstLon()));
        }
        return res;
    }

    /**
     * This method is useful for navigation devices to find the next instruction for the specified
     * coordinate (e.g. the current position).
     * <p>
     * @param maxDistance the maximum acceptable distance to the instruction (in meter)
     * @return the next Instruction or null if too far away.
     */
    public Instruction find( double lat, double lon, double maxDistance )
    {
        // handle special cases
        if (getSize() == 0)
        {
            return null;
        }
        PointList points = get(0).getPoints();
        double prevLat = points.getLatitude(0);
        double prevLon = points.getLongitude(0);
        DistanceCalc distCalc = Helper.DIST_EARTH;
        double foundMinDistance = distCalc.calcNormalizedDist(lat, lon, prevLat, prevLon);
        int foundInstruction = 0;

        // Search the closest edge to the query point
        if (getSize() > 1)
        {
            for (int instructionIndex = 0; instructionIndex < getSize(); instructionIndex++)
            {
                points = get(instructionIndex).getPoints();
                for (int pointIndex = 0; pointIndex < points.size(); pointIndex++)
                {
                    double currLat = points.getLatitude(pointIndex);
                    double currLon = points.getLongitude(pointIndex);

                    if (!(instructionIndex == 0 && pointIndex == 0))
                    {
                        // calculate the distance from the point to the edge
                        double distance;
                        int index = instructionIndex;
                        if (distCalc.validEdgeDistance(lat, lon, currLat, currLon, prevLat, prevLon))
                        {
                            distance = distCalc.calcNormalizedEdgeDistance(lat, lon, currLat, currLon, prevLat, prevLon);
                            if (pointIndex > 0)
                                index++;
                        } else
                        {
                            distance = distCalc.calcNormalizedDist(lat, lon, currLat, currLon);
                        }

                        if (distance < foundMinDistance)
                        {
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
        if (foundInstruction == getSize())
            foundInstruction--;

        return get(foundInstruction);
    }

}
