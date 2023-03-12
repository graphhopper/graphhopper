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
import com.graphhopper.routing.ev.IntEncodedValue;
import com.graphhopper.storage.IntsRef;

public class OSMWayIDParser implements TagParser {
    private final IntEncodedValue osmWayIdEnc;

    public OSMWayIDParser(IntEncodedValue osmWayIdEnc) {
        this.osmWayIdEnc = osmWayIdEnc;
    }

    @Override
    public void handleWayTags(IntsRef edgeFlags, ReaderWay way, IntsRef relationFlags) {
        if (way.getId() > osmWayIdEnc.getMaxStorableInt())
            throw new IllegalArgumentException("Cannot store OSM way ID: " + way.getId() + " as it is too large (> "
                    + osmWayIdEnc.getMaxStorableInt() + "). You can disable " + osmWayIdEnc.getName() + " if you do not " +
                    "need to store the OSM way IDs");
        int wayId = Math.toIntExact(way.getId());
        osmWayIdEnc.setInt(false, edgeFlags, wayId);
    }
}
