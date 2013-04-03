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
package com.graphhopper.geohash;

import com.graphhopper.util.shapes.CoordTrig;

/**
 * This class maps lat,lon to a (tile)number unlike SpatialKeyAlgo.
 *
 * @author Peter Karich
 */
// A 4*3 precision linear key will look like
//  
//     |----|----|----|----|
//     |   0|   1|   2|   3|
//     |----|----|----|----|
// lat |   4|   5|   6|   7|
//     |----|----|----|----|
//     |   8|   9|  10|  11|
//     |----|----|----|----|
//
//              lon
public class LinearKeyAlgo implements KeyAlgo {

    // private int factorForPrecision;
    // normally -180 degree
    private double minLon;
    // normally +180 degree (parallel to equator)
    private double maxLon;
    // normally -90 degree
    private double minLat;
    // normally +90 degree (south to nord)
    private double maxLat;
    private double latDelta, lonDelta;
    private final int latUnits, lonUnits;
    private static final double C = 1 - 1e-15;

    public LinearKeyAlgo(int latUnits, int lonUnits) {
        this.latUnits = latUnits;
        this.lonUnits = lonUnits;
        setWorldBounds();
    }

    @Override
    public LinearKeyAlgo bounds(double minLonInit, double maxLonInit, double minLatInit, double maxLatInit) {
        minLon = minLonInit;
        maxLon = maxLonInit;
        minLat = minLatInit;
        maxLat = maxLatInit;
        latDelta = (maxLat - minLat) / latUnits;
        lonDelta = (maxLon - minLon) / lonUnits;
        return this;
    }

    protected void setWorldBounds() {
        bounds(-180, 180, -90, 90);
    }

    @Override
    public long encode(CoordTrig coord) {
        return encode(coord.lat, coord.lon);
    }

    /**
     * Take latitude and longitude as input.
     *
     * @return the linear key
     */
    @Override
    public final long encode(double lat, double lon) {
        lat = Math.min(Math.max(lat, minLat), maxLat);
        lon = Math.min(Math.max(lon, minLon), maxLon);
        // introduce a minor correction to round to lower grid entry!
        int latIndex = (int) ((lat - minLat) / latDelta * C);
        int lonIndex = (int) ((lon - minLon) / lonDelta * C);
        return latIndex * lonUnits + lonIndex;
    }

    /**
     * This method returns latitude and longitude via latLon - calculated from
     * specified linearKey
     *
     * @param linearKey is the input
     */
    @Override
    public final void decode(long linearKey, CoordTrig latLon) {
        double lat = linearKey / lonUnits * latDelta + minLat;
        double lon = linearKey % lonUnits * lonDelta + minLon;
        latLon.lat = lat + latDelta / 2;
        latLon.lon = lon + lonDelta / 2;
    }
}
