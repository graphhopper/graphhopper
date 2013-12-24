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
package com.graphhopper.util.shapes;

/**
 * @author Peter Karich
 */
public class GHPoint extends CoordTrig<Void>
{
    public GHPoint()
    {
    }

    public GHPoint( double lat, double lon )
    {
        super(lat, lon);
    }

    public static GHPoint parse( String str )
    {
        // if the point is in the format of lat,lon we don't need to call geocoding service
        String[] fromStrs = str.split(",");
        if (fromStrs.length == 2)
        {
            try
            {
                double fromLat = Double.parseDouble(fromStrs[0]);
                double fromLon = Double.parseDouble(fromStrs[1]);
                return new GHPoint(fromLat, fromLon);
            } catch (Exception ex)
            {
            }
        }
        return null;
    }
}
