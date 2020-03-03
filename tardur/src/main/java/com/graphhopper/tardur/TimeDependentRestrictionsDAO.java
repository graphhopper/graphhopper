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

package com.graphhopper.tardur;

import ch.poole.conditionalrestrictionparser.Condition;
import ch.poole.conditionalrestrictionparser.ConditionalRestrictionParser;
import ch.poole.conditionalrestrictionparser.ParseException;
import ch.poole.conditionalrestrictionparser.Restriction;
import ch.poole.openinghoursparser.*;
import com.conveyal.osmlib.OSMEntity;
import com.conveyal.osmlib.Way;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.profiles.IntEncodedValue;
import com.graphhopper.search.StringIndex;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.timezone.core.TimeZones;
import com.graphhopper.util.EdgeIteratorState;

import java.io.ByteArrayInputStream;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TimeDependentRestrictionsDAO {

    private final BooleanEncodedValue property;
    private final IntEncodedValue tagPointer;
    private TimeZones timeZones;
    private GraphHopperStorage ghStorage;
    private final StringIndex tagStore;

    public TimeDependentRestrictionsDAO(GraphHopperStorage ghStorage, TimeZones timeZones) {
        this.ghStorage = ghStorage;
        this.timeZones = timeZones;
        property = ghStorage.getEncodingManager().getBooleanEncodedValue("conditional");
        tagPointer = ghStorage.getEncodingManager().getIntEncodedValue("edgetagpointer");
        tagStore = ghStorage.getTagStore();
    }

    public static Map<String, String> getTags(OSMEntity relation) {
        Map<String, String> tags = new HashMap<>();
        for (OSMEntity.Tag tag : relation.tags) {
            tags.put(tag.key, tag.value);
        }
        return tags;
    }

    public static class ConditionalTagData {
        public OSMEntity.Tag getTag() {
            return tag;
        }

        public OSMEntity.Tag tag;

        public List<TimeDependentRestrictionData> getRestrictionData() {
            return restrictionData;
        }

        public List<TimeDependentRestrictionData> restrictionData;
    }

    public static class TimeDependentRestrictionData {
        public Restriction getRestriction() {
            return restriction;
        }

        public Restriction restriction;

        public List<Rule> getRules() {
            return rules;
        }

        public List<Rule> rules;
    }


    /**
     *
     * We use ReaderWay instead of Way as the domain type, because we also need these functions in the
     * OSM import, where we don't have the separate OSM database.
     */
    public ReaderWay readerWay(long osmid, Way way) {
        ReaderWay readerWay = new ReaderWay(osmid);
        for (OSMEntity.Tag tag : way.tags) {
            readerWay.setTag(tag.key, tag.value);
        }
        return readerWay;
    }

    public static List<ConditionalTagData> getTimeDependentAccessConditions(ReaderWay way) {
        List<ConditionalTagData> restrictionData = getConditionalTagDataWithTimeDependentConditions(sanitize(way.getTags()));
        List<ConditionalTagData> accessRestrictionData = restrictionData.stream()
                .filter(c -> c.tag.key.contains("access") || c.tag.key.contains("vehicle"))
                .filter(c -> !c.tag.key.contains("lanes"))
                .collect(Collectors.toList());
        return accessRestrictionData.stream()
                .filter(c -> !c.restrictionData.isEmpty())
                .collect(Collectors.toList());
    }

    public static Map<String, String> sanitize(Map<String, Object> tagsAndPerhapsOtherStuff) {
        HashMap<String, String> tags = new HashMap<>();
        tagsAndPerhapsOtherStuff.forEach((key, value) -> {if (value instanceof String) tags.put(key, (String) value);});
        return tags;
    }

    /**
     *
     * For a Way, returns all tags that are conditional (with the @-notation), but already filters down
     * the restrictions to those that are time-dependent.
     *
     * Can be used to explore conditional tags, to ensure that we don't filter too much and miss anything.
     * To see only those conditional tags that have time-dependent restrictions, throw away all ConditionalTagData
     * that have empty restrictionData.
     *
     * @param tags
     */
    public static List<ConditionalTagData> getConditionalTagDataWithTimeDependentConditions(Map<String, String> tags) {
        List<ConditionalTagData> restrictionData = new ArrayList<>();
        for (Map.Entry<String, String> tag : tags.entrySet()) {
            String tagValue = tag.getValue();
            if (tag.getKey().contains("conditional") && tagValue.contains("@")) {
                ConditionalTagData conditionalTagData = new ConditionalTagData();
                conditionalTagData.tag = new OSMEntity.Tag(tag.getKey(), tagValue);
                conditionalTagData.restrictionData = new ArrayList<>();
                ConditionalRestrictionParser parser = new ConditionalRestrictionParser(new ByteArrayInputStream(tagValue.getBytes()));
                try {
                    for (Restriction restriction : parser.restrictions()) {
                        for (Condition condition : restriction.getConditions()) {
                            if (condition.isOpeningHours()) {
                                TimeDependentRestrictionData data = new TimeDependentRestrictionData();
                                data.restriction = restriction;
                                OpeningHoursParser ohParser = new OpeningHoursParser(new ByteArrayInputStream(condition.toString().getBytes()));
                                data.rules = ohParser.rules(false);
                                conditionalTagData.restrictionData.add(data);
                            }
                        }
                    }
                } catch (ParseException | ch.poole.openinghoursparser.ParseException e) {
                    System.err.println("In tag: "+tag);
                    System.err.println(e.getMessage());
                }
                restrictionData.add(conditionalTagData);
            }
        }
        return restrictionData;
    }

    public boolean matches(ZonedDateTime zonedWhen, Rule rule) {
        return matchesYears(zonedWhen, rule) && matchesWeekdayRange(zonedWhen, rule) && matchesTimes(zonedWhen, rule);
    }

    private boolean matchesYears(ZonedDateTime zonedWhen, Rule rule) {
        if (rule.getDates() == null) return true;
        for (DateRange dateRange : rule.getDates()) {
            if (!(dateRange.getStartDate() != null && dateRange.getEndDate() != null)) continue;
            if (!(isLikeJavaLocalDate(dateRange.getStartDate()) && isLikeJavaLocalDate(dateRange.getEndDate()))) continue;
            try {
                LocalDate start = toJavaLocalDate(dateRange.getStartDate());
                LocalDate end = toJavaLocalDate((dateRange.getEndDate()));
                if (!zonedWhen.toLocalDate().isBefore(start) && !zonedWhen.toLocalDate().isAfter(end)) {
                    return true;
                }
            } catch (DateTimeException ignored) {
            }
        }
        return false;
    }

    private boolean matchesWeekdayRange(ZonedDateTime when, Rule rule) {
        if (rule.getDays() == null) return true;
        for (WeekDayRange weekDayRange : rule.getDays()) {
            if (matches(when, weekDayRange)) return true;
        }
        return false;
    }

    private boolean matches(ZonedDateTime when, WeekDayRange weekDayRange) {
        DayOfWeek startDay = toJavaDayOfWeek(weekDayRange.getStartDay());
        DayOfWeek endDay = weekDayRange.getEndDay() != null ? toJavaDayOfWeek(weekDayRange.getEndDay()) : startDay;
        if (when.getDayOfWeek().ordinal() >= startDay.ordinal() && when.getDayOfWeek().ordinal() <= endDay.ordinal()) {
            return true;
        }
        return false;
    }

    private DayOfWeek toJavaDayOfWeek(WeekDay weekDay) {
        return DayOfWeek.of(weekDay.ordinal()+1);
    }

    private LocalDate toJavaLocalDate(DateWithOffset dateWithOffset) {
        LocalDate localDate = LocalDate.of(dateWithOffset.getYear(), dateWithOffset.getMonth().ordinal()+1, dateWithOffset.getDay());
        return localDate;
    }

    private boolean isLikeJavaLocalDate(DateWithOffset dateWithOffset) {
        return dateWithOffset.getMonth() != null && !dateWithOffset.undefinedDay() && dateWithOffset.getYear() != YearRange.UNDEFINED_YEAR;
    }

    private boolean matchesTimes(ZonedDateTime when, Rule rule) {
        if (rule.getTimes() == null) return true;
        for (TimeSpan timeSpan : rule.getTimes()) {
            if (matches(when, timeSpan)) return true;
        }
        return false;
    }

    private boolean matches(ZonedDateTime when, TimeSpan time) {
        int startMinute = time.getStart();
        int endMinute = time.getEnd();
        int minuteOfDay = (int) ChronoUnit.MINUTES.between(when.toLocalDate().atStartOfDay(when.getZone()), when);
        if (minuteOfDay >= startMinute && minuteOfDay <= endMinute) {
            return true;
        }
        return false;
    }

    // For weighting
    public Optional<Boolean> accessible(EdgeIteratorState edgeState, Instant at) {
        if (edgeState.get(property)) {
            TimeZone timeZone = timeZones.getTimeZone(ghStorage.getNodeAccess().getLat(edgeState.getBaseNode()), ghStorage.getNodeAccess().getLon(edgeState.getBaseNode()));
            int pointer = edgeState.get(tagPointer);
            Map<String, String> tags = tagStore.getAll(pointer);
            return accessible(tags, at.atZone(timeZone.toZoneId()));
        }
        return Optional.empty();
    }

    public Optional<Boolean> accessible(Map<String, String> tags, ZonedDateTime linkEnterTime) {
        List<ConditionalTagData> conditionalTagDataWithTimeDependentConditions = getConditionalTagDataWithTimeDependentConditions(tags);
        for (ConditionalTagData conditionalTagData : conditionalTagDataWithTimeDependentConditions) {
            for (TimeDependentRestrictionData timeDependentRestrictionData : conditionalTagData.restrictionData) {
                if (timeDependentRestrictionData.restriction.getValue().startsWith("yes")) {
                    for (Rule rule : timeDependentRestrictionData.rules) {
                        if (matches(linkEnterTime, rule)) {
                            return Optional.of(true);
                        }
                    }
                }
                if (timeDependentRestrictionData.restriction.getValue().startsWith("no")) {
                    for (Rule rule : timeDependentRestrictionData.rules) {
                        if (matches(linkEnterTime, rule)) {
                            return Optional.of(false);
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

}