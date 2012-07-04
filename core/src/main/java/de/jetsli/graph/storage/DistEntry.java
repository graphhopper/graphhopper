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

import de.jetsli.graph.util.EmptyIterable;

/**
 * <b>DistEntry</b> is used as most simplistic return type for outgoing edges in Graph although
 * edges are stored as LinkedDistEntryWithFlags.<br/>
 *
 * <b>LinkedDistEntry</b> is used as simple linked list entry for the shortest path tree used in
 * Dijkstra algorithms and PathWrapper
 *
 * @author Peter Karich, info@jetsli.de
 */
public class DistEntry implements Comparable<DistEntry> {

    public final static EmptyIterable<DistEntry> EMPTY_ITER = new EmptyIterable<DistEntry>();
    public int node;
    public double distance; 
    
    public DistEntry(int loc, double distance) {
        this.node = loc;
        this.distance = distance;
    }

    @Override public int compareTo(DistEntry o) {
        return Double.compare(distance, o.distance);
    }

    @Override public String toString() {
        return "distance to " + node + " is " + distance;
    }
}
