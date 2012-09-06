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
package de.jetsli.graph.routing.util;

import de.jetsli.graph.routing.Path;
import de.jetsli.graph.routing.RoutingAlgorithm;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Peter Karich
 */
public class TestAlgoCollector {

    public List<String> list = new ArrayList<String>();

    public TestAlgoCollector assertNull(RoutingAlgorithm algo, int from, int to) {
        Path p = algo.clear().calcPath(from, to);
        if (p != null) {
            synchronized (this) {
                list.add(algo + " returns value where null is expected. " + "from:" + from + ", to:" + to);
            }
        }
        return this;
    }

    public TestAlgoCollector assertDistance(RoutingAlgorithm algo, int from, int to, double distance, int locations) {
        Path p = algo.clear().calcPath(from, to);
        if (p == null) {
            list.add(algo + " returns no path for " + "from:" + from + ", to:" + to);
            return this;
        } else if (Math.abs(p.weight() - distance) > 1e-2)
            list.add(algo + " returns path not matching the expected " + "distance of " + distance + "\t Returned was " + p.weight() + "\t (expected locations " + locations + ", was " + p.locations() + ") " + "from:" + from + ", to:" + to);
        // Yes, there are indeed real world instances where A-B-C is identical to A-C (in meter precision).
        // And for from:501620, to:155552 the location difference of astar to bi-dijkstra gets even bigger (7!).
        if (Math.abs(p.locations() - locations) > 7)
            list.add(algo + " returns path not matching the expected " + "locations of " + locations + "\t Returned was " + p.locations() + "\t (expected distance " + distance + ", was " + p.weight() + ") " + "from:" + from + ", to:" + to);
        return this;
    }

    @Override
    public String toString() {
        String str = "";
        str += "FOUND " + list.size() + " ERRORS.\n";
        for (String s : list) {
            str += s + ".\n";
        }
        return str;
    }
}
