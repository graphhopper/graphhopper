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
import com.conveyal.gtfs.error.DuplicateKeyError;
import com.google.common.base.Function;
import com.google.common.collect.Iterators;

import java.io.IOException;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.Map;

public class CalendarDate extends Entity implements Cloneable, Serializable {

    private static final long serialVersionUID = 6936614582249119431L;
    public String    service_id;
    public LocalDate date;
    public int       exception_type;

    public CalendarDate clone () {
        try {
            return (CalendarDate) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    public static class Loader extends Entity.Loader<CalendarDate> {

        private final Map<String, Service> services;

        /**
         * Create a loader. The map parameter should be an in-memory map that will be modified. We can't write directly
         * to MapDB because we modify services as we load calendar dates, and this creates concurrentmodificationexceptions.
         */
        public Loader(GTFSFeed feed, Map<String, Service> services) {
            super(feed, "calendar_dates");
            this.services = services;
        }

        @Override
        protected boolean isRequired() {
            return false;
        }

        @Override
        public void loadOneRow() throws IOException {
            /* Calendars and Fares are special: they are stored as joined tables rather than simple maps. */
            String service_id = getStringField("service_id", true);
            Service service = services.computeIfAbsent(service_id, Service::new);
            LocalDate date = getDateField("date", true);
            if (service.calendar_dates.containsKey(date)) {
                feed.errors.add(new DuplicateKeyError(tableName, row, "(service_id, date)"));
            } else {
                CalendarDate cd = new CalendarDate();
                cd.sourceFileLine = row + 1; // offset line number by 1 to account for 0-based row index
                cd.service_id = service_id;
                cd.date = date;
                cd.exception_type = getIntField("exception_type", true, 1, 2);
                cd.feed = feed;
                service.calendar_dates.put(date, cd);
            }
        }
    }

    public static class Writer extends Entity.Writer<CalendarDate> {
        public Writer (GTFSFeed feed) {
            super(feed, "calendar_dates");
        }

        @Override
        protected void writeHeaders() throws IOException {
            writer.writeRecord(new String[] {"service_id", "date", "exception_type"});
        }

        @Override
        protected void writeOneRow(CalendarDate d) throws IOException {
            writeStringField(d.service_id);
            writeDateField(d.date);
            writeIntField(d.exception_type);
            endRecord();
        }

        @Override
        protected Iterator<CalendarDate> iterator() {
            Iterator<Service> serviceIterator = feed.services.values().iterator();
            return Iterators.concat(Iterators.transform(serviceIterator, new Function<Service, Iterator<CalendarDate>> () {
                @Override
                public Iterator<CalendarDate> apply(Service service) {
                    return service.calendar_dates.values().iterator();
                }
            }));
        }
    }
}
