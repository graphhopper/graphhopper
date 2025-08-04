/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
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
package com.graphhopper.reader.osm;

import com.graphhopper.storage.DataAccess;
import com.graphhopper.storage.Directory;
import com.graphhopper.util.Helper;

/**
 * This class helps to store lat,lon,ele for every node parsed in OSMReader
 * <p>
 *
 * @author Peter Karich
 */
public class PillarInfo {
    private static final int LAT = 0 * 4, LON = 1 * 4, ELE = 2 * 4;
    private final boolean enabled3D;
    private final DataAccess da;
    private final int rowSizeInBytes;
    private final Directory dir;

    public PillarInfo(boolean enabled3D, Directory dir) {
        this.enabled3D = enabled3D;
        this.dir = dir;
        this.da = dir.create("tmp_pillar_info").create(100);
        this.rowSizeInBytes = getDimension() * 4;
    }

    public boolean is3D() {
        return enabled3D;
    }

    public int getDimension() {
        return enabled3D ? 3 : 2;
    }

    public void ensureNode(long nodeId) {
        long tmp = nodeId * rowSizeInBytes;
        da.ensureCapacity(tmp + rowSizeInBytes);
    }

    public void setNode(long nodeId, double lat, double lon, double ele) {
        ensureNode(nodeId);
        long tmp = nodeId * rowSizeInBytes;
        da.setInt(tmp + LAT, Helper.degreeToInt(lat));
        da.setInt(tmp + LON, Helper.degreeToInt(lon));

        if (is3D())
            da.setInt(tmp + ELE, Helper.eleToUInt(ele));
    }

    public double getLat(long id) {
        int intVal = da.getInt(id * rowSizeInBytes + LAT);
        return Helper.intToDegree(intVal);
    }

    public double getLon(long id) {
        int intVal = da.getInt(id * rowSizeInBytes + LON);
        return Helper.intToDegree(intVal);
    }

    public double getEle(long id) {
        if (!is3D())
            return Double.NaN;

        int intVal = da.getInt(id * rowSizeInBytes + ELE);
        return Helper.uIntToEle(intVal);
    }

    public void clear() {
        dir.remove(da.getName());
    }
}
