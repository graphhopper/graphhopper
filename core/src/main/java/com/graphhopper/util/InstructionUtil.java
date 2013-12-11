package com.graphhopper.util;

import gnu.trove.list.TDoubleList;
import gnu.trove.list.array.TDoubleArrayList;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class InstructionUtil
{
    /**
     * @return list of indications useful to create images
     */
    public static List<Integer> createIndications(List<Instruction> instructions)
    {
        List<Integer> res = new ArrayList<Integer>(instructions.size());
        for (Instruction instruction : instructions)
        {
            res.add(instruction.getIndication());
        }
        return res;
    }

    public static TDoubleList getDistances(List<Instruction> instructions)
    {
        TDoubleList res = new TDoubleArrayList(instructions.size());
        for (Instruction instruction : instructions)
        {
            res.add(instruction.getDistance());
        }
        return res;
    }

    public static List<String> createDistances(List<Instruction> instructions, TranslationMap.Translation tr, boolean mile)
    {
        TDoubleList distances = getDistances(instructions);

        // United Kingdom, Canada, Ireland, Australia, the Bahamas, India, and Malaysia
        // still use some forms of the Imperial System, but are official Metric Nations
        List<String> labels = new ArrayList<String>(distances.size());
        String country = tr.getLocale().getCountry();
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

    public static List<String> createTimes(List<Instruction> instructions, TranslationMap.Translation tr)
    {
        List<String> res = new ArrayList<String>();
        for (Instruction instruction : instructions)
        {
            long minutes = Math.round(instruction.getTime() / 60.0);
            if (minutes > 60)
            {
                if (minutes / 60.0 > 24)
                {
                    long days = (long) Math.floor(minutes / 60.0 / 24.0);
                    long hours = Math.round((minutes / 60.0) % 24);
                    res.add(String.format("%d %s %d %s", days, tr.tr("dayAbbr"), hours, tr.tr("hourAbbr")));
                }
                else
                {
                    long hours = (long) Math.floor(minutes / 60.0);
                    minutes = Math.round(minutes % 60);
                    res.add(String.format("%d %s %d %s", hours, tr.tr("hourAbbr"), minutes, tr.tr("minAbbr")));
                }
            }
            else
            {
                if (minutes > 0)
                    res.add(String.format("%d %s", minutes, tr.tr("minAbbr")));
                else
                    res.add(String.format(Locale.US, "%.1f %s", instruction.getTime() / 60.0, tr.tr("minAbbr")));
            }
        }
        return res;
    }

    public static List<List<Double>> createSegmentStartPoints(List<Instruction> instructions)
    {
        List<List<Double>> res = new ArrayList<List<Double>>();
        for (Instruction instruction : instructions)
        {
            List<Double> latLng = new ArrayList<Double>(2);
            latLng.add(instruction.getLat());
            latLng.add(instruction.getLon());
            res.add(latLng);
        }
        return res;
    }

    public static List<String> createDescription(List<Instruction> instructions, TranslationMap.Translation tr)
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
            if (indi == Instruction.CONTINUE_ON_STREET)
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

    /**
     * Sets the last added distance and time to the specified value.
     */
    public static void updateLastDistanceAndTime(List<Instruction> instructions, double prevDist, long prevTime)
    {
        if (instructions.isEmpty())
            throw new IllegalStateException("Cannot update last distance with:" + prevDist);
        instructions.get(instructions.size() - 1).setDistance(prevDist);
        instructions.get(instructions.size() - 1).setTime(prevTime);
    }
}
