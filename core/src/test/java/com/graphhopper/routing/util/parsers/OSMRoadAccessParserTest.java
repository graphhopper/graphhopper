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
import com.graphhopper.routing.ev.RoadAccess;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.TransportationMode;
import com.graphhopper.routing.util.countryrules.CountryRule;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OSMRoadAccessParserTest {

    @Test
    void countryRule() {
        EncodingManager em = EncodingManager.create("car");
        EnumEncodedValue<RoadAccess> roadAccessEnc = em.getEnumEncodedValue(RoadAccess.KEY, RoadAccess.class);
        Graph graph = new GraphBuilder(em).create();
        FlagEncoder encoder = em.getEncoder("car");
        EdgeIteratorState e1 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 1).setDistance(100));
        EdgeIteratorState e2 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(1, 2).setDistance(100));

        OSMRoadAccessParser parser = new OSMRoadAccessParser(roadAccessEnc, OSMRoadAccessParser.toOSMRestrictions(TransportationMode.CAR));
        IntsRef relFlags = em.createRelationFlags();
        ReaderWay way = new ReaderWay(27L);
        way.setTag("highway", "track");
        way.setTag("country_rule", new CountryRule() {
            @Override
            public RoadAccess getAccess(ReaderWay readerWay, TransportationMode transportationMode, RoadAccess currentRoadAccess) {
                return RoadAccess.DESTINATION;
            }
        });
        parser.handleWayTags(e1.getFlags(), way, relFlags);
        assertEquals(RoadAccess.DESTINATION, e1.get(roadAccessEnc));

        // if there is no country rule we get the default value
        way.removeTag("country_rule");
        parser.handleWayTags(e2.getFlags(), way, relFlags);
        assertEquals(RoadAccess.YES, e2.get(roadAccessEnc));
    }

}