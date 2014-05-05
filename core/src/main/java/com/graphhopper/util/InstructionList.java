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

    /**
     * Returns the descriptions of the distance per instruction.
     */
    public List<String> createDistances( boolean mile )
    {
        List<String> labels = new ArrayList<String>(instructions.size());
        for (int i = 0; i < instructions.size(); i++)
        {
            double distInMeter = instructions.get(i).getDistance();
            if (mile)
            {
                // calculate miles
                double distInMiles = distInMeter / 1000 / DistanceCalcEarth.KM_MILE;
                if (distInMiles < 0.9)
                {
                    labels.add((int) Helper.round(distInMiles * 5280, 1) + " " + tr.tr("ftAbbr"));
                } else
                {
                    if (distInMiles < 100)
                        labels.add(Helper.round(distInMiles, 2) + " " + tr.tr("miAbbr"));
                    else
                        labels.add((int) Helper.round(distInMiles, 1) + " " + tr.tr("miAbbr"));
                }
            } else
            {
                if (distInMeter < 950)
                {
                    labels.add((int) Helper.round(distInMeter, 1) + " " + tr.tr("mAbbr"));
                } else
                {
                    distInMeter /= 1000;
                    if (distInMeter < 100)
                        labels.add(Helper.round(distInMeter, 2) + " " + tr.tr("kmAbbr"));
                    else
                        labels.add((int) Helper.round(distInMeter, 1) + " " + tr.tr("kmAbbr"));
                }
            }
        }
        return labels;
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
                instrJson.put("annotationText", Helper.firstBig(ia.getMessage()));
                instrJson.put("annotationImportance", ia.getImportance());
            }

            instrJson.put("time", instruction.getTime());
            instrJson.put("distance", Helper.round(instruction.getDistance(), 3));
            instrJson.put("sign", instruction.getSign());

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
        double lastLat = lastI.getFirstLat(), lastLon = lastI.getFirstLon();
        gpxList.add(new GPXEntry(lastLat, lastLon, timeOffset));
        return gpxList;
    }

    /**
     * Creates the GPX Format out of the points.
     * <p/>
     * @return string to be stored as gpx file
     */
    public String createGPX( String trackName, long startTimeMillis, String timeZoneId )
    {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
        TimeZone tz = TimeZone.getDefault();
        if (!Helper.isEmpty(timeZoneId))
            tz = TimeZone.getTimeZone(timeZoneId);

        formatter.setTimeZone(tz);
        String header = "<?xml version='1.0' encoding='UTF-8' standalone='no' ?>"
                + "<gpx xmlns='http://www.topografix.com/GPX/1/1' creator='Graphhopper' version='1.1' >"
                + "<metadata>"
                + "<link href='http://graphhopper.com'>"
                + "<text>GraphHopper GPX</text>"
                + "</link>"
                + "<time>" + tzHack(formatter.format(startTimeMillis)) + "</time>"
                + "</metadata>";
        StringBuilder track = new StringBuilder(header);
        track.append("<trk><name>").append(trackName).append("</name>");

        track.append("<trkseg>");
        for (GPXEntry entry : createGPXList())
        {
            track.append("\n<trkpt lat='").append(Helper.round6(entry.getLat()));
            track.append("' lon='").append(Helper.round6(entry.getLon())).append("'>");
            track.append("<time>").append(tzHack(formatter.format(startTimeMillis + entry.getMillis())));
            track.append("</time>").append("</trkpt>");
        }
        track.append("</trkseg>");
        track.append("</trk>");

        if (!isEmpty())
        {
            track.append("<rte>");
            Instruction nextI = null;
            for (Instruction instr : instructions)
            {
                if (null != nextI)
                    createRteptBlock(track, nextI, instr);

                nextI = instr;
            }
            createRteptBlock(track, nextI, null);
            track.append("</rte>");
        }

        // TODO #147 use wpt for via points!
        track.append("</gpx>");
        return track.toString().replaceAll("\\'", "\"");
    }

    /**
     * Hack to form valid timezone ala +01:00 instead +0100
     */
    private static String tzHack( String str )
    {
        return str.substring(0, str.length() - 2) + ":" + str.substring(str.length() - 2);
    }

    private void createRteptBlock( StringBuilder output, Instruction instruction, Instruction nextI )
    {
        output.append("<rtept lat=\"").append(Helper.round6(instruction.getFirstLat())).
                append("\" lon=\"").append(Helper.round6(instruction.getFirstLon())).append("\">");

        if (!instruction.getName().isEmpty())
            output.append("<desc>").append(instruction.getTurnDescription(tr)).append("</desc>");

        output.append("<extensions>");
        output.append("<distance>").append(Helper.round(instruction.getDistance(), 3)).append("</distance>");
        output.append("<time>").append(instruction.getTime()).append("</time>");

        String direction = instruction.getDirection(nextI);
        if (direction != null)
            output.append("<direction>").append(direction).append("</direction>");

        String azimuth = instruction.getAzimuth(nextI);
        if (azimuth != null)
            output.append("<azimuth>").append(azimuth).append("</azimuth>");

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
}
