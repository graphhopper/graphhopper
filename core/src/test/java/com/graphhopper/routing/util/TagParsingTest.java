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

package com.graphhopper.routing.util;

import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.reader.osm.conditional.DateRangeParser;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.parsers.OSMBikeNetworkTagParser;
import com.graphhopper.routing.util.parsers.OSMRoadClassParser;
import com.graphhopper.routing.util.parsers.OSMRoundaboutParser;
import com.graphhopper.routing.util.parsers.TagParser;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.PMap;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static com.graphhopper.routing.util.EncodingManager.getKey;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TagParsingTest {
    @Test
    public void testCombineRelations() {
        ReaderWay osmWay = new ReaderWay(1);
        osmWay.setTag("highway", "track");
        ReaderRelation osmRel = new ReaderRelation(1);

        FlagEncoder defaultBike = FlagEncoders.createBike();
        FlagEncoder lessRelationCodes = FlagEncoders.createBike(new PMap("name=less_relation_bits"));

        EncodingManager em = EncodingManager.create(defaultBike, lessRelationCodes);
        EnumEncodedValue<RouteNetwork> bikeNetworkEnc = em.getEnumEncodedValue(BikeNetwork.KEY, RouteNetwork.class);
        BikeTagParser defaultBikeParser = new BikeTagParser(em, new PMap("name=bike"));
        defaultBikeParser.init(new DateRangeParser());
        BikeTagParser lessRelationCodesParser = new BikeTagParser(em, new PMap("name=less_relation_bits")) {
            @Override
            public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way) {
                if (bikeRouteEnc.getEnum(false, edgeFlags) != RouteNetwork.MISSING)
                    priorityEnc.setDecimal(false, edgeFlags, PriorityCode.getFactor(2));
                return edgeFlags;
            }
        };
        lessRelationCodesParser.init(new DateRangeParser());
        OSMParsers osmParsers = new OSMParsers()
                .addRelationTagParser(relConfig -> new OSMBikeNetworkTagParser(bikeNetworkEnc, relConfig))
                .addWayTagParser(new OSMRoadClassParser(em.getEnumEncodedValue(RoadClass.KEY, RoadClass.class)))
                .addWayTagParser(defaultBikeParser)
                .addWayTagParser(lessRelationCodesParser);

        // relation code is PREFER
        osmRel.setTag("route", "bicycle");
        osmRel.setTag("network", "lcn");
        IntsRef relFlags = osmParsers.createRelationFlags();
        relFlags = osmParsers.handleRelationTags(osmRel, relFlags);
        IntsRef edgeFlags = em.createEdgeFlags();
        edgeFlags = osmParsers.handleWayTags(edgeFlags, osmWay, relFlags);
        assertEquals(RouteNetwork.LOCAL, bikeNetworkEnc.getEnum(false, edgeFlags));
        assertTrue(defaultBike.getPriorityEnc().getDecimal(false, edgeFlags) > lessRelationCodes.getPriorityEnc().getDecimal(false, edgeFlags));
    }

    @Test
    public void testMixBikeTypesAndRelationCombination() {
        ReaderWay osmWay = new ReaderWay(1);
        osmWay.setTag("highway", "track");
        osmWay.setTag("tracktype", "grade1");

        ReaderRelation osmRel = new ReaderRelation(1);

        FlagEncoder bikeEncoder = FlagEncoders.createBike();
        FlagEncoder mtbEncoder = FlagEncoders.createMountainBike();
        EncodingManager manager = EncodingManager.create(bikeEncoder, mtbEncoder);

        EnumEncodedValue<RouteNetwork> bikeNetworkEnc = manager.getEnumEncodedValue(BikeNetwork.KEY, RouteNetwork.class);
        BikeTagParser bikeTagParser = new BikeTagParser(manager, new PMap());
        bikeTagParser.init(new DateRangeParser());
        MountainBikeTagParser mtbTagParser = new MountainBikeTagParser(manager, new PMap());
        mtbTagParser.init(new DateRangeParser());
        OSMParsers osmParsers = new OSMParsers()
                .addRelationTagParser(relConfig -> new OSMBikeNetworkTagParser(bikeNetworkEnc, relConfig))
                .addWayTagParser(new OSMRoadClassParser(manager.getEnumEncodedValue(RoadClass.KEY, RoadClass.class)))
                .addWayTagParser(bikeTagParser)
                .addWayTagParser(mtbTagParser);

        // relation code for network rcn is NICE for bike and PREFER for mountainbike
        osmRel.setTag("route", "bicycle");
        osmRel.setTag("network", "rcn");
        IntsRef relFlags = osmParsers.createRelationFlags();
        relFlags = osmParsers.handleRelationTags(osmRel, relFlags);
        IntsRef edgeFlags = manager.createEdgeFlags();
        edgeFlags = osmParsers.handleWayTags(edgeFlags, osmWay, relFlags);
        // bike: uninfluenced speed for grade but via network => NICE
        // mtb: uninfluenced speed only PREFER
        assertTrue(bikeEncoder.getPriorityEnc().getDecimal(false, edgeFlags) > mtbEncoder.getPriorityEnc().getDecimal(false, edgeFlags));
    }

    @Test
    public void testCompatibilityBug() {
        EncodingManager manager2 = EncodingManager.create("bike2");
        ReaderWay osmWay = new ReaderWay(1);
        osmWay.setTag("highway", "footway");
        osmWay.setTag("name", "test");

        Bike2WeightTagParser parser = new Bike2WeightTagParser(manager2, new PMap());
        parser.init(new DateRangeParser());
        IntsRef flags = parser.handleWayTags(manager2.createEdgeFlags(), osmWay);
        double singleSpeed = parser.avgSpeedEnc.getDecimal(false, flags);
        assertEquals(4, singleSpeed, 1e-3);
        assertEquals(singleSpeed, parser.avgSpeedEnc.getDecimal(true, flags), 1e-3);

        EncodingManager manager = EncodingManager.create("bike2,bike,foot");
        FootTagParser footParser = new FootTagParser(manager, new PMap());
        footParser.init(new DateRangeParser());
        Bike2WeightTagParser bikeParser = new Bike2WeightTagParser(manager, new PMap());
        bikeParser.init(new DateRangeParser());

        flags = footParser.handleWayTags(manager.createEdgeFlags(), osmWay);
        flags = bikeParser.handleWayTags(flags, osmWay);
        DecimalEncodedValue bikeSpeedEnc = manager.getDecimalEncodedValue(getKey("bike2", "average_speed"));
        assertEquals(singleSpeed, bikeSpeedEnc.getDecimal(false, flags), 1e-2);
        assertEquals(singleSpeed, bikeSpeedEnc.getDecimal(true, flags), 1e-2);

        DecimalEncodedValue footSpeedEnc = manager.getDecimalEncodedValue(getKey("foot", "average_speed"));
        assertEquals(5, footSpeedEnc.getDecimal(false, flags), 1e-2);
        assertEquals(5, footSpeedEnc.getDecimal(true, flags), 1e-2);
    }

    @Test
    public void testSharedEncodedValues() {
        EncodingManager manager = EncodingManager.create("car,foot,bike,motorcycle,mtb");

        BooleanEncodedValue roundaboutEnc = manager.getBooleanEncodedValue(Roundabout.KEY);
        List<TagParser> tagParsers = Arrays.asList(
                new OSMRoundaboutParser(roundaboutEnc),
                new CarTagParser(manager, new PMap()),
                new FootTagParser(manager, new PMap()),
                new BikeTagParser(manager, new PMap()),
                new MotorcycleTagParser(manager, new PMap()),
                new MountainBikeTagParser(manager, new PMap())
        );
        for (TagParser tagParser : tagParsers)
            if (tagParser instanceof VehicleTagParser)
                ((VehicleTagParser) tagParser).init(new DateRangeParser());

        final IntsRef edgeFlags = manager.createEdgeFlags();
        IntsRef relFlags = manager.createRelationFlags();
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("junction", "roundabout");
        tagParsers.forEach(p -> p.handleWayTags(edgeFlags, way, relFlags));

        for (FlagEncoder tmp : manager.fetchEdgeEncoders()) {
            BooleanEncodedValue accessEnc = tmp.getAccessEnc();
            assertTrue(accessEnc.getBool(false, edgeFlags));
            assertTrue(roundaboutEnc.getBool(false, edgeFlags), tmp.toString());
        }

        final IntsRef edgeFlags2 = manager.createEdgeFlags();
        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("junction", "circular");
        tagParsers.forEach(p -> p.handleWayTags(edgeFlags2, way, relFlags));

        for (FlagEncoder tmp : manager.fetchEdgeEncoders()) {
            BooleanEncodedValue accessEnc = tmp.getAccessEnc();
            assertTrue(accessEnc.getBool(false, edgeFlags));
            assertTrue(roundaboutEnc.getBool(false, edgeFlags), tmp.toString());
        }
    }

}