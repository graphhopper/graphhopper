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

import de.jetsli.graph.dijkstra.DijkstraPath;

/**
 * This class creates a DijkstraPath from two DistEntry's resulting from a BidirectionalDijkstra
 * 
 * @author Peter Karich, info@jetsli.de
 */
public class GeoPathWrapper {

    public LinkedDistEntry entryFrom;
    public LinkedDistEntry entryTo;
    public float distance;

    public GeoPathWrapper() {
    }

    /**
     * Extracts path from two shortest-path-tree
     */
    public DijkstraPath extract() {
        if (entryFrom == null || entryTo == null)
            return null;
        
        if (entryFrom.node != entryTo.node)
            throw new IllegalStateException("Locations of 'to' and 'from' DistEntries has to be the same." + toString());        

        DijkstraPath path = new DijkstraPath();
        LinkedDistEntry curr = entryFrom;
        while (curr != null) {
            path.add(curr);
            curr = curr.prevEntry;
        }
        path.reverseOrder();

        float fromDistance = path.distance();
        float toDistance = entryTo.distance;
        curr = entryTo.prevEntry;
        while (curr != null) {
            path.add(curr);
            curr = curr.prevEntry;
        }
        // we didn't correct the distances of the other to-DistEntry for performance reasons
        path.setDistance(fromDistance + toDistance);
        return path;
    }

    @Override public String toString() {
        return "distance:" + distance + ", from:" + entryFrom + ", to:" + entryTo;
    }
}
