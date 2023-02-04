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
package com.graphhopper.core.util.shapes;

import com.graphhopper.core.util.NumHelper;

/**
 * @author Peter Karich
 */
public class GHPoint3D extends GHPoint {
    public double ele;

    public GHPoint3D(double lat, double lon, double elevation) {
        super(lat, lon);
        this.ele = elevation;
    }

    public double getEle() {
        return ele;
    }

    @Override
    public int hashCode() {
        int hash = 59 * super.hashCode()
                + (int) (Double.doubleToLongBits(this.ele) ^ (Double.doubleToLongBits(this.ele) >>> 32));
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;

        @SuppressWarnings("unchecked")
        final GHPoint3D other = (GHPoint3D) obj;
        if (Double.isNaN(ele))
            // very special case necessary in QueryGraph, asserted via test
            return NumHelper.equalsEps(lat, other.lat) && NumHelper.equalsEps(lon, other.lon);
        else
            return NumHelper.equalsEps(lat, other.lat) && NumHelper.equalsEps(lon, other.lon)
                    && NumHelper.equalsEps(ele, other.ele);
    }

    @Override
    public String toString() {
        return super.toString() + "," + ele;
    }

    @Override
    public Double[] toGeoJson() {
        return new Double[]{lon, lat, ele};
    }
}
