/*
 *  Licensed to Peter Karich under one or more contributor license 
 *  agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  Peter Karich licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except 
 *  in compliance with the License. You may obtain a copy of the 
 *  License at
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
import com.graphhopper.util.PointList;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Peter Karich
 */
public class TestAlgoCollector {

    public List<String> list = new ArrayList<String>();

    public TestAlgoCollector assertDistance(RoutingAlgorithm algo,
            int from, int to, double distance, int pointCount) {
        Path path = algo.calcPath(from, to);
        if (!path.found()) {
            list.add(algo + " returns no path. from:" + from + ", to:" + to);
            return this;
        }

        PointList pointList = path.calcPoints();
        // Yes, there are indeed real world instances where A-B-C is identical to A-C (in meter precision).
        // And for from:501620, to:155552 the node difference of astar to bi-dijkstra gets even bigger (7!).
        if (Math.abs(path.distance() - distance) > 10)
            list.add(algo + " returns path not matching the expected distance of " + distance
                    + "\t Returned was " + path.distance() + "\t (expected points " + pointCount
                    + ", was " + pointList.size() + ") from:" + from + ", to:" + to);
        if (Math.abs(pointList.size() - pointCount) > 7)
            list.add(algo + " returns path not matching the expected points of " + pointCount
                    + "\t Returned was " + pointList.size() + "\t (expected distance " + distance
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
