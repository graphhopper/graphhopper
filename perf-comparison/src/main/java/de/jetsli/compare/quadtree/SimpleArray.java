/*
 *  Copyright 2012 Peter Karich info@jetsli.de
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package de.jetsli.compare.quadtree;

import de.jetsli.graph.util.CalcDistance;
import java.util.ArrayList;
import java.util.List;
import org.apache.sis.core.LatLon;

/**
 * Using a simple array as implementation
 * 
 * @author Peter Karich
 */
class SimpleArray implements SimplisticQuadTree {
    double[] lats = new double[0];
    double[] lons = new double[0];
    int currentIndex = 0;
    CalcDistance calc = new CalcDistance();

    public SimpleArray() {
    }

    public void init(int size) {
        if (lats.length < size) {
            lats = new double[size];
            lons = new double[size];
        }
    }

    public void put(double lat, double lon) {
        lats[currentIndex] = lat;
        lons[currentIndex] = lon;
        currentIndex++;
    }

    public int countNodes(double lat, double lon, double radius) {
        List<LatLon> res = new ArrayList<LatLon>();
        radius = calc.normalizeDist(radius);
        for (int i = 0; i < currentIndex; i++) {
            if (calc.calcNormalizedDist(lats[i], lons[i], lat, lon) <= radius)
                res.add(new LatLon(lats[i], lons[i]));
        }
        return res.size();
    }

    @Override
    public String toString() {
        return "Arr";
    }

    public int size() {
        return currentIndex;
    }

    public long getEmptyEntries(boolean b) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
