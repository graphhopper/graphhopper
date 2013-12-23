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
package com.graphhopper.storage.index;

/**
 * http://en.wikipedia.org/wiki/Bresenham%27s_line_algorithm or even better:
 * http://en.wikipedia.org/wiki/Xiaolin_Wu%27s_line_algorithm
 * <p/>
 * @author Peter Karich
 */
public class BresenhamLine
{
    // http://en.wikipedia.org/wiki/Bresenham%27s_line_algorithm#Simplification
    public static void calcPoints( int y1, int x1, int y2, int x2,
            PointEmitter emitter )
    {
        boolean latIncreasing = y1 < y2;
        boolean lonIncreasing = x1 < x2;
        int dLat = Math.abs(y2 - y1), sLat = latIncreasing ? 1 : -1;
        int dLon = Math.abs(x2 - x1), sLon = lonIncreasing ? 1 : -1;
        int err = 2 * (dLon - dLat);

        while (true)
        {
            emitter.set(y1, x1);
            if (y1 == y2 && x1 == x2)
            {
                break;
            }
            int tmpErr = err;
            if (tmpErr > -dLat)
            {
                err -= dLat;
                x1 += sLon;
            }
            if (tmpErr < dLon)
            {
                err += dLon;
                y1 += sLat;
            }
        }
    }

    public static void calcPoints( double lat1, double lon1,
            double lat2, double lon2, final PointEmitter emitter,
            final double offsetLat, final double offsetLon,
            final double deltaLat, final double deltaLon )
    {
        // round to make results of bresenham closer to correct solution
        int y1 = (int) Math.round((lat1 - offsetLat) / deltaLat);
        int x1 = (int) Math.round((lon1 - offsetLon) / deltaLon);
        int y2 = (int) Math.round((lat2 - offsetLat) / deltaLat);
        int x2 = (int) Math.round((lon2 - offsetLon) / deltaLon);
        calcPoints(y1, x1, y2, x2, new PointEmitter()
        {
            @Override
            public void set( double lat, double lon )
            {
                emitter.set(((lat) * deltaLat + offsetLat),
                        ((lon) * deltaLon + offsetLon));
            }
        });
    }

    public static void calcPointsOffset( double lat1, double lon1,
            double lat2, double lon2, final PointEmitter emitter,
            final double offsetLat, final double offsetLon,
            final double deltaLat, final double deltaLon )
    {
        int y1 = (int) ((lat1 - offsetLat) / deltaLat);
        int x1 = (int) ((lon1 - offsetLon) / deltaLon);
        int y2 = (int) ((lat2 - offsetLat) / deltaLat);
        int x2 = (int) ((lon2 - offsetLon) / deltaLon);
        calcPoints(y1, x1, y2, x2, new PointEmitter()
        {
            @Override
            public void set( double lat, double lon )
            {
                // +0.5 to move into the center of the tile
                emitter.set((lat + .1) * deltaLat + offsetLat, (lon + .1) * deltaLon + offsetLon);
            }
        });
    }
}
