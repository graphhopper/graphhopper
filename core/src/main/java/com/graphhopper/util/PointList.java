/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
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

import com.graphhopper.util.shapes.GHPoint;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Slim list to store several points (without the need for a point object).
 * <p/>
 * @author Peter Karich
 */
public class PointList
{
    private double[] latitudes;
    private double[] longitudes;
    private int size = 0;

    public PointList()
    {
        this(10);
    }

    public PointList( int cap )
    {
        latitudes = new double[cap];
        longitudes = new double[cap];
    }

    public void set( int index, double lat, double lon )
    {
        if (index >= size)
            throw new ArrayIndexOutOfBoundsException("index has to be smaller than size " + size);

        latitudes[index] = lat;
        longitudes[index] = lon;
    }

    private void incCap( int newSize )
    {
        if (newSize >= latitudes.length)
        {
            int cap = (int) (newSize * 1.7);
            if (cap < 8)
                cap = 8;
            latitudes = Arrays.copyOf(latitudes, cap);
            longitudes = Arrays.copyOf(longitudes, cap);
        }
    }

    public void add( double lat, double lon )
    {
        int newSize = size + 1;
        incCap(newSize);
        latitudes[size] = lat;
        longitudes[size] = lon;
        size = newSize;
    }

    public void add( PointList points )
    {
        int newSize = size + points.getSize();
        incCap(newSize);
        for (int i = 0; i < points.getSize(); i++)
        {
            int tmp = size + i;
            latitudes[tmp] = points.getLatitude(i);
            longitudes[tmp] = points.getLongitude(i);
        }
        size = newSize;
    }

    public int size()
    {
        return size;
    }

    public int getSize()
    {
        return size;
    }

    public boolean isEmpty()
    {
        return size == 0;
    }

    public double getLatitude( int index )
    {
        if (index >= size)
        {
            throw new ArrayIndexOutOfBoundsException("Tried to access PointList with too big index! "
                    + "index:" + index + ", size:" + size);
        }
        return latitudes[index];
    }

    public double getLongitude( int index )
    {
        if (index >= size)
            throw new ArrayIndexOutOfBoundsException("Tried to access PointList with too big index! "
                    + "index:" + index + ", size:" + size);

        return longitudes[index];
    }

    public void reverse()
    {
        int max = size / 2;
        for (int i = 0; i < max; i++)
        {
            int swapIndex = size - i - 1;

            double tmp = latitudes[i];
            latitudes[i] = latitudes[swapIndex];
            latitudes[swapIndex] = tmp;

            tmp = longitudes[i];
            longitudes[i] = longitudes[swapIndex];
            longitudes[swapIndex] = tmp;
        }
    }

    public void clear()
    {
        size = 0;
    }

    public void trimToSize( int newSize )
    {
        if (newSize > size)
            throw new IllegalArgumentException("new size needs be smaller than old size");

        size = newSize;
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < size; i++)
        {
            if (i > 0)
                sb.append(", ");

            sb.append('(');
            sb.append(latitudes[i]);
            sb.append(',');
            sb.append(longitudes[i]);
            sb.append(')');
        }
        return sb.toString();
    }

    /**
     * Attention: geoJson is LON,LAT
     */
    public List<Double[]> toGeoJson()
    {
        ArrayList<Double[]> points = new ArrayList<Double[]>(size);
        for (int i = 0; i < size; i++)
        {
            points.add(new Double[]
            {
                getLongitude(i), getLatitude(i)
            });
        }
        return points;
    }

    @Override
    public boolean equals( Object obj )
    {
        if (obj == null)
            return false;

        final PointList other = (PointList) obj;
        if (this.size != other.size)
            return false;

        for (int i = 0; i < size; i++)
        {
            if (!NumHelper.equalsEps(latitudes[i], other.latitudes[i]))
                return false;

            if (!NumHelper.equalsEps(longitudes[i], other.longitudes[i]))
                return false;
        }
        return true;
    }

    public PointList clone( boolean reverse )
    {
        PointList clonePL = new PointList(size);
        for (int i = 0; i < size; i++)
        {
            clonePL.add(latitudes[i], longitudes[i]);
        }
        if (reverse)
            clonePL.reverse();
        return clonePL;
    }

    public PointList copy( int from, int end )
    {
        if (from > end)
            throw new IllegalArgumentException("from must be smaller or equals to end");
        if (from < 0 || end > size)
            throw new IllegalArgumentException("Illegal interval: " + from + ", " + end + ", size:" + size);

        PointList copyPL = new PointList(size);
        for (int i = from; i < end; i++)
        {
            copyPL.add(latitudes[i], longitudes[i]);
        }
        return copyPL;
    }

    @Override
    public int hashCode()
    {
        int hash = 5;
        for (int i = 0; i < latitudes.length; i++)
        {
            hash = 73 * hash + (int) Math.round(latitudes[i] * 1000000);
            hash = 73 * hash + (int) Math.round(longitudes[i] * 1000000);
        }
        hash = 73 * hash + this.size;
        return hash;
    }

    public double calcDistance( DistanceCalc calc )
    {
        double lat = -1;
        double lon = -1;
        double dist = 0;
        for (int i = 0; i < size; i++)
        {
            if (i > 0)
                dist += calc.calcDist(lat, lon, latitudes[i], longitudes[i]);

            lat = latitudes[i];
            lon = longitudes[i];
        }
        return dist;
    }

    /**
     * Takes the string from a json array ala [lon1,lat1], [lon2,lat2], ... and fills the list from
     * it.
     */
    public void parseJSON( String str )
    {
        for (String latlon : str.split("\\["))
        {
            if (latlon.trim().length() == 0)
                continue;

            String ll[] = latlon.split(",");
            String lat = ll[1].replace("]", "").trim();
            add(Double.parseDouble(lat), Double.parseDouble(ll[0].trim()));
        }
    }
    public static final PointList EMPTY = new PointList(0)
    {
        @Override
        public void set( int index, double lat, double lon )
        {
            throw new RuntimeException("cannot change EMPTY PointList");
        }

        @Override
        public void add( double lat, double lon )
        {
            throw new RuntimeException("cannot change EMPTY PointList");
        }

        @Override
        public double getLatitude( int index )
        {
            throw new RuntimeException("cannot access EMPTY PointList");
        }

        @Override
        public double getLongitude( int index )
        {
            throw new RuntimeException("cannot access EMPTY PointList");
        }

        @Override
        public void clear()
        {
            throw new RuntimeException("cannot change EMPTY PointList");
        }

        @Override
        public void trimToSize( int newSize )
        {
            throw new RuntimeException("cannot change EMPTY PointList");
        }

        @Override
        public void parseJSON( String str )
        {
            throw new RuntimeException("cannot change EMPTY PointList");
        }

        @Override
        public double calcDistance( DistanceCalc calc )
        {
            return 0;
        }

        @Override
        public PointList copy( int from, int end )
        {
            throw new RuntimeException("cannot copy EMPTY PointList");
        }

        @Override
        public PointList clone( boolean reverse )
        {
            return this;
        }
    };

    public GHPoint toGHPoint( int index )
    {
        return new GHPoint(getLatitude(index), getLongitude(index));
    }
    
    int getCapacity() {
        return latitudes.length;
    }
}
