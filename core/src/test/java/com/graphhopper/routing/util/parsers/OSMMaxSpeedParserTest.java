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
import com.graphhopper.storage.IntsRef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OSMMaxSpeedParserTest {

    @Test
    void countryRule() {
        DecimalEncodedValue maxSpeedEnc = MaxSpeed.create();
        maxSpeedEnc.init(new EncodedValue.InitializerConfig());
        OSMMaxSpeedParser parser = new OSMMaxSpeedParser(maxSpeedEnc);
        IntsRef relFlags = new IntsRef(2);
        ReaderWay way = new ReaderWay(29L);
        way.setTag("highway", "primary");
        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        int edgeId = 0;
        way.setTag("maxspeed", "30");
        parser.handleWayTags(edgeId, edgeIntAccess, way, relFlags);
        assertEquals(30, maxSpeedEnc.getDecimal(false, edgeId, edgeIntAccess), .1);

        // different direction
        edgeIntAccess = new ArrayEdgeIntAccess(1);
        way = new ReaderWay(29L);
        way.setTag("highway", "primary");
        way.setTag("maxspeed:forward", "30");
        way.setTag("maxspeed:backward", "40");
        parser.handleWayTags(edgeId, edgeIntAccess, way, relFlags);
        assertEquals(30, maxSpeedEnc.getDecimal(false, edgeId, edgeIntAccess), .1);
        assertEquals(40, maxSpeedEnc.getDecimal(true, edgeId, edgeIntAccess), .1);
    }

    @Test
    public void parseMaxSpeed() {
        ReaderWay way = new ReaderWay(12);
        way.setTag("maxspeed", "90");
        assertEquals(90, OSMMaxSpeedParser.parseMaxSpeed(way, false), 1e-2);

        way = new ReaderWay(12);
        way.setTag("maxspeed", "90");
        way.setTag("maxspeed:backward", "50");
        assertEquals(90, OSMMaxSpeedParser.parseMaxSpeed(way, false), 1e-2);
        assertEquals(50, OSMMaxSpeedParser.parseMaxSpeed(way, true), 1e-2);

        way = new ReaderWay(12);
        way.setTag("maxspeed", "none");
        assertEquals(MaxSpeed.UNSET_SPEED, OSMMaxSpeedParser.parseMaxSpeed(way, false), 1e-2);

        way = new ReaderWay(12);
        way.setTag("maxspeed", "none");
        way.setTag("highway", "secondary");
        assertEquals(MaxSpeed.UNSET_SPEED, OSMMaxSpeedParser.parseMaxSpeed(way, false), 1e-2);

        way = new ReaderWay(12);
        way.setTag("maxspeed", "none");
        way.setTag("highway", "motorway");
        assertEquals(MaxSpeed.UNLIMITED_SIGN_SPEED, OSMMaxSpeedParser.parseMaxSpeed(way, false), 1e-2);

        way = new ReaderWay(12);
        // we ignore low maxspeeds as they are mostly bugs, see discussion in #3077
        way.setTag("maxspeed", "3");
        assertEquals(MaxSpeed.UNSET_SPEED, OSMMaxSpeedParser.parseMaxSpeed(way, false), 1e-2);

        way = new ReaderWay(12);
        // maxspeed=5 is rather popular
        way.setTag("maxspeed", "5");
        assertEquals(5, OSMMaxSpeedParser.parseMaxSpeed(way, false), 1e-2);
    }

}
