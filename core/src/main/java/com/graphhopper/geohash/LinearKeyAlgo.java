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
package com.graphhopper.geohash;

import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.GHPoint;

/**
 * This class maps lat,lon to a (tile)number unlike SpatialKeyAlgo.
 * <p>
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
    private static final double C = 1 - 1e-15;
    private final int latUnits, lonUnits;
    private BBox bounds;
    private double latDelta, lonDelta;

    public LinearKeyAlgo(int latUnits, int lonUnits) {
        this.latUnits = latUnits;
        this.lonUnits = lonUnits;
        setWorldBounds();
    }

    @Override
    public LinearKeyAlgo setBounds(double minLonInit, double maxLonInit, double minLatInit, double maxLatInit) {
        bounds = new BBox(minLonInit, maxLonInit, minLatInit, maxLatInit);
        latDelta = (bounds.maxLat - bounds.minLat) / latUnits;
        lonDelta = (bounds.maxLon - bounds.minLon) / lonUnits;
        return this;
    }

    public LinearKeyAlgo setBounds(BBox bounds) {
        setBounds(bounds.minLon, bounds.maxLon, bounds.minLat, bounds.maxLat);
        return this;
    }

    protected void setWorldBounds() {
        setBounds(-180, 180, -90, 90);
    }

    @Override
    public long encode(GHPoint coord) {
        return encode(coord.lat, coord.lon);
    }

    /**
     * Take latitude and longitude as input.
     * <p>
     *
     * @return the linear key
     */
    @Override
    public final long encode(double lat, double lon) {
        lat = Math.min(Math.max(lat, bounds.minLat), bounds.maxLat);
        lon = Math.min(Math.max(lon, bounds.minLon), bounds.maxLon);
        // introduce a minor correction to round to lower grid entry!
        long latIndex = (long) ((lat - bounds.minLat) / latDelta * C);
        long lonIndex = (long) ((lon - bounds.minLon) / lonDelta * C);
        return latIndex * lonUnits + lonIndex;
    }

    /**
     * This method returns latitude and longitude via latLon - calculated from specified linearKey
     * <p>
     *
     * @param linearKey is the input
     */
    @Override
    public final void decode(long linearKey, GHPoint latLon) {
        double lat = linearKey / lonUnits * latDelta + bounds.minLat;
        double lon = linearKey % lonUnits * lonDelta + bounds.minLon;
        latLon.lat = lat + latDelta / 2;
        latLon.lon = lon + lonDelta / 2;
    }

    public double getLatDelta() {
        return latDelta;
    }

    public double getLonDelta() {
        return lonDelta;
    }

}
