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
package com.graphhopper.routing.util;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.reader.osm.conditional.DateRangeParser;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PMap;
import org.junit.jupiter.api.Test;

import java.text.DateFormat;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Peter Karich
 * @author zstadler
 */
public class Car4WDTagParserTest extends CarTagParserTest {

    @Override
    protected String getCarName() {
        return "car4wd";
    }

    @Override
    CarTagParser createParser(EncodedValueLookup lookup, PMap properties) {
        Car4WDTagParser parser = new Car4WDTagParser(lookup, properties);
        parser.init(new DateRangeParser());
        return parser;
    }

    @Override
    @Test
    public void testAccess() {
        ReaderWay way = new ReaderWay(1);
        assertTrue(parser.getAccess(way).canSkip());
        way.setTag("highway", "service");
        assertTrue(parser.getAccess(way).isWay());
        way.setTag("access", "no");
        assertTrue(parser.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "track");
        assertTrue(parser.getAccess(way).isWay());

        way.setTag("motorcar", "no");
        assertTrue(parser.getAccess(way).canSkip());

        // for now allow grade1+2+3 for every country, see #253
        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("tracktype", "grade2");
        assertTrue(parser.getAccess(way).isWay());
        // This is the only difference from a "car"
        way.setTag("tracktype", "grade4");
        assertTrue(parser.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("access", "delivery");
        assertTrue(parser.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "unclassified");
        way.setTag("ford", "yes");
        assertTrue(parser.getAccess(way).canSkip());
        way.setTag("motorcar", "yes");
        assertTrue(parser.getAccess(way).isWay());

        way.clearTags();
        way.setTag("route", "ferry");
        assertTrue(parser.getAccess(way).isFerry());
        way.setTag("motorcar", "no");
        assertTrue(parser.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("route", "ferry");
        way.setTag("foot", "yes");
        assertTrue(parser.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("access", "yes");
        way.setTag("motor_vehicle", "no");
        assertTrue(parser.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("access", "yes");
        way.setTag("motor_vehicle", "no");
        assertTrue(parser.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("access", "no");
        way.setTag("motorcar", "yes");
        assertTrue(parser.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("access", "emergency");
        assertTrue(parser.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("motor_vehicle", "emergency");
        assertTrue(parser.getAccess(way).canSkip());

        DateFormat simpleDateFormat = Helper.createFormatter("yyyy MMM dd");

        way.clearTags();
        way.setTag("highway", "road");
        way.setTag("access:conditional", "no @ (" + simpleDateFormat.format(new Date().getTime()) + ")");
        assertTrue(parser.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "road");
        way.setTag("access", "no");
        way.setTag("access:conditional", "yes @ (" + simpleDateFormat.format(new Date().getTime()) + ")");
        assertTrue(parser.getAccess(way).isWay());
    }
}
