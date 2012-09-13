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
package de.jetsli.graph.storage;

import de.jetsli.graph.util.CalcDistance;
import de.jetsli.graph.util.shapes.Circle;

/**
 * @author Peter Karich
 */
public class Location2IDFullIndex implements Location2IDIndex {

    private CalcDistance calc = new CalcDistance();
    private Graph g;

    public Location2IDFullIndex(Graph g) {
        this.g = g;
    }

    @Override
    public Location2IDIndex prepareIndex(int capacity) {
        return this;
    }

//    @Override
//    public int findID(double lat, double lon) {
//        Collection<CoordTrig<Long>> coll = qt.getNodes(lat, lon, 0.001);
//        if (coll.isEmpty())
//            throw new IllegalStateException("cannot find node for " + lat + "," + lon);
//
//        return ((Number) coll.iterator().next().getValue()).intValue();
//    }
    @Override public int findID(double lat, double lon) {
        int locs = g.getNodes();
        int id = -1;
        Circle circle = null;
        for (int i = 0; i < locs; i++) {
            double tmpLat = g.getLatitude(i);
            double tmpLon = g.getLongitude(i);
            if (circle == null || circle.contains(tmpLat, tmpLon)) {
                id = i;
                double dist = calc.calcDistKm(tmpLat, tmpLon, lat, lon);
                if (dist <= 0)
                    break;

                circle = new Circle(lat, lon, dist, calc);
            }
        }
        return id;
    }
}
