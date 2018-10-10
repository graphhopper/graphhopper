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
import org.mapdb.Fun;

import java.io.IOException;
import java.io.Serializable;
import java.util.Iterator;

/**
 * Represents a GTFS StopTime. Note that once created and saved in a feed, stop times are by convention immutable
 * because they are in a MapDB.
 */
public class StopTime extends Entity implements Cloneable, Serializable {

    private static final long serialVersionUID = -8883780047901081832L;
    /* StopTime cannot directly reference Trips or Stops because they would be serialized into the MapDB. */
    public String trip_id;
    public int    arrival_time = INT_MISSING;
    public int    departure_time = INT_MISSING;
    public String stop_id;
    public int    stop_sequence;
    public String stop_headsign;
    public int    pickup_type;
    public int    drop_off_type;
    public double shape_dist_traveled;
    public int    timepoint = INT_MISSING;

    public static class Loader extends Entity.Loader<StopTime> {

        public Loader(GTFSFeed feed) {
            super(feed, "stop_times");
        }

        @Override
        protected boolean isRequired() {
            return true;
        }

        @Override
        public void loadOneRow() throws IOException {
            StopTime st = new StopTime();
            st.sourceFileLine = row + 1; // offset line number by 1 to account for 0-based row index
            st.trip_id        = getStringField("trip_id", true);
            // TODO: arrival_time and departure time are not required, but if one is present the other should be
            // also, if this is the first or last stop, they are both required
            st.arrival_time   = getTimeField("arrival_time", false);
            st.departure_time = getTimeField("departure_time", false);
            st.stop_id        = getStringField("stop_id", true);
            st.stop_sequence  = getIntField("stop_sequence", true, 0, Integer.MAX_VALUE);
            st.stop_headsign  = getStringField("stop_headsign", false);
            st.pickup_type    = getIntField("pickup_type", false, 0, 3); // TODO add ranges as parameters
            st.drop_off_type  = getIntField("drop_off_type", false, 0, 3);
            st.shape_dist_traveled = getDoubleField("shape_dist_traveled", false, 0D, Double.MAX_VALUE); // FIXME using both 0 and NaN for "missing", define DOUBLE_MISSING
            st.timepoint      = getIntField("timepoint", false, 0, 1, INT_MISSING);
            st.feed           = null; // this could circular-serialize the whole feed
            feed.stop_times.put(new Fun.Tuple2(st.trip_id, st.stop_sequence), st);

            /*
              Check referential integrity without storing references. StopTime cannot directly reference Trips or
              Stops because they would be serialized into the MapDB.
             */
            getRefField("trip_id", true, feed.trips);
            getRefField("stop_id", true, feed.stops);
        }

    }

    public static class Writer extends Entity.Writer<StopTime> {
        public Writer (GTFSFeed feed) {
            super(feed, "stop_times");
        }

        @Override
        protected void writeHeaders() throws IOException {
            writer.writeRecord(new String[] {"trip_id", "arrival_time", "departure_time", "stop_id", "stop_sequence", "stop_headsign",
                    "pickup_type", "drop_off_type", "shape_dist_traveled", "timepoint"});
        }

        @Override
        protected void writeOneRow(StopTime st) throws IOException {
            writeStringField(st.trip_id);
            writeTimeField(st.arrival_time);
            writeTimeField(st.departure_time);
            writeStringField(st.stop_id);
            writeIntField(st.stop_sequence);
            writeStringField(st.stop_headsign);
            writeIntField(st.pickup_type);
            writeIntField(st.drop_off_type);
            writeDoubleField(st.shape_dist_traveled);
            writeIntField(st.timepoint);
            endRecord();
        }

        @Override
        protected Iterator<StopTime> iterator() {
            return feed.stop_times.values().iterator();
        }


    }

    @Override
    public StopTime clone () {
        try {
            return (StopTime) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }
}
