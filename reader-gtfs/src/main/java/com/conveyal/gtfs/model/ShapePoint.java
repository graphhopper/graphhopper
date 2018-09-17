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
import org.mapdb.Fun.Tuple2;

import java.io.IOException;
import java.util.Iterator;

public class ShapePoint extends Entity {

    private static final long serialVersionUID = 6751814959971086070L;
    public final String shape_id;
    public final double shape_pt_lat;
    public final double shape_pt_lon;
    public final int    shape_pt_sequence;
    public final double shape_dist_traveled;

    // Similar to stoptime, we have to have a constructor, because fields are final so as to be immutable for storage in MapDB.
    public ShapePoint(String shape_id, double shape_pt_lat, double shape_pt_lon, int shape_pt_sequence, double shape_dist_traveled) {
        this.shape_id = shape_id;
        this.shape_pt_lat = shape_pt_lat;
        this.shape_pt_lon = shape_pt_lon;
        this.shape_pt_sequence = shape_pt_sequence;
        this.shape_dist_traveled = shape_dist_traveled;
    }

    public static class Loader extends Entity.Loader<ShapePoint> {

        public Loader(GTFSFeed feed) {
            super(feed, "shapes");
        }

        @Override
        protected boolean isRequired() {
            return false;
        }

        @Override
        public void loadOneRow() throws IOException {
            String shape_id = getStringField("shape_id", true);
            double shape_pt_lat = getDoubleField("shape_pt_lat", true, -90D, 90D);
            double shape_pt_lon = getDoubleField("shape_pt_lon", true, -180D, 180D);
            int shape_pt_sequence = getIntField("shape_pt_sequence", true, 0, Integer.MAX_VALUE);
            double shape_dist_traveled = getDoubleField("shape_dist_traveled", false, 0D, Double.MAX_VALUE);

            ShapePoint s = new ShapePoint(shape_id, shape_pt_lat, shape_pt_lon, shape_pt_sequence, shape_dist_traveled);
            s.sourceFileLine = row + 1; // offset line number by 1 to account for 0-based row index
            s.feed = null; // since we're putting this into MapDB, we don't want circular serialization
            feed.shape_points.put(new Tuple2<String, Integer>(s.shape_id, s.shape_pt_sequence), s);
        }
    }

    public static class Writer extends Entity.Writer<ShapePoint> {
        public Writer (GTFSFeed feed) {
            super(feed, "shapes");
        }

        @Override
        protected void writeHeaders() throws IOException {
            writer.writeRecord(new String[] {"shape_id", "shape_pt_lat", "shape_pt_lon", "shape_pt_sequence", "shape_dist_traveled"});
        }

        @Override
        protected void writeOneRow(ShapePoint s) throws IOException {
            writeStringField(s.shape_id);
            writeDoubleField(s.shape_pt_lat);
            writeDoubleField(s.shape_pt_lon);
            writeIntField(s.shape_pt_sequence);
            writeDoubleField(s.shape_dist_traveled);
            endRecord();
        }

        @Override
        protected Iterator<ShapePoint> iterator() {
            return feed.shape_points.values().iterator();
        }
    }
}
