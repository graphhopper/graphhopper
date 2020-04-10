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

package com.graphhopper.reader.gtfs;

import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.Fare;

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;

// Used to mock GtfsStorage for GtfsReader, to reuse it for realtime updates.
// GtfsReader basically emits things to be added as a stream of events.
// TODO: Make that explicit
public interface GtfsStorageI {

    public abstract class PlatformDescriptor implements Serializable {
        String stop_id;

        public static PlatformDescriptor route(String stop_id, String route_id) {
            RoutePlatform routePlatform = new RoutePlatform();
            routePlatform.stop_id = stop_id;
            routePlatform.route_id = route_id;
            return routePlatform;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PlatformDescriptor that = (PlatformDescriptor) o;
            return Objects.equals(stop_id, that.stop_id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(stop_id);
        }

        public static RouteTypePlatform routeType(String stop_id, int route_type) {
            RouteTypePlatform routeTypePlatform = new RouteTypePlatform();
            routeTypePlatform.stop_id = stop_id;
            routeTypePlatform.route_type = route_type;
            return routeTypePlatform;
        }

    }

    class RoutePlatform extends PlatformDescriptor {
        String route_id;

        @Override
        public String toString() {
            return "RoutePlatform{" +
                    "stop_id='" + stop_id + '\'' +
                    ", route_id='" + route_id + '\'' +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            RoutePlatform that = (RoutePlatform) o;
            return route_id.equals(that.route_id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), route_id);
        }
    }

    class RouteTypePlatform extends PlatformDescriptor {
        int route_type;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            RouteTypePlatform that = (RouteTypePlatform) o;
            return route_type == that.route_type;
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), route_type);
        }

        @Override
        public String toString() {
            return "RouteTypePlatform{" +
                    "stop_id='" + stop_id + '\'' +
                    ", route_type=" + route_type +
                    '}';
        }
    }

    Map<String, Fare> getFares();

    Map<GtfsStorage.Validity, Integer> getOperatingDayPatterns();

    Map<GtfsStorage.FeedIdWithTimezone, Integer> getWritableTimeZones();

    Map<Integer, GtfsStorage.FeedIdWithTimezone> getTimeZones();

    Map<Integer, byte[]> getTripDescriptors();

    Map<Integer, Integer> getStopSequences();

    Map<String, int[]> getBoardEdgesForTrip();

    Map<String, int[]> getAlightEdgesForTrip();

    Map<String, GTFSFeed> getGtfsFeeds();

    Map<String, Transfers> getTransfers();

    Map<String, Integer> getStationNodes();

    Map<Integer, PlatformDescriptor> getPlatformDescriptorByEdge();
}
