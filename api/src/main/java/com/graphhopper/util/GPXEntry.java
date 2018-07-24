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
package com.graphhopper.util;

import com.graphhopper.util.shapes.GHPoint;
import com.graphhopper.util.shapes.GHPoint3D;

/**
 * @author Peter Karich
 */
public class GPXEntry extends GHPoint3D {
    private long time;

    public GPXEntry(GHPoint p, long millis) {
        this(p.lat, p.lon, millis);
    }

    public GPXEntry(double lat, double lon, long millis) {
        super(lat, lon, Double.NaN);
        this.time = millis;
    }

    public GPXEntry(double lat, double lon, double ele, long millis) {
        super(lat, lon, ele);
        this.time = millis;
    }

    boolean is3D() {
        return !Double.isNaN(ele);
    }

    /**
     * The time relative to the start time in milli seconds.
     */
    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    @Override
    public int hashCode() {
        return 59 * super.hashCode() + (int) (time ^ (time >>> 32));
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;

        final GPXEntry other = (GPXEntry) obj;
        return time == other.time && super.equals(obj);
    }

    @Override
    public String toString() {
        return super.toString() + ", " + time;
    }
}
