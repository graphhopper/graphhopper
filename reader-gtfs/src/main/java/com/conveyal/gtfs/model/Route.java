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
import java.util.Iterator;

public class Route extends Entity { // implements Entity.Factory<Route>

    private static final long serialVersionUID = -819444896818029068L;

    public static final int TRAM = 0;
    public static final int SUBWAY = 1;
    public static final int RAIL = 2;
    public static final int BUS = 3;
    public static final int FERRY = 4;
    public static final int CABLE_CAR = 4;
    public static final int GONDOLA = 4;
    public static final int FUNICULAR = 5;

    public String route_id;
    public String agency_id;
    public String route_short_name;
    public String route_long_name;
    public String route_desc;
    public int    route_type;
    public URL    route_url;
    public String route_color;
    public String route_text_color;
    public URL route_branding_url;
    public String feed_id;

    public static class Loader extends Entity.Loader<Route> {

        public Loader(GTFSFeed feed) {
            super(feed, "routes");
        }

        @Override
        protected boolean isRequired() {
            return true;
        }

        @Override
        public void loadOneRow() throws IOException {
            Route r = new Route();
            r.sourceFileLine = row + 1; // offset line number by 1 to account for 0-based row index
            r.route_id = getStringField("route_id", true);
            Agency agency = getRefField("agency_id", false, feed.agency);

            // if there is only one agency, associate with it automatically
            if (agency == null && feed.agency.size() == 1) {
                agency = feed.agency.values().iterator().next();
            }

            // If we still haven't got an agency, it's because we have a bad reference, or because there is no agency
            if (agency != null) {
                r.agency_id = agency.agency_id;
            }

            r.route_short_name = getStringField("route_short_name", false); // one or the other required, needs a special validator
            r.route_long_name = getStringField("route_long_name", false);
            r.route_desc = getStringField("route_desc", false);
            r.route_type = getIntField("route_type", true, 0, 7);
            r.route_url = getUrlField("route_url", false);
            r.route_color = getStringField("route_color", false);
            r.route_text_color = getStringField("route_text_color", false);
            r.route_branding_url = getUrlField("route_branding_url", false);
            r.feed = feed;
            r.feed_id = feed.feedId;
            feed.routes.put(r.route_id, r);
        }

    }

    public static class Writer extends Entity.Writer<Route> {    	
        public Writer (GTFSFeed feed) {
            super(feed, "routes");
        }

        @Override
        public void writeHeaders() throws IOException {
            writeStringField("agency_id");
            writeStringField("route_id");
            writeStringField("route_short_name");
            writeStringField("route_long_name");
            writeStringField("route_desc");
            writeStringField("route_type");
            writeStringField("route_url");
            writeStringField("route_color");
            writeStringField("route_text_color");
            writeStringField("route_branding_url");
            endRecord();
        }

        @Override
        public void writeOneRow(Route r) throws IOException {
            writeStringField(r.agency_id);
            writeStringField(r.route_id);
            writeStringField(r.route_short_name);
            writeStringField(r.route_long_name);
            writeStringField(r.route_desc);
            writeIntField(r.route_type);
            writeUrlField(r.route_url);
            writeStringField(r.route_color);
            writeStringField(r.route_text_color);
            writeUrlField(r.route_branding_url);
            endRecord();
        }

        @Override
        public Iterator<Route> iterator() {
            return feed.routes.values().iterator();
        }   	
    }
}
