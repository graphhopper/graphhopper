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
import com.conveyal.gtfs.error.DuplicateKeyError;

import java.io.IOException;
import java.util.Map;

public class FareAttribute extends Entity {

    private static final long serialVersionUID = 2157859372072056891L;
    public String fare_id;
    public double price;
    public String currency_type;
    public int payment_method;
    public int transfers;
    public int transfer_duration;
    public String feed_id;

    public static class Loader extends Entity.Loader<FareAttribute> {
        private final Map<String, Fare> fares;

        public Loader(GTFSFeed feed, Map<String, Fare> fares) {
            super(feed, "fare_attributes");
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
            Fare fare = fares.computeIfAbsent(fareId, Fare::new);
            if (fare.fare_attribute != null) {
                feed.errors.add(new DuplicateKeyError(tableName, row, "fare_id"));
            } else {
                FareAttribute fa = new FareAttribute();
                fa.sourceFileLine = row + 1; // offset line number by 1 to account for 0-based row index
                fa.fare_id = fareId;
                fa.price = getDoubleField("price", true, 0, Integer.MAX_VALUE);
                fa.currency_type = getStringField("currency_type", true);
                fa.payment_method = getIntField("payment_method", true, 0, 1);
                fa.transfers = getIntField("transfers", false, 0, Integer.MAX_VALUE, Integer.MAX_VALUE);
                fa.transfer_duration = getIntField("transfer_duration", false, 0, 24*60*60, 24*60*60);
                fa.feed = feed;
                fa.feed_id = feed.feedId;
                fare.fare_attribute = fa;
            }

        }

    }

}
