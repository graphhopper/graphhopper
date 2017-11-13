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

import com.graphhopper.routing.Path;
import com.graphhopper.util.PointList;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * If a route cannot be found due to disconnected graphs.
 *
 * @author Peter Karich
 */
public class ConnectionNotFoundException extends DetailedIllegalArgumentException {
    private static final String INTRO = "Connection between ";
    private static final String FMT = "point %d and point %d";
    private static final String JOIN = ", and ";
    private static final String OUTRO = " not found";

    public ConnectionNotFoundException(String var1, Map<String, Object> details) {
        super(var1, details);
    }

    public ConnectionNotFoundException(List<Path> paths, PointList waypoints) {
        this(unconnectedListString(paths), unconnectedInfo(paths, waypoints));
    }

    private static String unconnectedListString(List<Path> paths) {
        StringBuilder sb = new StringBuilder(INTRO);

        for (int n = 0; n < paths.size(); ++n) {
            if (!paths.get(n).isFound())
                sb.append(String.format(FMT, n, n + 1)).append(JOIN);
        }

        if (sb.length() == INTRO.length())
            throw new IllegalArgumentException("All provided paths are found");

        sb.setLength(sb.length() - JOIN.length()); // delete last occurrence of JOIN
        sb.append(OUTRO);

        return sb.toString();
    }

    private static Map<String, Object> unconnectedInfo(List<Path> paths, PointList waypoints) {
        Map<String, Object> info = new HashMap<>();

        for (int pathIndex = 0; pathIndex < paths.size(); ++pathIndex) {
            if (!paths.get(pathIndex).isFound()) {
                Map<String, Number> infoEntry = new HashMap<>();

                infoEntry.put(Keys.idFrom.name(), pathIndex);
                infoEntry.put(Keys.latFrom.name(), waypoints.getLat(pathIndex));
                infoEntry.put(Keys.lonFrom.name(), waypoints.getLon(pathIndex));
                infoEntry.put(Keys.idTo.name(), pathIndex + 1);
                infoEntry.put(Keys.latTo.name(), waypoints.getLat(pathIndex + 1));
                infoEntry.put(Keys.lonTo.name(), waypoints.getLon(pathIndex + 1));

                info.put("connection-not-found-" + info.size(), infoEntry);
            }
        }

        return info;
    }

    /**
     * The map keys used for each detail entry.
     *
     * An example for the containing map could look like this:
     * details = {
     *   "connection-not-found-0": {
     *     "idFrom": 1,
     *     ...
     *   },
     *   "connection-not-found-1": {
     *     "idFrom": 4
     *     ....
     *   }
     * }
     *
     * @author Oliver Schlüter
     */
    public enum Keys {
        idFrom, latFrom, lonFrom, idTo, latTo, lonTo
    }
}
