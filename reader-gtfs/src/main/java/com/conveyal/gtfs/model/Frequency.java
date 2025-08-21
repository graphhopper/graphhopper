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
import java.util.Locale;

import static com.conveyal.gtfs.model.Entity.Loader.convertToGtfsTime;

public class Frequency extends Entity implements Comparable<Frequency> {
    /**
     * Frequency entries have no ID in GTFS so we define one based on the fields in the frequency entry.
     *
     * It is possible to have two identical frequency entries in the GTFS, which under our understanding of the situation
     * would mean that two sets of vehicles were randomly running the same trip at the same headway, but uncorrelated
     * with each other, which is almost certain to be an error.
     */
     public String getId() {
        StringBuilder sb = new StringBuilder();
         sb.append(trip_id);
         sb.append('_');
         sb.append(convertToGtfsTime(start_time));
         sb.append("_to_");
         sb.append(convertToGtfsTime(end_time));
         sb.append("_every_");
         sb.append(String.format(Locale.getDefault(), "%dm%02ds", headway_secs / 60, headway_secs % 60));
         if (exact_times == 1) sb.append("_exact");
         return sb.toString();
     }

    private static final long serialVersionUID = -7182161664471704133L;
    public String trip_id;
    public int start_time;
    public int end_time;
    public int headway_secs;
    public int exact_times;

    /** must have a comparator since they go in a navigable set that is serialized */
    @Override
    public int compareTo(Frequency o) {
        return this.start_time - o.start_time;
    }

    public static class Loader extends Entity.Loader<Frequency> {

        public Loader(GTFSFeed feed) {
            super(feed, "frequencies");
        }

        @Override
        protected boolean isRequired() {
            return false;
        }

        @Override
        public void loadOneRow() throws IOException {
            Frequency f = new Frequency();
            Trip trip = getRefField("trip_id", true, feed.trips);
            f.sourceFileLine = row + 1; // offset line number by 1 to account for 0-based row index
            f.trip_id = trip.trip_id;
            f.start_time = getTimeField("start_time", true);
            f.end_time = getTimeField("end_time", true);
            f.headway_secs = getIntField("headway_secs", true, 1, 24 * 60 * 60);
            f.exact_times = getIntField("exact_times", false, 0, 1);
            f.feed = feed;
            feed.frequencies.add(Fun.t2(f.trip_id, f));
        }
    }

}
