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
package com.graphhopper.transit;

import com.graphhopper.GHRequest;
import com.graphhopper.transit.util.GHPlaceTime;
import com.graphhopper.util.shapes.GHPlace;

/**
 * GraphHopper request wrapper to simplify requesting GraphHopper.
 * <p/>
 * @author Peter Karich
 */
public class TransitRequest extends GHRequest
{
    /**
     * Calculate the path from specified startPoint (fromLat, fromLon) and startTime to endPoint
     * (toLat, toLon).
     */
    public TransitRequest( double fromLat, double fromLon, int startTime, double toLat, double toLon )
    {
        super(new GHPlaceTime(fromLat, fromLon, startTime), new GHPlace(toLat, toLon));
    }

    @Override
    public GHPlaceTime getFrom()
    {
        return (GHPlaceTime) super.getFrom();
    }
}
