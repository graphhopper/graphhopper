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
import com.graphhopper.routing.ev.EncodedValue;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.IntEncodedValue;
import com.graphhopper.routing.ev.Lanes;
import com.graphhopper.storage.IntsRef;

import java.util.List;

/**
 * https://wiki.openstreetmap.org/wiki/Key:lanes
 */
public class OSMLanesParser implements TagParser {
    private final IntEncodedValue lanesEnc;

    public OSMLanesParser() {
        this(Lanes.create());
    }

    public OSMLanesParser(IntEncodedValue lanesEnc) {
        this.lanesEnc = lanesEnc;
    }

    @Override
    public void createEncodedValues(EncodedValueLookup lookup, List<EncodedValue> registerNewEncodedValue) {
        registerNewEncodedValue.add(lanesEnc);
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way, IntsRef relationFlags) {
        int laneCount = 1;
        if (way.hasTag("lanes")) {
            String noLanes = way.getTag("lanes");
            String[] noLanesTok = noLanes.split(";|\\.");
            if (noLanesTok.length > 0) {
                try {
                    int noLanesInt = Integer.parseInt(noLanesTok[0]);
                    // there was a proposal with negative lanes but I cannot find it
                    if (noLanesInt < 0)
                        laneCount = 1;
                    else if (noLanesInt > 6)
                        laneCount = 6;
                    else
                        laneCount = noLanesInt;
                } catch (NumberFormatException ex) {
                    // ignore if no number
                }
            }
        }
        lanesEnc.setInt(false, edgeFlags, laneCount);
        return edgeFlags;
    }
}
