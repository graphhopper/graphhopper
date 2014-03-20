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
package com.graphhopper.reader;

import com.graphhopper.storage.DataAccess;
import com.graphhopper.storage.Directory;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PointAccess;

/**
 * This class helps to store lat,lon,ele for every node parsed in OSMReader
 * @author Peter Karich
 */
public class PillarInfo implements PointAccess
{
    private final int LAT = 0 * 4, LON = 1 * 4, ELE = 2 * 4;
    private final boolean enabled3D;
    private final DataAccess da;
    private final int rowSizeInBytes;
    private final Directory dir;

    public PillarInfo( boolean enabled3D, Directory dir )
    {
        this.enabled3D = enabled3D;
        this.dir = dir;
        this.da = dir.find("tmpPillarInfo").create(100);
        this.rowSizeInBytes = getDimension() * 4;
    }

    @Override
    public boolean is3D()
    {
        return enabled3D;
    }

    @Override
    public int getDimension()
    {
        return enabled3D ? 3 : 2;
    }

    @Override
    public void setNode( int id, double lat, double lon )
    {
//        if (is3D())
//            throw new IllegalStateException("Can only be called if 3D is disabled");

        _setNode(id, lat, lon, Double.NaN);
    }

    @Override
    public void setNode( int id, double lat, double lon, double ele )
    {
//        if (!is3D())
//            throw new IllegalStateException("Can only be called if 3D is enabled");
        _setNode(id, lat, lon, ele);
    }

    private void _setNode( int id, double lat, double lon, double ele )
    {
        long tmp = (long) id * rowSizeInBytes;
        da.incCapacity(tmp + rowSizeInBytes);        
        da.setInt(tmp + LAT, Helper.degreeToInt(lat));
        da.setInt(tmp + LON, Helper.degreeToInt(lon));
        
        if (is3D())                    
            da.setInt(tmp + ELE, Helper.eleToInt(ele));        
    }

    @Override
    public double getLatitude( int id )
    {
        int intVal = da.getInt((long) id * rowSizeInBytes + LAT);        
        return Helper.intToDegree(intVal);
    }

    @Override
    public double getLat( int id )
    {
        return getLatitude(id);
    }

    @Override
    public double getLongitude( int id )
    {
        int intVal = da.getInt((long) id * rowSizeInBytes + LON);        
        return Helper.intToDegree(intVal);
    }

    @Override
    public double getLon( int id )
    {
        return getLongitude(id);
    }

    @Override
    public double getElevation( int id )
    {
        if(!is3D())
            return Double.NaN;
        
        int intVal = da.getInt((long) id * rowSizeInBytes + ELE);        
        return Helper.intToEle(intVal);
    }

    @Override
    public double getEle( int id )
    {
        return getElevation(id);
    }

    public void clear()
    {
        dir.remove(da);
    }
}
