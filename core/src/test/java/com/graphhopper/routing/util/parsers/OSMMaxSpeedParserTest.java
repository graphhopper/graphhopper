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
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EncodedValue;
import com.graphhopper.routing.ev.MaxSpeed;
import com.graphhopper.routing.util.TransportationMode;
import com.graphhopper.routing.util.countryrules.CountryRule;
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
        way.setTag("highway", "living_street");
        way.setTag("country_rule", new CountryRule() {
            @Override
            public double getMaxSpeed(ReaderWay readerWay, TransportationMode transportationMode, double currentMaxSpeed) {
                return 5;
            }
        });
        IntsRef edgeFlags = new IntsRef(1);
        parser.handleWayTags(edgeId, intAccess, way, relFlags);
        assertEquals(5, maxSpeedEnc.getDecimal(false, edgeId, intAccess), .1);

        // without a country_rule we get the default value
        edgeFlags = new IntsRef(1);
        way.removeTag("country_rule");
        parser.handleWayTags(edgeId, intAccess, way, relFlags);
        assertEquals(MaxSpeed.UNSET_SPEED, maxSpeedEnc.getDecimal(false, edgeId, intAccess), .1);
    }

}