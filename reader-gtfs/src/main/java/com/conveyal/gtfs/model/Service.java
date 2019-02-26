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

import com.google.common.collect.Maps;

import java.io.Serializable;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Map;

import static java.time.DayOfWeek.*;

/**
 * This table does not exist in GTFS. It is a join of calendars and calendar_dates on service_id.
 * There should only be one Calendar per service_id. There should only be one calendar_date per tuple of
 * (service_id, date), which means there can be many calendar_dates per service_id.
 */
public class Service implements Serializable {

    private static final long serialVersionUID = 7966238549509747091L;
    public String   service_id;
    public Calendar calendar;
    public Map<LocalDate, CalendarDate> calendar_dates = Maps.newHashMap();

    public Service(String service_id) {
        this.service_id = service_id;
    }

    /**
     * @param service_id the service_id to assign to the newly created copy.
     * @param daysToRemove the days of the week on which to deactivate service in the copy.
     * @return a copy of this Service with any service on the specified days of the week deactivated.
     */
    public Service removeDays(String service_id, EnumSet<DayOfWeek> daysToRemove) {
        Service service = new Service(service_id);
        // First, duplicate any Calendar in this Service, minus the specified days of the week.
        if (this.calendar != null) {
            Calendar calendar = new Calendar();
            //  TODO calendar.getDaysOfWeek/setDaysOfWeek which allow simplifying this section and activeOn below.
            calendar.monday    = daysToRemove.contains(MONDAY)    ? 0 : this.calendar.monday;
            calendar.tuesday   = daysToRemove.contains(TUESDAY)   ? 0 : this.calendar.tuesday;
            calendar.wednesday = daysToRemove.contains(WEDNESDAY) ? 0 : this.calendar.wednesday;
            calendar.thursday  = daysToRemove.contains(THURSDAY)  ? 0 : this.calendar.thursday;
            calendar.friday    = daysToRemove.contains(FRIDAY)    ? 0 : this.calendar.friday;
            calendar.saturday  = daysToRemove.contains(SATURDAY)  ? 0 : this.calendar.saturday;
            calendar.sunday    = daysToRemove.contains(SUNDAY)    ? 0 : this.calendar.sunday;
            // The new calendar should cover exactly the same time range as the existing one.
            calendar.start_date = this.calendar.start_date;
            calendar.end_date   = this.calendar.end_date;
            // Create the bidirectional reference between Calendar and Service.
            service.calendar = calendar;
        }
        // Copy over all exceptions whose dates fall on days of the week that are retained.
        this.calendar_dates.forEach((date, exception) -> {
            DayOfWeek dow = date.getDayOfWeek();
            if (!daysToRemove.contains(dow)) {
                CalendarDate newException = exception.clone();
                service.calendar_dates.put(date, newException);
            }
        });
        return service;
    }

    /**
     * @return whether this Service is ever active at all, either from calendar or calendar_dates.
     */
    public boolean hasAnyService() {

        // Look for any service defined in calendar (on days of the week).
        boolean hasAnyService = calendar != null && (
                calendar.monday == 1 ||
                calendar.tuesday == 1 ||
                calendar.wednesday == 1 ||
                calendar.thursday == 1 ||
                calendar.friday == 1 ||
                calendar.saturday == 1 ||
                calendar.sunday == 1 );

        // Also look for any exceptions of type 1 (added service).
        hasAnyService |= calendar_dates.values().stream().anyMatch(cd -> cd.exception_type == 1);

        return hasAnyService;
    }

    /**
     * Is this service active on the specified date?
     */
    public boolean activeOn (LocalDate date) {
        // first check for exceptions
        CalendarDate exception = calendar_dates.get(date);

        if (exception != null)
            return exception.exception_type == 1;

        else if (calendar == null)
            return false;

        else {
            int gtfsDate = date.getYear() * 10000 + date.getMonthValue() * 100 + date.getDayOfMonth();
            boolean withinValidityRange = calendar.end_date >= gtfsDate && calendar.start_date <= gtfsDate;

            if (!withinValidityRange) return false;

            switch (date.getDayOfWeek()) {
                case MONDAY:
                    return calendar.monday == 1;
                case TUESDAY:
                    return calendar.tuesday == 1;
                case WEDNESDAY:
                    return calendar.wednesday == 1;
                case THURSDAY:
                    return calendar.thursday == 1;
                case FRIDAY:
                    return calendar.friday == 1;
                case SATURDAY:
                    return calendar.saturday == 1;
                case SUNDAY:
                    return calendar.sunday == 1;
                default:
                    throw new IllegalArgumentException("unknown day of week constant!");
            }
        }
    }

    /**
     * Checks for overlapping days of week between two service calendars
     * @param s1
     * @param s2
     * @return true if both calendars simultaneously operate on at least one day of the week
     */
    public static boolean checkOverlap (Service s1, Service s2) {
        if (s1.calendar == null || s2.calendar == null) {
            return false;
        }
        // overlap exists if at least one day of week is shared by two calendars
        boolean overlappingDays = s1.calendar.monday == 1 && s2.calendar.monday == 1 ||
                s1.calendar.tuesday == 1 && s2.calendar.tuesday == 1 ||
                s1.calendar.wednesday == 1 && s2.calendar.wednesday == 1 ||
                s1.calendar.thursday == 1 && s2.calendar.thursday == 1 ||
                s1.calendar.friday == 1 && s2.calendar.friday == 1 ||
                s1.calendar.saturday == 1 && s2.calendar.saturday == 1 ||
                s1.calendar.sunday == 1 && s2.calendar.sunday == 1;
        return overlappingDays;
    }
}
