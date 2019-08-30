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
import com.graphhopper.storage.IntsRef;

import java.util.List;

public class OSMTollParser implements TagParser {

    private final EnumEncodedValue<Toll> tollEnc;

    public OSMTollParser() {
        this.tollEnc = new EnumEncodedValue<>(Toll.KEY, Toll.class);
    }

    @Override
    public void createEncodedValues(EncodedValueLookup lookup, List<EncodedValue> list) {
        list.add(tollEnc);
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay readerWay, EncodingManager.Access access, long relationFlags) {
        if (readerWay.hasTag("toll", "yes"))
            tollEnc.setEnum(false, edgeFlags, Toll.ALL);
        else if (readerWay.hasTag("toll:hgv", "yes"))
            tollEnc.setEnum(false, edgeFlags, Toll.HGV);
        else if (readerWay.hasTag("toll:N2", "yes"))
            tollEnc.setEnum(false, edgeFlags, Toll.HGV);
        else if (readerWay.hasTag("toll:N3", "yes"))
            tollEnc.setEnum(false, edgeFlags, Toll.HGV);
        return edgeFlags;
    }
}
