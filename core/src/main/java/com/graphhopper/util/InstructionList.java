/*
 *  Licensed to Peter Karich under one or more contributor license
 *  agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  Peter Karich licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the
 *  License at
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

import com.graphhopper.util.TranslationMap.Translation;
import gnu.trove.list.TDoubleList;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TDoubleArrayList;
import gnu.trove.list.array.TIntArrayList;
import java.util.*;

/**
 * Slim list to store several points (without the need for a point object).
 * <p/>
 * @author Ottavio Campana
 * @author Peter Karich
 */
public class InstructionList
{    
    public static final int TURN_SHARP_LEFT = -3;
    public static final int TURN_LEFT = -2;
    public static final int TURN_SLIGHT_LEFT = -1;
    public static final int CONTINUE_ON_STREET = 0;
    public static final int TURN_SLIGHT_RIGHT = 1;
    public static final int TURN_RIGHT = 2;
    public static final int TURN_SHARP_RIGHT = 3;
    private TIntList indications;
    private List<String> names;
    private TDoubleArrayList distances;

    public InstructionList()
    {
        this(10);
    }

    public InstructionList( int cap )
    {
        if (cap < 5)
        {
            cap = 5;
        }
        indications = new TIntArrayList(cap);
        names = new ArrayList<String>(cap);
        distances = new TDoubleArrayList(cap);
    }

    public void add( int indication, String name, double dist )
    {
        indications.add(indication);
        names.add(name);
        distances.add(dist);
    }

    public int size()
    {
        return indications.size();
    }

    public boolean isEmpty()
    {
        return indications.isEmpty();
    }

    public void clear()
    {
        indications.clear();
        names.clear();
        distances.clear();
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < indications.size(); i++)
        {
            if (i > 0)
            {
                sb.append(", ");
            }

            sb.append('(');
            sb.append(indications.get(i));
            sb.append(',');
            sb.append(names.get(i));
            sb.append(',');
            sb.append(distances.get(i));
            sb.append(')');
        }
        return sb.toString();
    }

    /**
     * @return list of indications useful to create images
     */
    public List<Integer> createIndications()
    {
        List<Integer> res = new ArrayList<Integer>(indications.size());
        for (int i = 0; i < indications.size(); i++)
        {
            res.add(indications.get(i));
        }
        return res;
    }

    public TDoubleList getDistances()
    {
        return distances;
    }

    public List<String> createDistances( Translation tr )
    {
        // United Kingdom, Canada, Ireland, Australia, the Bahamas, India, and Malaysia 
        // still use some forms of the Imperial System, but are official Metric Nations
        List<String> labels = new ArrayList<String>(distances.size());
        String country = tr.getLocale().getCountry();
        boolean mile = Locale.US.getCountry().equals(country)
                || Locale.UK.getCountry().equals(country)
                || Locale.CANADA.getCountry().equals(country);

        for (int i = 0; i < distances.size(); i++)
        {
            double dist = distances.get(i);
            if (mile)
            {
                // calculate miles
                double distInMiles = dist / 1000 / DistanceCalc.KM_MILE;
                if (distInMiles < 0.9)
                {
                    labels.add((int) DistanceCalc.round(distInMiles * 5280, 1) + " " + tr.tr("ftAbbr"));
                } else
                {
                    if (distInMiles < 100)
                    {
                        labels.add(DistanceCalc.round(distInMiles, 2) + " " + tr.tr("miAbbr"));
                    } else
                    {
                        labels.add((int) DistanceCalc.round(distInMiles, 1) + " " + tr.tr("miAbbr"));
                    }
                }
            } else
            {
                if (dist < 950)
                {
                    labels.add((int) DistanceCalc.round(dist, 1) + " " + tr.tr("mAbbr"));
                } else
                {
                    if (dist < 100000)
                    {
                        labels.add(DistanceCalc.round(dist / 1000, 2) + " " + tr.tr("kmAbbr"));
                    } else
                    {
                        labels.add((int) DistanceCalc.round(dist / 1000, 1) + " " + tr.tr("kmAbbr"));
                    }
                }
            }
        }
        return labels;
    }

    public List<String> createDescription( Translation tr )
    {        
        String shLeftTr = tr.tr("sharp_left");
        String shRightTr = tr.tr("sharp_right");
        String slLeftTr = tr.tr("slight_left");
        String slRightTr = tr.tr("slight_right");
        String leftTr = tr.tr("left");
        String rightTr = tr.tr("right");
        String continueTr = tr.tr("continue");
        List<String> instructions = new ArrayList<String>(names.size());
        for (int i = 0; i < indications.size(); i++)
        {
            String str;
            String n = names.get(i);
            int indi = indications.get(i);
            if (indi == CONTINUE_ON_STREET)
            {
                str = Helper.isEmpty(n) ? continueTr : tr.tr("continue_onto", n);
            } else
            {
                String dir = null;
                switch (indi)
                {
                    case TURN_SHARP_LEFT:
                        dir = shLeftTr;
                        break;
                    case TURN_LEFT:
                        dir = leftTr;
                        break;
                    case TURN_SLIGHT_LEFT:
                        dir = slLeftTr;
                        break;
                    case TURN_SLIGHT_RIGHT:
                        dir = slRightTr;
                        break;
                    case TURN_RIGHT:
                        dir = rightTr;
                        break;
                    case TURN_SHARP_RIGHT:
                        dir = shRightTr;
                        break;
                }
                if (dir == null)
                {
                    throw new IllegalStateException("Indication not found " + indi);
                }

                str = Helper.isEmpty(n) ? tr.tr("turn", dir) : tr.tr("turn_onto", dir, n);
            }
            instructions.add(Helper.firstBig(str));
        }
        return instructions;
    }

    public void updateLastDistance( double prevDist )
    {
        if (distances.isEmpty())
            throw new IllegalStateException("Cannot update last distance with:" + prevDist);
        distances.set(distances.size() - 1, prevDist);
    }
}
