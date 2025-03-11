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
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.TrafficSignalsSound;
import com.graphhopper.storage.IntsRef;

/**
 * see: https://wiki.openstreetmap.org/wiki/Key:traffic_signals:sound
 */
public class OSMTrafficSignalsSoundParser implements TagParser {
    private final EnumEncodedValue<TrafficSignalsSound> trafficSignalsSoundEnc;

    public OSMTrafficSignalsSoundParser(
                    EnumEncodedValue<TrafficSignalsSound> trafficSignalsSoundEnc) {
        this.trafficSignalsSoundEnc = trafficSignalsSoundEnc;
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way,
                    IntsRef relationFlags) {
        String trafficSignalsSound = way.getTag("traffic_signals:sound");
        trafficSignalsSoundEnc.setEnum(false, edgeId, edgeIntAccess,
                        TrafficSignalsSound.find(trafficSignalsSound));
    }
}
