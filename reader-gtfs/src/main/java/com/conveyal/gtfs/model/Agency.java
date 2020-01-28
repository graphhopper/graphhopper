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

import java.io.IOException;
import java.net.URL;

public class Agency extends Entity {

    private static final long serialVersionUID = -2825890165823575940L;
    public String agency_id;
    public String agency_name;
    public URL    agency_url;
    public String agency_timezone;
    public String agency_lang;
    public String agency_phone;
    public URL    agency_fare_url;
    public URL    agency_branding_url;
    public String feed_id;

    public static class Loader extends Entity.Loader<Agency> {

        public Loader(GTFSFeed feed) {
            super(feed, "agency");
        }

        @Override
        protected boolean isRequired() {
            return true;
        }

        @Override
        public void loadOneRow() throws IOException {
            Agency a = new Agency();
            a.sourceFileLine = row + 1; // offset line number by 1 to account for 0-based row index
            a.agency_id    = getStringField("agency_id", false); // can only be absent if there is a single agency -- requires a special validator.
            a.agency_name  = getStringField("agency_name", true);
            a.agency_url   = getUrlField("agency_url", true);
            a.agency_lang  = getStringField("agency_lang", false);
            a.agency_phone = getStringField("agency_phone", false);
            a.agency_timezone = getStringField("agency_timezone", true);
            a.agency_fare_url = getUrlField("agency_fare_url", false);
            a.agency_branding_url = getUrlField("agency_branding_url", false);
            a.feed = feed;
            a.feed_id = feed.feedId;

            // TODO clooge due to not being able to have null keys in mapdb
            if (a.agency_id == null) a.agency_id = "NONE";

            feed.agency.put(a.agency_id, a);
        }

    }

}
