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

package de.jetsli.quadtreecomparison;

import de.jetsli.graph.trees.QuadTree;
import de.jetsli.graph.trees.QuadTreeSimple;

/**
 * @author Peter Karich
 */
// GraphHopper
class GHTree implements SimplisticQuadTree {
    Integer integ = new Integer(0);
    QuadTree qt = new QuadTreeSimple(16, 56);

    public GHTree() {
    }

    public void init(int size) {
    }

    public void put(double lat, double lon) {
        Object ret = qt.put(lat, lon, integ);
        if (ret != null)
            throw new IllegalStateException("point already exists:" + lat + ", " + lon + " size:" + qt.size());
    }

    public int countNodes(double lat, double lon, double radiusInKm) {
        return qt.getNeighbours(lat, lon, radiusInKm).size();
    }

    @Override
    public String toString() {
        return "GHO";
    }

    public int size() {
        return qt.size();
    }

}
