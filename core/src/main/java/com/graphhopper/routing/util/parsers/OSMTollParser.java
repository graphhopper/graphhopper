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
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.ev.Toll;
import com.graphhopper.routing.util.countryrules.CountryRule;
import com.graphhopper.storage.IntsRef;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class OSMTollParser implements TagParser {

    private static final List<String> HGV_TAGS = Collections.unmodifiableList(Arrays.asList("toll:hgv", "toll:N2", "toll:N3"));
    private final EnumEncodedValue<Toll> tollEnc;

    public OSMTollParser(EnumEncodedValue<Toll> tollEnc) {
        this.tollEnc = tollEnc;
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay readerWay, IntsRef relationFlags) {
        Toll toll;
        if (readerWay.hasTag("toll", "yes")) {
            toll = Toll.ALL;
        } else if (readerWay.hasTag(HGV_TAGS, "yes")) {
            toll = Toll.HGV;
        } else if (readerWay.hasTag("toll", "no")) {
            toll = Toll.NO;
        } else {
            toll = Toll.MISSING;
        }
        
        CountryRule countryRule = readerWay.getTag("country_rule", null);
        if (countryRule != null)
            toll = countryRule.getToll(readerWay, toll);

        tollEnc.setEnum(false, edgeId, edgeIntAccess, toll);
    }
}
