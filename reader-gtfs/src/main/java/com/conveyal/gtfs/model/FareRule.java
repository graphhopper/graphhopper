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
import com.conveyal.gtfs.error.ReferentialIntegrityError;

import java.io.IOException;
import java.util.Map;

public class FareRule extends Entity {

    private static final long serialVersionUID = 3209660216692732272L;
    public String fare_id;
    public String route_id;
    public String origin_id;
    public String destination_id;
    public String contains_id;

    public static class Loader extends Entity.Loader<FareRule> {

        private final Map<String, Fare> fares;

        public Loader(GTFSFeed feed, Map<String, Fare> fares) {
            super(feed, "fare_rules");
            this.fares = fares;
        }

        @Override
        protected boolean isRequired() {
            return false;
        }

        @Override
        public void loadOneRow() throws IOException {

            /* Calendars and Fares are special: they are stored as joined tables rather than simple maps. */
            String fareId = getStringField("fare_id", true);

            /* Referential integrity check for fare id */
            if (!fares.containsKey(fareId)) {
                this.feed.errors.add(new ReferentialIntegrityError(tableName, row, "fare_id", fareId));
            }

            Fare fare = fares.computeIfAbsent(fareId, Fare::new);
            FareRule fr = new FareRule();
            fr.sourceFileLine = row + 1; // offset line number by 1 to account for 0-based row index
            fr.fare_id = fare.fare_id;
            fr.route_id = getStringField("route_id", false);
            fr.origin_id = getStringField("origin_id", false);
            fr.destination_id = getStringField("destination_id", false);
            fr.contains_id = getStringField("contains_id", false);
            fr.feed = feed;
            fare.fare_rules.add(fr);

        }

    }

}
