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

import org.apache.sis.core.LatLon;
import org.apache.sis.storage.QuadTreeData;

/**
 * Spatial Information System implementation
 *
 * @author Peter Karich
 */
public class SISTree implements SimplisticQuadTree {

    org.apache.sis.storage.QuadTree qt;

    public SISTree() {
    }

    public void init(int size) {
        // 32 entries per leaf
        qt = new org.apache.sis.storage.QuadTree(16, 100);
    }

    public void put(double lat, double lon) {
        qt.insert(new SisPoint(lat, lon));
    }

    public int countNodes(double lat, double lon, double radiusInKm) {
        return qt.queryByPointRadius(new LatLon(lat, lon), radiusInKm).size();
    }

    @Override
    public String toString() {
        return "SIS";
    }

    public int size() {
        return qt.size();
    }

    public long getEmptyEntries(boolean b) {
        return qt.getEmptyEntries(b);
    }

    private static class SisPoint implements QuadTreeData {

        private final LatLon latLon;

        public SisPoint(double lat, double lon) {
            latLon = new LatLon(lat, lon);
        }

        public final double getX() {
            return latLon.getShiftedLon();
        }

        public final double getY() {
            return latLon.getShiftedLat();
        }

        public final LatLon getLatLon() {
            return latLon;
        }

        public String getFileName() {
            return null;
        }
    }
}
