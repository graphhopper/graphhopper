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
import com.graphhopper.storage.BytesRef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OSMMaxSpeedParserTest {

    @Test
    void countryRule() {
        DecimalEncodedValue maxSpeedEnc = MaxSpeed.create();
        maxSpeedEnc.init(new EncodedValue.InitializerConfig());
        OSMMaxSpeedParser parser = new OSMMaxSpeedParser(maxSpeedEnc);
        BytesRef relFlags = new BytesRef(8);
        ReaderWay way = new ReaderWay(29L);
        way.setTag("highway", "primary");
        EdgeBytesAccess edgeAccess = new EdgeBytesAccessArray(4);
        int edgeId = 0;
        way.setTag("maxspeed", "30");
        parser.handleWayTags(edgeId, edgeAccess, way, relFlags);
        assertEquals(30, maxSpeedEnc.getDecimal(false, edgeId, edgeAccess), .1);

        // different direction
        edgeAccess = new EdgeBytesAccessArray(4);
        way = new ReaderWay(29L);
        way.setTag("highway", "primary");
        way.setTag("maxspeed:forward", "30");
        way.setTag("maxspeed:backward", "40");
        parser.handleWayTags(edgeId, edgeAccess, way, relFlags);
        assertEquals(30, maxSpeedEnc.getDecimal(false, edgeId, edgeAccess), .1);
        assertEquals(40, maxSpeedEnc.getDecimal(true, edgeId, edgeAccess), .1);
    }

}
