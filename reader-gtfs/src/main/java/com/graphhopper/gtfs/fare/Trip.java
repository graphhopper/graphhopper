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

package com.graphhopper.gtfs.fare;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class Trip {

    public static class Segment {

        private final String route;
        private long startTime;
        private String originId;
        private String destinationId;
        private Set<String> zones;

        public Segment(String route, long startTime, String originId, String destinationId, Set<String> zones) {
            this.route = route;
            this.startTime = startTime;
            this.originId = originId;
            this.destinationId = destinationId;
            this.zones = zones;
        }

        String getRoute() {
            return route;
        }

        long getStartTime() {
            return startTime;
        }

        String getOriginId() {
            return originId;
        }

        String getDestinationId() {
            return destinationId;
        }

        Set<String> getZones() {
            return zones;
        }

    }

    public final List<Segment> segments = new ArrayList<>();


}
