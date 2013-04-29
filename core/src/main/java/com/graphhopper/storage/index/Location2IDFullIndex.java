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
package com.graphhopper.storage.index;

import com.graphhopper.storage.Graph;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.DistancePlaneProjection;
import com.graphhopper.util.shapes.Circle;

/**
 * Very slow O(n) Location2IDIndex but no RAM required.
 *
 * @author Peter Karich
 */
public class Location2IDFullIndex implements Location2IDIndex {

    private DistanceCalc calc = new DistanceCalc();
    private Graph g;

    public Location2IDFullIndex(Graph g) {
        this.g = g;
    }

    @Override
    public boolean loadExisting() {
        return true;
    }

    @Override
    public Location2IDIndex precision(boolean approxDist) {
        if (approxDist)
            calc = new DistancePlaneProjection();
        else
            calc = new DistanceCalc();
        return this;
    }

    @Override
    public Location2IDIndex resolution(int resolution) {
        return this;
    }

    @Override
    public Location2IDIndex prepareIndex() {
        return this;
    }

    @Override public int findID(double lat, double lon) {
        int locs = g.nodes();
        int id = -1;
        Circle circle = null;
        for (int i = 0; i < locs; i++) {
            double tmpLat = g.getLatitude(i);
            double tmpLon = g.getLongitude(i);
            if (circle == null || circle.contains(tmpLat, tmpLon)) {
                id = i;
                double dist = calc.calcDist(tmpLat, tmpLon, lat, lon);
                if (dist <= 0)
                    break;

                circle = new Circle(lat, lon, dist, calc);
            }
        }
        return id;
    }

    @Override
    public Location2IDIndex create(long size) {
        return this;
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
    }

    @Override
    public long capacity() {
        return 0;
    }
}
