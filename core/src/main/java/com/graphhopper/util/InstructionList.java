package com.graphhopper.util;

import gnu.trove.list.TDoubleList;
import gnu.trove.list.array.TDoubleArrayList;
import java.text.SimpleDateFormat;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * List of instruction. TODO: the last instruction is a special finish instruction and has only one
 * point and no distance or time.
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

    /**
     * @return list of indications useful to create images
     */
    public List<Integer> createIndications()
    {
        List<Integer> res = new ArrayList<Integer>(instructions.size());
        for (Instruction instruction : instructions)
        {
            res.add(instruction.getIndication());
        }
        return res;
    }

    public TDoubleList createDistances()
    {
        TDoubleList res = new TDoubleArrayList(instructions.size());
        for (Instruction instruction : instructions)
        {
            res.add(instruction.calcDistance());
        }
        return res;
    }

    public List<String> createDistances( TranslationMap.Translation tr, boolean mile )
    {
        TDoubleList distances = createDistances();
        List<String> labels = new ArrayList<String>(distances.size());
        for (int i = 0; i < distances.size(); i++)
        {
            double dist = distances.get(i);
            if (mile)
            {
                // calculate miles
                double distInMiles = dist / 1000 / DistanceCalcEarth.KM_MILE;
                if (distInMiles < 0.9)
                {
                    labels.add((int) DistanceCalcEarth.round(distInMiles * 5280, 1) + " " + tr.tr("ftAbbr"));
                } else
                {
                    if (distInMiles < 100)
                        labels.add(DistanceCalcEarth.round(distInMiles, 2) + " " + tr.tr("miAbbr"));
                    else
                        labels.add((int) DistanceCalcEarth.round(distInMiles, 1) + " " + tr.tr("miAbbr"));
                }
            } else
            {
                if (dist < 950)
                {
                    labels.add((int) DistanceCalcEarth.round(dist, 1) + " " + tr.tr("mAbbr"));
                } else
                {
                    if (dist < 100000)
                        labels.add(DistanceCalcEarth.round(dist / 1000, 2) + " " + tr.tr("kmAbbr"));
                    else
                        labels.add((int) DistanceCalcEarth.round(dist / 1000, 1) + " " + tr.tr("kmAbbr"));
                }
            }
        }
        return labels;
    }

    /**
     * @return string representations of the times until no new instruction.
     */
    public List<String> createTimes( TranslationMap.Translation tr )
    {
        List<String> res = new ArrayList<String>();
        for (Instruction instruction : instructions)
        {
            long millis = instruction.calcMillis();
            int minutes = (int) Math.round(millis / 60000.0);
            if (minutes > 60)
            {
                if (minutes / 60.0 > 24)
                {
                    long days = (long) Math.floor(minutes / 60.0 / 24.0);
                    long hours = Math.round((minutes / 60.0) % 24);
                    res.add(String.format("%d %s %d %s", days, tr.tr("dayAbbr"), hours, tr.tr("hourAbbr")));
                } else
                {
                    long hours = (long) Math.floor(minutes / 60.0);
                    minutes = Math.round(minutes % 60);
                    res.add(String.format("%d %s %d %s", hours, tr.tr("hourAbbr"), minutes, tr.tr("minAbbr")));
                }
            } else
            {
                if (minutes > 0)
                    res.add(String.format("%d %s", minutes, tr.tr("minAbbr")));
                else
                    res.add(String.format(Locale.US, "%.1f %s", millis / 60000.0, tr.tr("minAbbr")));
            }
        }
        return res;
    }

    public List<List<Double>> createSegmentStartPoints()
    {
        List<List<Double>> res = new ArrayList<List<Double>>();
        for (Instruction instruction : instructions)
        {
            List<Double> latLng = new ArrayList<Double>(2);
            latLng.add(instruction.getStartLat());
            latLng.add(instruction.getStartLon());
            res.add(latLng);
        }
        return res;
    }

    public List<String> createDescription( TranslationMap.Translation tr )
    {
        String shLeftTr = tr.tr("sharp_left");
        String shRightTr = tr.tr("sharp_right");
        String slLeftTr = tr.tr("slight_left");
        String slRightTr = tr.tr("slight_right");
        String leftTr = tr.tr("left");
        String rightTr = tr.tr("right");
        String continueTr = tr.tr("continue");
        List<String> res = new ArrayList<String>(instructions.size());
        for (Instruction instruction : instructions)
        {
            String str;
            String n = instruction.getName();
            int indi = instruction.getIndication();
            if (indi == Instruction.FINISH)
            {
                str = tr.tr("finish");
            } else if (indi == Instruction.CONTINUE_ON_STREET)
            {
                str = Helper.isEmpty(n) ? continueTr : tr.tr("continue_onto", n);
            } else
            {
                String dir = null;
                switch (indi)
                {
                    case Instruction.TURN_SHARP_LEFT:
                        dir = shLeftTr;
                        break;
                    case Instruction.TURN_LEFT:
                        dir = leftTr;
                        break;
                    case Instruction.TURN_SLIGHT_LEFT:
                        dir = slLeftTr;
                        break;
                    case Instruction.TURN_SLIGHT_RIGHT:
                        dir = slRightTr;
                        break;
                    case Instruction.TURN_RIGHT:
                        dir = rightTr;
                        break;
                    case Instruction.TURN_SHARP_RIGHT:
                        dir = shRightTr;
                        break;
                }
                if (dir == null)
                    throw new IllegalStateException("Indication not found " + indi);

                str = Helper.isEmpty(n) ? tr.tr("turn", dir) : tr.tr("turn_onto", dir, n);
            }
            res.add(Helper.firstBig(str));
        }
        return res;
    }

    public boolean isEmpty() {
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
     * This method returns a list of gpx entries where the time (in millis) is relative to the first
     * which is 0.
     */
    public List<GPXEntry> createGPXList()
    {
        List<GPXEntry> gpxList = new ArrayList<GPXEntry>();
        long sumTime = 0;
        for (Instruction i : this)
        {
            List<GPXEntry> tmp = i.createGPXList();
            if (tmp.isEmpty())
                throw new IllegalStateException("cannot happen");

            long offset = tmp.get(tmp.size() - 1).getMillis();
            for (GPXEntry e : tmp)
            {
                e.setMillis(sumTime + e.getMillis());
                gpxList.add(e);
            }
            sumTime += offset;
        }
        return gpxList;
    }

    /**
     * Creates the GPX Format out of the points.
     * <p/>
     * @return string to be stored as gpx file
     */
    public String createGPX( String trackName, long startTimeMillis )
    {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:SSZ");
        String header = "<?xml version='1.0' encoding='UTF-8' standalone='no' ?>"
                + "<gpx xmlns='http://www.topografix.com/GPX/1/1' >"
                + "<metadata>"
                + "<link href='http://graphhopper.com'>"
                + "<text>GraphHopper GPX</text>"
                + "</link>"
                + " <time>" + formatter.format(startTimeMillis) + "</time>"
                + "</metadata>";
        StringBuilder track = new StringBuilder(header);
        track.append("<trk><name>").append(trackName).append("</name>");
        track.append("<trkseg>");
        for (GPXEntry entry : createGPXList())
        {
            track.append("<trkpt lat='").append(entry.getLat()).append("' lon='").append(entry.getLon()).append("'>");
            track.append("<time>").append(formatter.format(startTimeMillis + entry.getMillis())).append("</time>");
            track.append("</trkpt>");
        }
        track.append("</trkseg>");
        track.append("</trk></gpx>");
        return track.toString().replaceAll("\\'", "\"");
    }
}
