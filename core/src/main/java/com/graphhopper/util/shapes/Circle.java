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

import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.Helper;

/**
 * @author Peter Karich
 */
public class Circle implements Shape
{
    private DistanceCalc calc = Helper.DIST_EARTH;
    private final double radiusInKm;
    private final double lat;
    private final double lon;
    private final double normedDist;
    private final BBox bbox;

    public Circle( double lat, double lon, double radiusInMeter )
    {
        this(lat, lon, radiusInMeter, Helper.DIST_EARTH);
    }

    public Circle( double lat, double lon, double radiusInMeter, DistanceCalc calc )
    {
        this.calc = calc;
        this.lat = lat;
        this.lon = lon;
        this.radiusInKm = radiusInMeter;
        this.normedDist = calc.calcNormalizedDist(radiusInMeter);
        bbox = calc.createBBox(lat, lon, radiusInMeter);
    }

    public double getLat()
    {
        return lat;
    }

    public double getLon()
    {
        return lon;
    }

    @Override
    public boolean contains( double lat1, double lon1 )
    {
        return normDist(lat1, lon1) <= normedDist;
    }

    @Override
    public BBox getBounds()
    {
        return bbox;
    }

    private double normDist( double lat1, double lon1 )
    {
        return calc.calcNormalizedDist(lat, lon, lat1, lon1);
    }

    @Override
    public boolean intersect( Shape o )
    {
        if (o instanceof Circle)
        {
            return intersect((Circle) o);
        } else if (o instanceof BBox)
        {
            return intersect((BBox) o);
        }

        return o.intersect(this);
    }

    @Override
    public boolean contains( Shape o )
    {
        if (o instanceof Circle)
        {
            return contains((Circle) o);
        } else if (o instanceof BBox)
        {
            return contains((BBox) o);
        }

        throw new UnsupportedOperationException("unsupported shape");
    }

    public boolean intersect( BBox b )
    {
        // test top intersect
        if (lat > b.maxLat)
        {
            if (lon < b.minLon)
            {
                return normDist(b.maxLat, b.minLon) <= normedDist;
            }
            if (lon > b.maxLon)
            {
                return normDist(b.maxLat, b.maxLon) <= normedDist;
            }
            return b.maxLat - bbox.minLat > 0;
        }

        // test bottom intersect
        if (lat < b.minLat)
        {
            if (lon < b.minLon)
            {
                return normDist(b.minLat, b.minLon) <= normedDist;
            }
            if (lon > b.maxLon)
            {
                return normDist(b.minLat, b.maxLon) <= normedDist;
            }
            return bbox.maxLat - b.minLat > 0;
        }

        // test middle intersect
        if (lon < b.minLon)
        {
            return bbox.maxLon - b.minLon > 0;
        }
        if (lon > b.maxLon)
        {
            return b.maxLon - bbox.minLon > 0;
        }
        return true;
    }

    public boolean intersect( Circle c )
    {
        // necessary to improve speed?
        if (!getBounds().intersect(c.getBounds()))
        {
            return false;
        }

        return normDist(c.lat, c.lon) <= calc.calcNormalizedDist(radiusInKm + c.radiusInKm);
    }

    public boolean contains( BBox b )
    {
        if (bbox.contains(b))
        {
            return contains(b.maxLat, b.minLon) && contains(b.minLat, b.minLon)
                    && contains(b.maxLat, b.maxLon) && contains(b.minLat, b.maxLon);
        }

        return false;
    }

    public boolean contains( Circle c )
    {
        double res = radiusInKm - c.radiusInKm;
        if (res < 0)
        {
            return false;
        }

        return calc.calcDist(lat, lon, c.lat, c.lon) <= res;
    }

    @Override
    public String toString()
    {
        return lat + "," + lon + ", radius:" + radiusInKm;
    }
}
