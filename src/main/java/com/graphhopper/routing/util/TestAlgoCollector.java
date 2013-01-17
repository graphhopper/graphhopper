/*
 *  Copyright 2012 Peter Karich 
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
package com.graphhopper.routing.util;

import com.graphhopper.routing.Path;
import com.graphhopper.routing.RoutingAlgorithm;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Peter Karich
 */
public class TestAlgoCollector {

    public List<String> list = new ArrayList<String>();

    public TestAlgoCollector assertNotFound(RoutingAlgorithm algo, int from, int to) {
        Path p = algo.clear().calcPath(from, to);
        if (p.found()) {
            synchronized (this) {
                list.add(algo + " returns value where null is expected. " + "from:" + from + ", to:" + to);
            }
        }
        return this;
    }

    public TestAlgoCollector assertDistance(RoutingAlgorithm algo,
            int from, int to, double distance, int points) {
        Path path = algo.clear().calcPath(from, to);
        if (!path.found()) {
            list.add(algo + " returns no path. from:" + from + ", to:" + to);
            return this;
        } else if (Math.abs(path.distance() - distance) > 10)
            list.add(algo + " returns path not matching the expected distance of " + distance
                    + "\t Returned was " + path.distance() + "\t (expected points " + points
                    + ", was " + path.calcPoints().size() + ") from:" + from + ", to:" + to);
        // Yes, there are indeed real world instances where A-B-C is identical to A-C (in meter precision).
        // And for from:501620, to:155552 the node difference of astar to bi-dijkstra gets even bigger (7!).
        if (Math.abs(path.calcPoints().size() - points) > 7)
            list.add(algo + " returns path not matching the expected points of " + points
                    + "\t Returned was " + path.calcPoints().size() + "\t (expected distance " + distance
                    + ", was " + path.distance() + ") from:" + from + ", to:" + to);
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
