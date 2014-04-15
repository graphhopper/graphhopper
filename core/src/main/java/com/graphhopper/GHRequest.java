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

import com.graphhopper.util.shapes.GHPlace;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GraphHopper request wrapper to simplify requesting GraphHopper.
 * <p/>
 * @author Peter Karich
 * @author ratrun
 */
public class GHRequest
{
    private String algo = "dijkstrabi";
    private List<GHPlace> places = new ArrayList<GHPlace>(5);
    private final Map<String, Object> hints = new HashMap<String, Object>(5);
    private String vehicle = "CAR";
    private String weighting = "fastest";
    private boolean possibleToAdd = false;

    public GHRequest()
    {
        possibleToAdd = true;
    }

    /**
     * Calculate the path from specified startPlace (fromLat, fromLon) to endPlace (toLat, toLon).
     */
    public GHRequest( double fromLat, double fromLon, double toLat, double toLon )
    {
        this(new GHPlace(fromLat, fromLon), new GHPlace(toLat, toLon));
    }

    /**
     * Calculate the path from specified startPlace to endPlace.
     */
    public GHRequest( GHPlace startPlace, GHPlace endPlace )
    {
        if (startPlace == null)
            throw new IllegalStateException("'from' cannot be null");

        if (endPlace == null)
            throw new IllegalStateException("'to' cannot be null");
        places.add(startPlace);
        places.add(endPlace);
    }

    public GHRequest( List<GHPlace> places )
    {
        this.places = places;
    }

    public GHRequest addPlace( GHPlace place )
    {
        if (place == null)
            throw new IllegalArgumentException("place cannot be null");
        if (!possibleToAdd)
            throw new IllegalStateException("Please call empty constructor if you intent to use "
                    + "more than two places via addPlace method.");

        places.add(place);
        return this;
    }

    public List<GHPlace> getPlaces()
    {
        return places;
    }

    /**
     * Possible values: astar (A* algorithm, default), astarbi (bidirectional A*) dijkstra
     * (Dijkstra), dijkstrabi and dijkstraNativebi (a bit faster bidirectional Dijkstra).
     */
    public GHRequest setAlgorithm( String algo )
    {
        this.algo = algo;
        return this;
    }

    public String getAlgorithm()
    {
        return algo;
    }

    public GHRequest putHint( String key, Object value )
    {
        Object old = hints.put(key, value);
        if (old != null)
            throw new RuntimeException("Key is already associated with " + old + ", your value:" + value);

        return this;
    }

    @SuppressWarnings("unchecked")
    public <T> T getHint( String key, T defaultValue )
    {
        Object obj = hints.get(key);
        if (obj == null)
            return defaultValue;

        if (defaultValue != null && defaultValue instanceof Number)
        {
            // what a monster! see #173
            if (defaultValue instanceof Double)
                return (T) (Double) ((Number) obj).doubleValue();
            if (defaultValue instanceof Long)
                return (T) (Long) ((Number) obj).longValue();
        }

        return (T) obj;
    }

    @Override
    public String toString()
    {
        String res = "";
        for (GHPlace place : places)
        {
            if (res.isEmpty())
                res = place.toString();
            else
                res += "; " + place.toString();
        }
        return res + "(" + algo + ")";
    }

    /**
     * By default it supports fastest and shortest
     */
    public GHRequest setWeighting( String w )
    {
        this.weighting = w;
        return this;
    }

    public String getWeighting()
    {
        return weighting;
    }

    public GHRequest setVehicle( String vehicle )
    {
        this.vehicle = vehicle;
        return this;
    }

    public String getVehicle()
    {
        return vehicle;
    }
}
