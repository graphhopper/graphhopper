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
 * This class stores lat,lon for every pillar node parsed in OSMReader.
 * Elevation is not stored here — it is looked up at edge creation time.
 *
 * @author Peter Karich
 */
public class PillarInfo {
    private static final int LAT = 0 * 4, LON = 1 * 4;
    private static final int ROW_SIZE_IN_BYTES = 8;
    private final DataAccess da;
    private final Directory dir;

    public PillarInfo(Directory dir) {
        this.dir = dir;
        this.da = dir.create("tmp_pillar_info").create(100);
    }

    public void ensureNode(long nodeId) {
        long tmp = nodeId * ROW_SIZE_IN_BYTES;
        da.ensureCapacity(tmp + ROW_SIZE_IN_BYTES);
    }

    public void setNode(long nodeId, double lat, double lon) {
        ensureNode(nodeId);
        long tmp = nodeId * ROW_SIZE_IN_BYTES;
        da.setInt(tmp + LAT, Helper.degreeToInt(lat));
        da.setInt(tmp + LON, Helper.degreeToInt(lon));
    }

    public double getLat(long id) {
        int intVal = da.getInt(id * ROW_SIZE_IN_BYTES + LAT);
        return Helper.intToDegree(intVal);
    }

    public double getLon(long id) {
        int intVal = da.getInt(id * ROW_SIZE_IN_BYTES + LON);
        return Helper.intToDegree(intVal);
    }

    public void clear() {
        dir.remove(da.getName());
    }
}
