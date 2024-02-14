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

import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.reader.osm.conditional.DateRangeParser;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.OSMParsers;
import com.graphhopper.routing.util.PriorityCode;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.PMap;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TagParsingTest {
    @Test
    public void testCombineRelations() {
        ReaderWay osmWay = new ReaderWay(1);
        osmWay.setTag("highway", "track");
        ReaderRelation osmRel = new ReaderRelation(1);

        BooleanEncodedValue bike1AccessEnc = VehicleAccess.create("bike1");
        DecimalEncodedValue bike1SpeedEnc = VehicleSpeed.create("bike1", 4, 2, false);
        DecimalEncodedValue bike1PriorityEnc = VehiclePriority.create("bike1", 4, PriorityCode.getFactor(1), false);
        BooleanEncodedValue bike2AccessEnc = VehicleAccess.create("bike2");
        DecimalEncodedValue bike2SpeedEnc = VehicleSpeed.create("bike2", 4, 2, false);
        DecimalEncodedValue bike2PriorityEnc = VehiclePriority.create("bike2", 4, PriorityCode.getFactor(1), false);
        EnumEncodedValue<RouteNetwork> bikeNetworkEnc = RouteNetwork.create(BikeNetwork.KEY);
        EncodingManager em = EncodingManager.start()
                .add(bike1AccessEnc).add(bike1SpeedEnc).add(bike1PriorityEnc)
                .add(bike2AccessEnc).add(bike2SpeedEnc).add(bike2PriorityEnc)
                .add(bikeNetworkEnc)
                .add(Smoothness.create())
                .add(RoadClass.create())
                .build();
        BikePriorityParser bike1Parser = new BikePriorityParser(bike1PriorityEnc, bike1SpeedEnc, bikeNetworkEnc);
        BikePriorityParser bike2Parser = new BikePriorityParser(bike2PriorityEnc, bike2SpeedEnc, bikeNetworkEnc) {
            @Override
            public void handleWayTags(int edgeId, EdgeIntAccess intAccess, ReaderWay way, IntsRef relTags) {
                // accept less relations
                if (bikeRouteEnc.getEnum(false, edgeId, intAccess) != RouteNetwork.MISSING)
                    priorityEnc.setDecimal(false, edgeId, intAccess, PriorityCode.getFactor(2));
            }
        };
        OSMParsers osmParsers = new OSMParsers()
                .addRelationTagParser(relConfig -> new OSMBikeNetworkTagParser(bikeNetworkEnc, relConfig))
                .addWayTagParser(new OSMRoadClassParser(em.getEnumEncodedValue(RoadClass.KEY, RoadClass.class)))
                .addWayTagParser(bike1Parser)
                .addWayTagParser(bike2Parser);

        // relation code is PREFER
        osmRel.setTag("route", "bicycle");
        osmRel.setTag("network", "lcn");
        IntsRef relFlags = osmParsers.createRelationFlags();
        relFlags = osmParsers.handleRelationTags(osmRel, relFlags);
        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(em.getIntsForFlags());
        int edgeId = 0;
        osmParsers.handleWayTags(edgeId, edgeIntAccess, osmWay, relFlags);
        assertEquals(RouteNetwork.LOCAL, bikeNetworkEnc.getEnum(false, edgeId, edgeIntAccess));
        assertTrue(bike1PriorityEnc.getDecimal(false, edgeId, edgeIntAccess) > bike2PriorityEnc.getDecimal(false, edgeId, edgeIntAccess));
    }

    @Test
    public void testMixBikeTypesAndRelationCombination() {
        ReaderWay osmWay = new ReaderWay(1);
        osmWay.setTag("highway", "track");
        osmWay.setTag("tracktype", "grade1");

        ReaderRelation osmRel = new ReaderRelation(1);

        BooleanEncodedValue bikeAccessEnc = VehicleAccess.create("bike");
        DecimalEncodedValue bikeSpeedEnc = VehicleSpeed.create("bike", 4, 2, false);
        DecimalEncodedValue bikePriorityEnc = VehiclePriority.create("bike", 4, PriorityCode.getFactor(1), false);
        BooleanEncodedValue mtbAccessEnc = VehicleAccess.create("mtb");
        DecimalEncodedValue mtbSpeedEnc = VehicleSpeed.create("mtb", 4, 2, false);
        DecimalEncodedValue mtbPriorityEnc = VehiclePriority.create("mtb", 4, PriorityCode.getFactor(1), false);
        EnumEncodedValue<RouteNetwork> bikeNetworkEnc = RouteNetwork.create(BikeNetwork.KEY);
        EncodingManager em = EncodingManager.start()
                .add(bikeAccessEnc).add(bikeSpeedEnc).add(bikePriorityEnc)
                .add(mtbAccessEnc).add(mtbSpeedEnc).add(mtbPriorityEnc)
                .add(bikeNetworkEnc)
                .add(Smoothness.create())
                .add(RoadClass.create())
                .build();
        BikePriorityParser bikeTagParser = new BikePriorityParser(em);
        MountainBikePriorityParser mtbTagParser = new MountainBikePriorityParser(em);
        OSMParsers osmParsers = new OSMParsers()
                .addRelationTagParser(relConfig -> new OSMBikeNetworkTagParser(bikeNetworkEnc, relConfig))
                .addWayTagParser(new OSMRoadClassParser(em.getEnumEncodedValue(RoadClass.KEY, RoadClass.class)))
                .addWayTagParser(bikeTagParser)
                .addWayTagParser(mtbTagParser);

        // relation code for network rcn is NICE for bike and PREFER for mountainbike
        osmRel.setTag("route", "bicycle");
        osmRel.setTag("network", "rcn");
        IntsRef relFlags = osmParsers.createRelationFlags();
        relFlags = osmParsers.handleRelationTags(osmRel, relFlags);
        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(em.getIntsForFlags());
        int edgeId = 0;
        osmParsers.handleWayTags(edgeId, edgeIntAccess, osmWay, relFlags);
        // bike: uninfluenced speed for grade but via network => NICE
        // mtb: uninfluenced speed only PREFER
        assertTrue(bikePriorityEnc.getDecimal(false, edgeId, edgeIntAccess) > mtbPriorityEnc.getDecimal(false, edgeId, edgeIntAccess));
    }

    @Test
    public void testSharedEncodedValues() {
        BooleanEncodedValue carAccessEnc = VehicleAccess.create("car");
        BooleanEncodedValue footAccessEnc = VehicleAccess.create("foot");
        BooleanEncodedValue bikeAccessEnc = VehicleAccess.create("bike");
        BooleanEncodedValue mtbAccessEnc = VehicleAccess.create("mtb");
        List<BooleanEncodedValue> accessEncs = Arrays.asList(carAccessEnc, footAccessEnc, bikeAccessEnc, mtbAccessEnc);
        EncodingManager manager = EncodingManager.start()
                .add(carAccessEnc).add(VehicleSpeed.create("car", 5, 5, true))
                .add(footAccessEnc).add(VehicleSpeed.create("foot", 4, 1, true)).add(VehiclePriority.create("foot", 4, PriorityCode.getFactor(1), false))
                .add(bikeAccessEnc).add(VehicleSpeed.create("bike", 4, 2, false)).add(VehiclePriority.create("bike", 4, PriorityCode.getFactor(1), false))
                .add(mtbAccessEnc).add(VehicleSpeed.create("mtb", 4, 2, false)).add(VehiclePriority.create("mtb", 4, PriorityCode.getFactor(1), false))
                .add(RouteNetwork.create(FootNetwork.KEY))
                .add(RouteNetwork.create(BikeNetwork.KEY))
                .add(Roundabout.create())
                .add(Smoothness.create())
                .build();

        BooleanEncodedValue roundaboutEnc = manager.getBooleanEncodedValue(Roundabout.KEY);
        List<TagParser> tagParsers = Arrays.asList(
                new OSMRoundaboutParser(roundaboutEnc),
                new CarAccessParser(manager, new PMap()),
                new FootAccessParser(manager, new PMap()),
                new BikeAccessParser(manager, new PMap()),
                new MountainBikeAccessParser(manager, new PMap())
        );
        for (TagParser tagParser : tagParsers)
            if (tagParser instanceof AbstractAccessParser)
                ((AbstractAccessParser) tagParser).init(new DateRangeParser());

        final ArrayEdgeIntAccess intAccess = new ArrayEdgeIntAccess(manager.getIntsForFlags());
        int edgeId = 0;
        IntsRef relFlags = manager.createRelationFlags();
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("junction", "roundabout");
        tagParsers.forEach(p -> p.handleWayTags(edgeId, intAccess, way, relFlags));

        assertTrue(roundaboutEnc.getBool(false, edgeId, intAccess));
        for (BooleanEncodedValue accessEnc : accessEncs)
            assertTrue(accessEnc.getBool(false, edgeId, intAccess));

        final IntsRef edgeFlags2 = manager.createEdgeFlags();
        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("junction", "circular");
        tagParsers.forEach(p -> p.handleWayTags(edgeId, intAccess, way, relFlags));

        assertTrue(roundaboutEnc.getBool(false, edgeId, intAccess));
        for (BooleanEncodedValue accessEnc : accessEncs)
            assertTrue(accessEnc.getBool(false, edgeId, intAccess));
    }

}
