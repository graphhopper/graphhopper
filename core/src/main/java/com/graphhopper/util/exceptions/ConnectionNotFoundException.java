/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.util.exceptions;

import com.graphhopper.PathWrapper;
import com.graphhopper.routing.Path;
import com.graphhopper.util.PointList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * If a route cannot be found due to disconnected graphs.
 *
 * @author Peter Karich
 */
public class ConnectionNotFoundException extends DetailedIllegalArgumentException {
    public ConnectionNotFoundException(String var1, Map<String, Object> details) {
        super(var1, details);
    }

    public ConnectionNotFoundException(List<Path> paths, PathWrapper altRsp) {
        super("Connection between locations not found: " +
                unconnectedListString(paths), unconnectedInfo(altRsp, paths));
    }

    private static String unconnectedListString(List<Path> paths) {
        StringBuilder sb = new StringBuilder().append("[");

        for (int n = 0; n < paths.size(); ++n) {
            if (!paths.get(n).isFound())
                sb.append(String.format("(%d,%d), ", n, n+1));
        }

        if (sb.length() < 2)
            throw new IllegalArgumentException("list doesn't have unavailable paths");

        sb.setLength(sb.length() - 2); // delete last occurrence of  ", "
        sb.append("]");

        return sb.toString();
    }

    private static Map<String, Object> unconnectedInfo(PathWrapper altRsp, List<Path> paths) {
        PointList waypoints = altRsp.getWaypoints();

        ArrayList<Object> idxFrom = new ArrayList<>();
        ArrayList<Object> latFrom = new ArrayList<>();
        ArrayList<Object> lonFrom = new ArrayList<>();
        ArrayList<Object> idxTo = new ArrayList<>();
        ArrayList<Object> latTo = new ArrayList<>();
        ArrayList<Object> lonTo = new ArrayList<>();

        for (int pathIndex = 0; pathIndex < paths.size(); ++pathIndex) {
            if (!paths.get(pathIndex).isFound()) {
                idxFrom.add(pathIndex);
                latFrom.add(waypoints.getLat(pathIndex));
                lonFrom.add(waypoints.getLon(pathIndex));
                idxTo.add(pathIndex + 1);
                latTo.add(waypoints.getLat(pathIndex + 1));
                lonTo.add(waypoints.getLon(pathIndex + 1));
            }
        }

        Map<String, Object> info = new HashMap<>();
        info.put("idxFrom", idxFrom);
        info.put("latFrom", latFrom);
        info.put("lonFrom", lonFrom);
        info.put("idxTo", idxTo);
        info.put("latTo", latTo);
        info.put("lonTo", lonTo);

        return info;
    }
}
