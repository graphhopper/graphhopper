package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.TransportationMode;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.PMap;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Combined test for car_access and road_access. These two encoded values are conceptually one
 * determination — "what kind of access does this road have?" — split into a boolean (open/closed)
 * and a qualification (YES/DESTINATION/DELIVERY/PRIVATE/...) for technical reasons.
 * <p>
 * This test ensures they stay in sync across tag combinations and parser configurations.
 */
class CarAccessAndRoadAccessTest {

    // Default configuration: delivery is restricted, private is restricted
    static Stream<Arguments> defaultConfig() {
        return Stream.of(
                //        highway        extra tags                                                               access  road_access
                Arguments.of("residential", "",                                                                   true,  RoadAccess.YES),
                Arguments.of("residential", "motor_vehicle=destination",                                          true,  RoadAccess.DESTINATION),
                Arguments.of("residential", "motor_vehicle=private",                                              false, RoadAccess.PRIVATE),
                Arguments.of("residential", "motor_vehicle=delivery",                                             false, RoadAccess.DELIVERY),
                Arguments.of("residential", "motor_vehicle=no",                                                   false, RoadAccess.NO),
                Arguments.of("residential", "motor_vehicle=no|motor_vehicle:conditional=delivery @ (06:00-11:00)", false, RoadAccess.DELIVERY),
                Arguments.of("pedestrian",  "",                                                                   false, RoadAccess.YES),
                Arguments.of("pedestrian",  "motor_vehicle:conditional=delivery @ (Mo-Fr 06:00-11:00)",           false, RoadAccess.DELIVERY)
        );
    }

    // block_private=false: private/permit/service move from restricted to allowed
    static Stream<Arguments> blockPrivateFalse() {
        return Stream.of(
                //        highway        extra tags                                                               access  road_access
                Arguments.of("residential", "",                                                                   true,  RoadAccess.YES),
                Arguments.of("residential", "motor_vehicle=destination",                                          true,  RoadAccess.DESTINATION),
                Arguments.of("residential", "motor_vehicle=private",                                              true,  RoadAccess.PRIVATE),
                Arguments.of("residential", "motor_vehicle=delivery",                                             false, RoadAccess.DELIVERY),
                Arguments.of("pedestrian",  "motor_vehicle=private",                                              true,  RoadAccess.PRIVATE),
                Arguments.of("pedestrian",  "motor_vehicle:conditional=delivery @ (Mo-Fr 06:00-11:00)",           false, RoadAccess.DELIVERY)
        );
    }

    // ModeAccessParser with allow=delivery: delivery moves from restricted to intended
    static Stream<Arguments> allowDelivery() {
        return Stream.of(
                //        highway        extra tags                                                               access  road_access
                Arguments.of("residential", "",                                                                   true,  RoadAccess.YES),
                Arguments.of("residential", "motor_vehicle=delivery",                                             true,  RoadAccess.DELIVERY),
                Arguments.of("residential", "motor_vehicle=no",                                                   false, RoadAccess.NO),
                Arguments.of("residential", "motor_vehicle=no|motor_vehicle:conditional=delivery @ (06:00-11:00)", true,  RoadAccess.DELIVERY),
                Arguments.of("pedestrian",  "",                                                                   false, RoadAccess.YES),
                Arguments.of("pedestrian",  "motor_vehicle:conditional=delivery @ (Mo-Fr 06:00-11:00)",           true,  RoadAccess.DELIVERY),
                Arguments.of("pedestrian",  "motor_vehicle=destination",                                          true,  RoadAccess.DESTINATION)
        );
    }

    @ParameterizedTest(name = "default: highway={0} {1} -> car_access={2}, road_access={3}")
    @MethodSource("defaultConfig")
    void testDefault(String highway, String extraTags, boolean expectedAccess, RoadAccess expectedRoadAccess) {
        Fixture f = new Fixture();
        TagParser accessParser = new CarAccessParser(f.carAccessEnc, f.roundaboutEnc,
                new PMap(), OSMRoadAccessParser.toOSMRestrictions(TransportationMode.CAR));
        f.check(accessParser, highway, extraTags, expectedAccess, expectedRoadAccess);
    }

    @ParameterizedTest(name = "block_private=false: highway={0} {1} -> car_access={2}, road_access={3}")
    @MethodSource("blockPrivateFalse")
    void testBlockPrivateFalse(String highway, String extraTags, boolean expectedAccess, RoadAccess expectedRoadAccess) {
        Fixture f = new Fixture();
        TagParser accessParser = new CarAccessParser(f.carAccessEnc, f.roundaboutEnc,
                new PMap("block_private=false"), OSMRoadAccessParser.toOSMRestrictions(TransportationMode.CAR));
        f.check(accessParser, highway, extraTags, expectedAccess, expectedRoadAccess);
    }

    @ParameterizedTest(name = "allow=delivery: highway={0} {1} -> car_access={2}, road_access={3}")
    @MethodSource("allowDelivery")
    void testAllowDelivery(String highway, String extraTags, boolean expectedAccess, RoadAccess expectedRoadAccess) {
        Fixture f = new Fixture();
        TagParser accessParser = new ModeAccessParser(OSMRoadAccessParser.toOSMRestrictions(TransportationMode.CAR),
                f.carAccessEnc, true, f.roundaboutEnc, Set.of("delivery"), Set.of());
        f.check(accessParser, highway, extraTags, expectedAccess, expectedRoadAccess);
    }

    private static class Fixture {
        final BooleanEncodedValue carAccessEnc = VehicleAccess.create("car");
        final BooleanEncodedValue roundaboutEnc = Roundabout.create();
        final EnumEncodedValue<RoadAccess> roadAccessEnc = RoadAccess.create();

        Fixture() {
            new EncodingManager.Builder().add(carAccessEnc).add(roundaboutEnc).add(roadAccessEnc).build();
        }

        void check(TagParser accessParser, String highway, String extraTags,
                    boolean expectedAccess, RoadAccess expectedRoadAccess) {
            OSMRoadAccessParser<RoadAccess> roadAccessParser = OSMRoadAccessParser.forCar(roadAccessEnc);

            ReaderWay way = new ReaderWay(1L);
            way.setTag("highway", highway);
            for (String tag : extraTags.split("\\|")) {
                if (tag.isEmpty()) continue;
                String[] kv = tag.split("=", 2);
                way.setTag(kv[0].trim(), kv[1].trim());
            }

            ArrayEdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
            int edgeId = 0;
            IntsRef relFlags = new IntsRef(1);
            accessParser.handleWayTags(edgeId, edgeIntAccess, way, relFlags);
            roadAccessParser.handleWayTags(edgeId, edgeIntAccess, way, relFlags);

            assertEquals(expectedAccess, carAccessEnc.getBool(false, edgeId, edgeIntAccess), "car_access");
            assertEquals(expectedRoadAccess, roadAccessEnc.getEnum(false, edgeId, edgeIntAccess), "road_access");
        }
    }
}
