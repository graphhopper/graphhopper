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
package de.jetsli.graph.trees;

import de.jetsli.graph.storage.Graph;
import de.jetsli.graph.util.CoordTrig;
import de.jetsli.graph.util.shapes.Shape;
import java.util.Collection;

/**
 * A quad tree interface - think Map<latitude+longitude, V> with the possibility to get neighbouring
 * entries fast.
 *
 * @author Peter Karich, info@jetsli.de
 */
public interface QuadTree<V> {

    /**
     * The quadtree could be configured with implementation specific values. After this it needs to
     * be configured.
     *
     * @throws RuntimeException could be thrown
     */
    QuadTree init(long maxItemsHint);

    long size();

    boolean isEmpty();

    void add(double lat, double lon, V value);

    int remove(double lat, double lon);

    /**
     * @return The nodes matching the specified latitude and longitude. If value is null all values
     * will be returned
     */
    Collection<CoordTrig<V>> getNodesFromValue(double lat, double lon, V value);

    /**
     * @return points near the specified latitude/longitude
     */
    Collection<CoordTrig<V>> getNodes(double lat, double lon, double distanceInKm);

    Collection<CoordTrig<V>> getNodes(Shape boundingBox);

    void clear();

    /**
     * For debugging purposes
     */
    String toDetailString();

    long getMemoryUsageInBytes(int factor);

    /**
     * Good for memory estimation
     */
    long getEmptyEntries(boolean onlyBranches);

    class Util {

        public static void fill(QuadTree<Long> quadTree, Graph graph) {
            int locs = graph.getNodes();
            for (int i = 0; i < locs; i++) {
                double lat = graph.getLatitude(i);
                double lon = graph.getLongitude(i);
                quadTree.add(lat, lon, 1L);
            }
        }
    }
}
