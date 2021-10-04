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
package com.graphhopper.reader.osm.conditional;

import com.graphhopper.reader.ConditionalTagInspector;
import com.graphhopper.reader.ReaderWay;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Robin Boldt
 * @author Andrzej Oles
 */
public class ConditionalOSMTagInspectorTest extends CalendarBasedTest {
    private static Set<String> getSampleRestrictedValues() {
        Set<String> restrictedValues = new HashSet<>();
        restrictedValues.add("private");
        restrictedValues.add("agricultural");
        restrictedValues.add("forestry");
        restrictedValues.add("no");
        restrictedValues.add("restricted");
        restrictedValues.add("delivery");
        restrictedValues.add("military");
        restrictedValues.add("emergency");
        return restrictedValues;
    }

    private static Set<String> getSamplePermissiveValues() {
        Set<String> restrictedValues = new HashSet<>();
        restrictedValues.add("yes");
        restrictedValues.add("permissive");
        return restrictedValues;
    }

    private static List<String> getSampleConditionalTags() {
        List<String> conditionalTags = new ArrayList<>();
        conditionalTags.add("vehicle");
        conditionalTags.add("access");
        return conditionalTags;
    }

    private static ConditionalOSMTagInspector createConditionalOSMTagInspector() {
        return new ConditionalOSMTagInspector(Arrays.asList(), getSampleConditionalTags(), getSampleRestrictedValues(), getSamplePermissiveValues(), false);
    }

    @Test
    public void testConditionalAccept() {
        ConditionalOSMTagInspector acceptor = createConditionalOSMTagInspector();
        acceptor.addValueParser(new DateRangeParser(getCalendar(2014, Calendar.MARCH, 10)));
        ReaderWay way = new ReaderWay(1);
        way.setTag("vehicle:conditional", "no @ (Aug 10-Aug 14)");
        assertFalse(acceptor.isPermittedWayConditionallyRestricted(way));
    }

    @Test
    public void testConditionalAcceptNextYear() {
        ConditionalOSMTagInspector acceptor = createConditionalOSMTagInspector();
        acceptor.addValueParser(new DateRangeParser(getCalendar(2014, Calendar.MARCH, 10)));
        ReaderWay way = new ReaderWay(1);
        way.setTag("vehicle:conditional", "no @ (2013 Mar 1-2013 Mar 31)");
        assertFalse(acceptor.isPermittedWayConditionallyRestricted(way));
    }

    @Test
    public void testConditionalReject() {
        ConditionalOSMTagInspector acceptor = createConditionalOSMTagInspector();
        acceptor.addValueParser(new DateRangeParser(getCalendar(2014, Calendar.MARCH, 10)));
        ReaderWay way = new ReaderWay(1);
        way.setTag("vehicle:conditional", "no @ (Mar 10-Aug 14)");
        assertTrue(acceptor.isPermittedWayConditionallyRestricted(way));
    }

    @Test
    public void testConditionalAllowance() {
        ConditionalOSMTagInspector acceptor = createConditionalOSMTagInspector();
        acceptor.addValueParser(new DateRangeParser(getCalendar(2014, Calendar.MARCH, 10)));
        ReaderWay way = new ReaderWay(1);
        way.setTag("vehicle:conditional", "yes @ (Mar 10-Aug 14)");
        assertTrue(acceptor.isRestrictedWayConditionallyPermitted(way));
    }

    @Test
    public void testConditionalAllowanceReject() {
        ConditionalOSMTagInspector acceptor = createConditionalOSMTagInspector();
        acceptor.addValueParser (new DateRangeParser(getCalendar(2014, Calendar.MARCH, 10)));
        ReaderWay way = new ReaderWay(1);
        way.setTag("vehicle:conditional", "no @ (Mar 10-Aug 14)");
        assertTrue(acceptor.isPermittedWayConditionallyRestricted(way));
    }

    @Test
    public void testConditionalSingleDay() {
        ConditionalOSMTagInspector acceptor = createConditionalOSMTagInspector();
        acceptor.addValueParser(new DateRangeParser(getCalendar(2015, Calendar.DECEMBER, 27)));
        ReaderWay way = new ReaderWay(1);
        way.setTag("vehicle:conditional", "no @ (Su)");
        assertTrue(acceptor.isPermittedWayConditionallyRestricted(way));
    }

    @Test
    public void testConditionalAllowanceSingleDay() {
        ConditionalOSMTagInspector acceptor = createConditionalOSMTagInspector();
        acceptor.addValueParser(new DateRangeParser(getCalendar(2015, Calendar.DECEMBER, 27)));
        ReaderWay way = new ReaderWay(1);
        way.setTag("vehicle:conditional", "yes @ (Su)");
        assertTrue(acceptor.isRestrictedWayConditionallyPermitted(way));
    }

    // ORS-GH MOD START - additional tests
    @Test
    public void testConditionalAccessHours() {
        ConditionalOSMTagInspector acceptor = createConditionalOSMTagInspector();
        acceptor.addValueParser(new DateRangeParser(getCalendar(2015, Calendar.DECEMBER, 27)));
        ReaderWay way = new ReaderWay(1);
        way.setTag("vehicle:conditional", "no @ (10:00-18:00)");
        assertFalse(acceptor.isPermittedWayConditionallyRestricted(way));
        acceptor.addValueParser(ConditionalParser.createDateTimeParser());
        assertFalse(acceptor.isPermittedWayConditionallyRestricted(way));
    }

    @Test
    public void testConditionalAccessLength() {
        ConditionalOSMTagInspector acceptor = createConditionalOSMTagInspector();
        acceptor.addValueParser(new DateRangeParser(getCalendar(2015, Calendar.DECEMBER, 27)));
        ReaderWay way = new ReaderWay(1);
        way.setTag("vehicle:conditional", "no @ length>5");
        assertFalse(acceptor.isPermittedWayConditionallyRestricted(way));
        acceptor.addValueParser(ConditionalParser.createNumberParser("length", 10));
        assertTrue(acceptor.isPermittedWayConditionallyRestricted(way));
    }

    @Test
    public void testCombinedConditionHoursAndLength() {
        ConditionalOSMTagInspector acceptor = createConditionalOSMTagInspector();
        acceptor.addValueParser(new DateRangeParser(getCalendar(2015, Calendar.DECEMBER, 27)));
        ReaderWay way = new ReaderWay(1);
        way.setTag("vehicle:conditional", "no @ (10:00-18:00 AND length>5)");
        assertFalse(acceptor.isPermittedWayConditionallyRestricted(way));
        acceptor.addValueParser(ConditionalParser.createDateTimeParser());
        assertFalse(acceptor.isPermittedWayConditionallyRestricted(way));
        acceptor.addValueParser(ConditionalParser.createNumberParser("length", 10));
        assertFalse(acceptor.isPermittedWayConditionallyRestricted(way));
    }
    // ORS-GH MOD END
}
