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

package com.conveyal.gtfs.model;

import com.conveyal.gtfs.GTFSFeed;

import java.io.IOException;
import java.util.Iterator;

public class Trip extends Entity {

    private static final long serialVersionUID = -4869384750974542712L;
    public String route_id;
    public String service_id;
    public String trip_id;
    public String trip_headsign;
    public String trip_short_name;
    public int    direction_id;
    public String block_id;
    public String shape_id;
    public int    bikes_allowed;
    public int    wheelchair_accessible;
    public String feed_id;

    public static class Loader extends Entity.Loader<Trip> {

        public Loader(GTFSFeed feed) {
            super(feed, "trips");
        }

        @Override
        protected boolean isRequired() {
            return true;
        }

        @Override
        public void loadOneRow() throws IOException {
            Trip t = new Trip();

            t.sourceFileLine  = row + 1; // offset line number by 1 to account for 0-based row index
            t.route_id        = getStringField("route_id", true);
            t.service_id      = getStringField("service_id", true);
            t.trip_id         = getStringField("trip_id", true);
            t.trip_headsign   = getStringField("trip_headsign", false);
            t.trip_short_name = getStringField("trip_short_name", false);
            t.direction_id    = getIntField("direction_id", false, 0, 1);
            t.block_id        = getStringField("block_id", false); // make a blocks multimap
            t.shape_id        = getStringField("shape_id", false);
            t.bikes_allowed   = getIntField("bikes_allowed", false, 0, 2);
            t.wheelchair_accessible = getIntField("wheelchair_accessible", false, 0, 2);
            t.feed = feed;
            t.feed_id = feed.feedId;
            feed.trips.put(t.trip_id, t);

            /*
              Check referential integrity without storing references. Trip cannot directly reference Services or
              Routes because they would be serialized into the MapDB.
             */
            // TODO confirm existence of shape ID
            getRefField("service_id", true, feed.services);
            getRefField("route_id", true, feed.routes);
        }

    }

    public static class Writer extends Entity.Writer<Trip> {
        public Writer (GTFSFeed feed) {
            super(feed, "trips");
        }

        @Override
        protected void writeHeaders() throws IOException {
            // TODO: export shapes
            writer.writeRecord(new String[] {"route_id", "trip_id", "trip_headsign", "trip_short_name", "direction_id", "block_id",
                    "shape_id", "bikes_allowed", "wheelchair_accessible", "service_id"});
        }

        @Override
        protected void writeOneRow(Trip t) throws IOException {
            writeStringField(t.route_id);
            writeStringField(t.trip_id);
            writeStringField(t.trip_headsign);
            writeStringField(t.trip_short_name);
            writeIntField(t.direction_id);
            writeStringField(t.block_id);
            writeStringField(t.shape_id);
            writeIntField(t.bikes_allowed);
            writeIntField(t.wheelchair_accessible);
            writeStringField(t.service_id);
            endRecord();
        }

        @Override
        protected Iterator<Trip> iterator() {
            return feed.trips.values().iterator();
        }


    }

}
