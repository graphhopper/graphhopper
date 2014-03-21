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
    private final List<Instruction> instructions;

    public InstructionList()
    {
        this(10);
    }

    public InstructionList( int cap )
    {
        instructions = new ArrayList<Instruction>(cap);
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

    public List<String> createDistances( TranslationMap.Translation tr, boolean mile )
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
                    labels.add((int) round(distInMiles * 5280, 1) + " " + tr.tr("ftAbbr"));
                } else
                {
                    if (distInMiles < 100)
                        labels.add(round(distInMiles, 2) + " " + tr.tr("miAbbr"));
                    else
                        labels.add((int) round(distInMiles, 1) + " " + tr.tr("miAbbr"));
                }
            } else
            {
                if (distInMeter < 950)
                {
                    labels.add((int) round(distInMeter, 1) + " " + tr.tr("mAbbr"));
                } else
                {
                    distInMeter /= 1000;
                    if (distInMeter < 100)
                        labels.add(round(distInMeter, 2) + " " + tr.tr("kmAbbr"));
                    else
                        labels.add((int) round(distInMeter, 1) + " " + tr.tr("kmAbbr"));
                }
            }
        }
        return labels;
    }

    public List<Map<String, Object>> createJson( TranslationMap.Translation tr )
    {
        List<Map<String, Object>> instrList = new ArrayList<Map<String, Object>>(instructions.size());
        int pointsIndex = 0;
        int counter = 0;
        for (Instruction instruction : instructions)
        {
            Map<String, Object> instrJson = new HashMap<String, Object>();
            instrList.add(instrJson);

            instrJson.put("text", Helper.firstBig(getTurnDescription(instruction, tr)));
            instrJson.put("time", instruction.getTime());
            instrJson.put("distance", instruction.getDistance());
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

    private String getTurnDescription( Instruction instruction, TranslationMap.Translation tr )
    {
        String str;
        String n = getWayName(instruction.getName(), instruction.getPavement(), instruction.getWayType(), tr);
        int indi = instruction.getSign();
        if (indi == Instruction.FINISH)
        {
            str = tr.tr("finish");
        } else if (indi == Instruction.CONTINUE_ON_STREET)
        {
            str = Helper.isEmpty(n) ? tr.tr("continue") : tr.tr("continue_onto", n);
        } else
        {
            String dir = null;
            switch (indi)
            {
                case Instruction.TURN_SHARP_LEFT:
                    dir = tr.tr("sharp_left");
                    break;
                case Instruction.TURN_LEFT:
                    dir = tr.tr("left");
                    break;
                case Instruction.TURN_SLIGHT_LEFT:
                    dir = tr.tr("slight_left");
                    break;
                case Instruction.TURN_SLIGHT_RIGHT:
                    dir = tr.tr("slight_right");
                    break;
                case Instruction.TURN_RIGHT:
                    dir = tr.tr("right");
                    break;
                case Instruction.TURN_SHARP_RIGHT:
                    dir = tr.tr("sharp_right");
                    break;
            }
            if (dir == null)
                throw new IllegalStateException("Indication not found " + indi);

            str = Helper.isEmpty(n) ? tr.tr("turn", dir) : tr.tr("turn_onto", dir, n);
        }
        return str;
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
            track.append("\n<trkpt lat='").append(entry.getLat()).append("' lon='").append(entry.getLon()).append("'>");
            track.append("<time>").append(tzHack(formatter.format(startTimeMillis + entry.getMillis()))).append("</time>");
            track.append("</trkpt>");
        }
        track.append("</trkseg>");
        track.append("</trk>");

        if (!isEmpty())
        {
            track.append("<rte>");
            for (Instruction i : instructions)
            {
                createExtensionsBlock(track, i);
            }
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

    private static final TranslationMap.Translation NO_TRANSLATE = new TranslationMap.Translation()
    {

        @Override
        public String tr( String key, Object... params )
        {
            if (key.equals("turn_onto") || key.equals("turn"))
                key = "";

            for (Object p : params)
            {
                key += " " + p.toString();
            }
            return key.trim();
        }

        @Override
        public Map<String, String> asMap()
        {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Locale getLocale()
        {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public String getLanguage()
        {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    };

    private String createExtensionsBlock( StringBuilder sbEx, Instruction instruction )
    {
        sbEx.append("<rtept lat=\"").append(InstructionList.round(instruction.getFirstLat(), 6)).
                append("\" lon=\"").append(InstructionList.round(instruction.getFirstLon(), 6)).append("\">");

        if (!instruction.getName().isEmpty())
            sbEx.append("<desc>").append(getTurnDescription(instruction, NO_TRANSLATE)).append("</desc>");

        sbEx.append("<extensions>");

        sbEx.append("<distance>").append((int) instruction.getDistance()).append("</distance>");
        sbEx.append("<time>").append(instruction.getTime()).append("</time>");

        // sbEx.append("<direction>").append(instruction.getDirection()).append("</direction>");
        // sbEx.append("<azimuth>").append(instruction.getAzimutz()).append("</azimuth>");
        sbEx.append("</extensions>");
        sbEx.append("</rtept>");
        return sbEx.toString();
    }

    public static String getWayName( String name, int pavetype, int waytype, TranslationMap.Translation tr )
    {
        String pavementName = "";
        if (pavetype == 1)
            pavementName = tr.tr("unpaved");

        String wayClass = "";
        switch (waytype)
        {
            case 0:
                wayClass = tr.tr("road");
                break;
            case 1:
                wayClass = tr.tr("pushing_section");
                break;
            case 2:
                wayClass = tr.tr("cycleway");
                break;
            case 3:
                wayClass = tr.tr("way");
                break;
        }

        if (name.isEmpty())
            if (pavementName.isEmpty())
                return wayClass;
            else
                return wayClass + ", " + pavementName;
        else if (pavementName.isEmpty())
            if (waytype == 0)
                return name;
            else
                return name + ", " + wayClass;
        else
            return name + ", " + pavementName;
    }

    /**
     * Round the value to the specified exponent
     */
    static double round( double value, int exponent )
    {
        double factor = Math.pow(10, exponent);
        return Math.round(value * factor) / factor;
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
