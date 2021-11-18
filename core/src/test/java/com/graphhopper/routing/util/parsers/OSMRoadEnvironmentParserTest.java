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
import com.graphhopper.routing.ev.RoadEnvironment;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.IntsRef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OSMRoadEnvironmentParserTest {

    @Test
    void ferry() {
        OSMRoadEnvironmentParser parser = new OSMRoadEnvironmentParser();
        CarFlagEncoder carEncoder = new CarFlagEncoder();
        EncodingManager em = new EncodingManager.Builder()
                .add(carEncoder)
                .add(parser).build();
        EnumEncodedValue<RoadEnvironment> roadEnvironmentEnc = em.getEnumEncodedValue(RoadEnvironment.KEY, RoadEnvironment.class);
        IntsRef edgeFlags = em.createEdgeFlags();
        ReaderWay way = new ReaderWay(0);
        way.setTag("route", "shuttle_train");
        parser.handleWayTags(edgeFlags, way, em.createRelationFlags());
        RoadEnvironment roadEnvironment = roadEnvironmentEnc.getEnum(false, edgeFlags);
        assertEquals(RoadEnvironment.FERRY, roadEnvironment);
    }

}