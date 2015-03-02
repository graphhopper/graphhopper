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
package com.graphhopper;

import com.graphhopper.routing.util.WeightingMap;
import com.graphhopper.util.Helper;
import com.graphhopper.util.shapes.GHPoint;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * GraphHopper request wrapper to simplify requesting GraphHopper.
 * <p/>
 * @author Peter Karich
 * @author ratrun
 */
public class GHRequest
{
    private String algo = "";
    private List<GHPoint> points;
    private final WeightingMap hints = new WeightingMap();
    private String vehicle = "";
    private boolean possibleToAdd = false;
    private Locale locale = Locale.US;

    public GHRequest()
    {
        this(5);
    }

    public GHRequest( int size )
    {
        points = new ArrayList<GHPoint>(size);
        possibleToAdd = true;
    }

    /**
     * Calculate the path from specified startPlace (fromLat, fromLon) to endPlace (toLat, toLon).
     */
    public GHRequest( double fromLat, double fromLon, double toLat, double toLon )
    {
        this(new GHPoint(fromLat, fromLon), new GHPoint(toLat, toLon));
    }

    /**
     * Calculate the path from specified startPlace to endPlace.
     */
    public GHRequest( GHPoint startPlace, GHPoint endPlace )
    {
        if (startPlace == null)
            throw new IllegalStateException("'from' cannot be null");

        if (endPlace == null)
            throw new IllegalStateException("'to' cannot be null");
        points = new ArrayList<GHPoint>(2);
        points.add(startPlace);
        points.add(endPlace);
    }

    public GHRequest( List<GHPoint> points )
    {
        this.points = points;
    }

    public GHRequest addPoint( GHPoint point )
    {
        if (point == null)
            throw new IllegalArgumentException("point cannot be null");
        if (!possibleToAdd)
            throw new IllegalStateException("Please call empty constructor if you intent to use "
                    + "more than two places via addPlace method.");

        points.add(point);
        return this;
    }

    public List<GHPoint> getPoints()
    {
        return points;
    }

    /**
     * For possible values see AlgorithmOptions.*
     */
    public GHRequest setAlgorithm( String algo )
    {
        if (algo != null)
            this.algo = algo;
        return this;
    }

    public String getAlgorithm()
    {
        return algo;
    }

    public Locale getLocale()
    {
        return locale;
    }

    public GHRequest setLocale( Locale locale )
    {
        if (locale != null)
            this.locale = locale;
        return this;
    }

    public GHRequest setLocale( String localeStr )
    {
        return setLocale(Helper.getLocale(localeStr));
    }

    /**
     * By default it supports fastest and shortest. Or specify empty to use default.
     */
    public GHRequest setWeighting( String w )
    {
        hints.setWeighting(w);
        return this;
    }

    public String getWeighting()
    {
        return hints.getWeighting();
    }

    /**
     * Specifiy car, bike or foot. Or specify empty to use default.
     */
    public GHRequest setVehicle( String vehicle )
    {
        if (vehicle != null)
            this.vehicle = vehicle;
        return this;
    }

    public String getVehicle()
    {
        return vehicle;
    }

    @Override
    public String toString()
    {
        String res = "";
        for (GHPoint point : points)
        {
            if (res.isEmpty())
                res = point.toString();
            else
                res += "; " + point.toString();
        }
        return res + "(" + algo + ")";
    }

    public WeightingMap getHints()
    {
        return hints;
    }
}
