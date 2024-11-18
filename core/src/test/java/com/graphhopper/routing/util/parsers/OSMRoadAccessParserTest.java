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

package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.TransportationMode;
import com.graphhopper.routing.util.countryrules.CountryRule;
import com.graphhopper.storage.IntsRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OSMRoadAccessParserTest {

    private final EnumEncodedValue<CarRoadAccess> roadAccessEnc = CarRoadAccess.create();
    private OSMRoadAccessParser<CarRoadAccess> parser;

    @BeforeEach
    public void setup() {
        roadAccessEnc.init(new EncodedValue.InitializerConfig());
        parser = new OSMRoadAccessParser<>(roadAccessEnc, OSMRoadAccessParser.toOSMRestrictions(TransportationMode.CAR),
                CarRoadAccess::countryHook, CarRoadAccess::find);
    }

    @Test
    void countryRule() {
        IntsRef relFlags = new IntsRef(2);
        ReaderWay way = new ReaderWay(27L);
        way.setTag("highway", "track");
        way.setTag("country_rule", new CountryRule() {
            @Override
            public CarRoadAccess getAccess(ReaderWay readerWay, TransportationMode transportationMode, CarRoadAccess currentCarRoadAccess) {
                return CarRoadAccess.DESTINATION;
            }
        });
        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        int edgeId = 0;
        parser.handleWayTags(edgeId, edgeIntAccess, way, relFlags);
        assertEquals(CarRoadAccess.DESTINATION, roadAccessEnc.getEnum(false, edgeId, edgeIntAccess));

        // if there is no country rule we get the default value
        edgeIntAccess = new ArrayEdgeIntAccess(1);
        way.removeTag("country_rule");
        parser.handleWayTags(edgeId, edgeIntAccess, way, relFlags);
        assertEquals(CarRoadAccess.MISSING, roadAccessEnc.getEnum(false, edgeId, edgeIntAccess));

        way.setTag("motor_vehicle", "agricultural;forestry");
        parser.handleWayTags(edgeId, edgeIntAccess, way, relFlags);
        assertEquals(CarRoadAccess.AGRICULTURAL, roadAccessEnc.getEnum(false, edgeId, edgeIntAccess));

        way.setTag("motor_vehicle", "forestry;agricultural");
        parser.handleWayTags(edgeId, edgeIntAccess, way, relFlags);
        assertEquals(CarRoadAccess.AGRICULTURAL, roadAccessEnc.getEnum(false, edgeId, edgeIntAccess));

    }

    @Test
    public void testPermit() {
        ArrayEdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        int edgeId = 0;
        ReaderWay way = new ReaderWay(27L);
        way.setTag("motor_vehicle", "permit");
        parser.handleWayTags(edgeId, edgeIntAccess, way, new IntsRef(1));
        assertEquals(CarRoadAccess.PRIVATE, roadAccessEnc.getEnum(false, edgeId, edgeIntAccess));
    }

    @Test
    public void testCar() {
        ArrayEdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        int edgeId = 0;
        ReaderWay way = new ReaderWay(1L);
        way.setTag("access", "private");
        parser.handleWayTags(edgeId, edgeIntAccess, way, new IntsRef(1));
        assertEquals(CarRoadAccess.PRIVATE, roadAccessEnc.getEnum(false, edgeId, edgeIntAccess));

        edgeIntAccess = new ArrayEdgeIntAccess(1);
        way.setTag("motorcar", "yes");
        parser.handleWayTags(edgeId, edgeIntAccess, way, new IntsRef(1));
        assertEquals(CarRoadAccess.YES, roadAccessEnc.getEnum(false, edgeId, edgeIntAccess));
    }
}
