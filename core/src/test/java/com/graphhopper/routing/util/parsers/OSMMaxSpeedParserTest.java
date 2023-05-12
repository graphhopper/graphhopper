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
import com.graphhopper.routing.util.MaxSpeedCalculator;
import com.graphhopper.storage.IntsRef;
import de.westnordost.osm_legal_default_speeds.LegalDefaultSpeeds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OSMMaxSpeedParserTest {

    private LegalDefaultSpeeds defaultSpeeds = MaxSpeedCalculator.createLegalDefaultSpeeds();

    @Test
    void countryRule() {
        DecimalEncodedValue maxSpeedEnc = MaxSpeed.create();
        maxSpeedEnc.init(new EncodedValue.InitializerConfig());
        OSMMaxSpeedParser parser = new OSMMaxSpeedParser(maxSpeedEnc, defaultSpeeds);
        IntsRef relFlags = new IntsRef(2);
        ReaderWay way = new ReaderWay(29L);
        way.setTag("highway", "living_street");
        way.setTag("country", Country.DEU);
        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        int edgeId = 0;
        parser.handleWayTags(edgeId, edgeIntAccess, way, relFlags);
        assertEquals(5, maxSpeedEnc.getDecimal(false, edgeId, edgeIntAccess), .1);

        way.setTag("maxspeed", "30");
        parser.handleWayTags(edgeId, edgeIntAccess, way, relFlags);
        assertEquals(30, maxSpeedEnc.getDecimal(false, edgeId, edgeIntAccess), .1);

        // without a country we get the default value
        edgeIntAccess = new ArrayEdgeIntAccess(1);
        way = new ReaderWay(29L);
        way.setTag("highway", "living_street");
        parser.handleWayTags(edgeId, edgeIntAccess, way, relFlags);
        assertEquals(MaxSpeed.UNSET_SPEED, maxSpeedEnc.getDecimal(false, edgeId, edgeIntAccess), .1);
    }

}
