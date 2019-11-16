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

package com.graphhopper;

import ch.poole.conditionalrestrictionparser.Condition;
import ch.poole.conditionalrestrictionparser.ConditionalRestrictionParser;
import ch.poole.conditionalrestrictionparser.ParseException;
import ch.poole.conditionalrestrictionparser.Restriction;
import ch.poole.openinghoursparser.OpeningHoursParser;
import ch.poole.openinghoursparser.Rule;
import ch.poole.openinghoursparser.TimeSpan;
import com.conveyal.osmlib.OSMEntity;
import com.conveyal.osmlib.Way;
import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.util.parsers.OSMIDParser;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.EdgeIteratorState;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;

public class TimeDependentAccessRestriction {

    private final BooleanEncodedValue property;
    private final OSMIDParser osmidParser;
    private final GraphHopperStorage ghStorage;
    private final ZoneId zoneId;

    public TimeDependentAccessRestriction(GraphHopperStorage ghStorage) {
        zoneId = ZoneId.of("Europe/Berlin");
        this.ghStorage = ghStorage;
        osmidParser = OSMIDParser.fromEncodingManager(ghStorage.getEncodingManager());
        property = ghStorage.getEncodingManager().getBooleanEncodedValue("conditional");
    }

    public boolean accessible(EdgeIteratorState edgeState, Instant linkEnterTime) {
        if (edgeState.get(property)) {
            long osmid = osmidParser.getOSMID(edgeState.getFlags());
            Way way = ghStorage.getOsm().ways.get(osmid);
            for (OSMEntity.Tag tag : way.tags) {
                if (tag.value.contains("yes") && tag.value.contains("@") && (tag.key.contains("access") || tag.key.contains("vehicle"))) {
                    final ZonedDateTime zonedDateTime = linkEnterTime.atZone(zoneId);
                    ConditionalRestrictionParser parser = new ConditionalRestrictionParser(new ByteArrayInputStream(tag.value.getBytes()));
                    try {
                        for (Restriction restriction : parser.restrictions()) {
                            for (Condition condition : restriction.getConditions()) {
                                if (condition.isOpeningHours()) {
                                    System.out.println(tag);
                                    System.out.println(restriction);
                                    OpeningHoursParser ohParser = new OpeningHoursParser(new ByteArrayInputStream(condition.toString().getBytes()));
                                    ArrayList<Rule> rules = ohParser.rules(false);
                                    for (Rule rule : rules) {
                                        System.out.println(rule);
                                        for (TimeSpan time : Optional.ofNullable(rule.getTimes()).orElse(Collections.emptyList())) {
                                            int startMinute = time.getStart();
                                            int endMinute = time.getEnd();
                                            int minuteOfDay = (int) ChronoUnit.MINUTES.between(zonedDateTime.toLocalDate().atStartOfDay(zoneId), zonedDateTime);
                                            System.out.println(startMinute + " " + endMinute + " vs. " + minuteOfDay);
                                            if (minuteOfDay >= startMinute && minuteOfDay <= endMinute) {
                                                return true;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (ParseException | ch.poole.openinghoursparser.ParseException e) {
                        e.printStackTrace();
                    }
                    return false;
                }
            }
        }
        return true;
    }
}