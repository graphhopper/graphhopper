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

/**
 * <b>DistEntry</b> is used in Path.<br/>
 *
 * <b>LinkedDistEntry</b> is used as simple linked list entry for the shortest path tree used in
 * Dijkstra algorithms and PathWrapper
 *
 * @author Peter Karich, info@jetsli.de
 */
public class Edge implements Comparable<Edge> {

    public int node;
    public double weight; 
    
    public Edge(int loc, double distance) {
        this.node = loc;
        this.weight = distance;
    }

    @Override public int compareTo(Edge o) {
        return Double.compare(weight, o.weight);
    }

    @Override public String toString() {
        return "distance to " + node + " is " + weight;
    }
}
