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

import com.graphhopper.util.shapes.GHPoint;
import java.util.Map;

/**
 * @author Peter Karich
 */
public class GPXEntry extends GHPoint
{
    private long time;

    // data which fits the extensions type of the gpx schema definition:
    Map<String, String> extensions;
        
    public GPXEntry( double lat, double lon, long millis, Map<String, String> extensions )
    {
        super(lat, lon);
        this.time = millis;
        this.extensions = extensions;
    }

    public GPXEntry( GHPoint p, long millis, Map<String, String> extensions)
    {
        this(p.lat, p.lon, millis, extensions);
    }

    /**
     * The time relative to the start time.
     */
    public long getMillis()
    {
        return time;
    }

    public void setMillis( long time )
    {
        this.time = time;
    }

    @Override
    public boolean equals( Object obj )
    {
        if (obj == null)
            return false;

        final GPXEntry other = (GPXEntry) obj;
        return time == other.time && super.equals(obj);
    }

    @Override
    public String toString()
    {
        return super.toString() + ", " + time;
    }
}
