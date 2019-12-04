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
import com.graphhopper.routing.profiles.EncodedValue;
import com.graphhopper.routing.profiles.EncodedValueLookup;
import com.graphhopper.routing.profiles.EnumEncodedValue;
import com.graphhopper.routing.profiles.Toll;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.EncodingManager.Access;
import com.graphhopper.routing.util.spatialrules.SpatialRule;
import com.graphhopper.routing.util.spatialrules.TransportationMode;
import com.graphhopper.storage.IntsRef;

import static com.graphhopper.routing.profiles.RoadAccess.YES;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class OSMTollParser implements TagParser {

    private final EnumEncodedValue<Toll> tollEnc;
    private static final Set<String> TOLL_VALUES = new HashSet<>(Arrays.asList("yes", "no"));

    public OSMTollParser() {
        this.tollEnc = new EnumEncodedValue<>(Toll.KEY, Toll.class);
    }

    @Override
    public void createEncodedValues(EncodedValueLookup lookup, List<EncodedValue> list) {
        list.add(tollEnc);
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay readerWay, Access access, IntsRef relationFlags) {
        
        Toll toll;
        if (readerWay.hasTag(Arrays.asList("toll", "toll:hgv", "toll:N2", "toll:N3"), TOLL_VALUES)) {
            if (readerWay.hasTag("toll", "yes"))
                toll = Toll.ALL;
            else if (readerWay.hasTag("toll:hgv", "yes"))
                toll = Toll.HGV;
            else if (readerWay.hasTag("toll:N2", "yes"))
                toll = Toll.HGV;
            else if (readerWay.hasTag("toll:N3", "yes"))
                toll = Toll.HGV;
            else
                toll = Toll.NO;
        } else {
            SpatialRule spatialRule = readerWay.getTag("spatial_rule", null);
            if (spatialRule != null) {
                toll = spatialRule.getToll(readerWay.getTag("highway", ""), Toll.NO);
            } else {
                toll = Toll.NO;
            }
        }
        
        tollEnc.setEnum(false, edgeFlags, toll);
        
        return edgeFlags;
    }
}
