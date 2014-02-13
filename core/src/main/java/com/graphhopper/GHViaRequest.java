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
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/**
 * GraphHopper request wrapper to simplify requesting GraphHopper.
 * <p/>
 * @author ratrun
 */
public class GHViaRequest
{
    private String algo = "dijkstrabi";
    private List<GHPlace> viaList;
    private Map<String, Object> hints = new HashMap<String, Object>(5);
    private String vehicle = "CAR";
    private String weighting = "shortest";

    /**
     * Calculate the path via the specified place list
     */
    public GHViaRequest( List<GHPlace> viaList )
    {
        this.viaList = viaList;
    }

    /**
     * Calculate the path from specified startPoint to endPoint.
     */
    public GHViaRequest( GHPlace startPoint, GHPlace endPoint )
    {
        this.viaList.set(0, startPoint);
        this.viaList.set(1, endPoint);
    }

    public void check()
    {
        if (viaList.get(0) == null)
            throw new IllegalStateException("the 'from' point needs to be initialized but was null");

        if (viaList.get(0) == null)
            throw new IllegalStateException("the 'to' point needs to be initialized but was null");
    }
    
    public List<GHPlace> getViaList()
    {
       return viaList;
    }

    public GHPlace getFrom()
    {
        return viaList.get(0);
    }

    public GHPlace getTo()
    {
        return viaList.get(1);
    }

    /**
     * Possible values: astar (A* algorithm, default), astarbi (bidirectional A*) dijkstra
     * (Dijkstra), dijkstrabi and dijkstraNative (a bit faster bidirectional Dijkstra).
     */
    public GHViaRequest setAlgorithm( String algo )
    {
        this.algo = algo;
        return this;
    }

    public String getAlgorithm()
    {
        return algo;
    }

    public GHViaRequest putHint( String key, Object value )
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

        return (T) obj;
    }

    @Override
    public String toString()
    {
        String res = new String();
        for (GHPlace place : viaList) 
        {
            res = res + place.toString();
        }
        return res + "(" + algo + ")";
    }

    /**
     * By default it supports fastest and shortest
     */
    public GHViaRequest setWeighting( String w )
    {
        this.weighting = w;
        return this;
    }

    public String getWeighting()
    {
        return weighting;
    }

    public GHViaRequest setVehicle( String vehicle )
    {
        this.vehicle = vehicle;
        return this;
    }

    public String getVehicle()
    {
        return vehicle;
    }
}
