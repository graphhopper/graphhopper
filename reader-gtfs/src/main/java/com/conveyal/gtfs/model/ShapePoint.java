/*
 * Copyright (c) 2015, Conveyal
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.conveyal.gtfs.model;

import com.conveyal.gtfs.GTFSFeed;
import org.mapdb.Fun.Tuple2;

import java.io.IOException;

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

}
