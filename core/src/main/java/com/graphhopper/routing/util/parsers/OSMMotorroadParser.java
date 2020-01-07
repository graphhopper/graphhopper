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

import java.util.List;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.profiles.EncodedValue;
import com.graphhopper.routing.profiles.EncodedValueLookup;
import com.graphhopper.routing.profiles.Motorroad;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.Helper;

public class OSMMotorroadParser implements TagParser {

    private final BooleanEncodedValue osmMotorroadEnc;

    public OSMMotorroadParser() {
        this.osmMotorroadEnc = Motorroad.create();
    }

    @Override
    public void createEncodedValues(EncodedValueLookup lookup, List<EncodedValue> link) {
        link.add(osmMotorroadEnc);
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay readerWay, boolean ferry, IntsRef relationFlags) {
        String highwayTag = readerWay.getTag("highway");
        if (!Helper.isEmpty(highwayTag) && readerWay.hasTag("motorroad", "yes")
                        && !"motorway".equals(highwayTag) && !"motorway_link".equals(highwayTag)) {
            osmMotorroadEnc.setBool(false, edgeFlags, true);
        }
        return edgeFlags;
    }
}
