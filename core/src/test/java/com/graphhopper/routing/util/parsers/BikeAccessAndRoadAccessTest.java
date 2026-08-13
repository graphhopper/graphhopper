package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.IntsRef;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Combined test for bike_access and bike_road_access — companion to
 * {@link CarAccessAndRoadAccessTest}.
 * <p>
 * For the car/foot pair the boolean and the qualifier almost always agree. For bikes they
 * deliberately don't: {@link BikeCommonAccessParser} is permissive on
 * pedestrian/footway/bridleway and on highways with no explicit bicycle/access tag, and lets
 * {@code bike_road_access} carry the qualification. The bundled bike profile then gates routing
 * on both — the custom model rule {@code bike_road_access == NO -> multiply_by 0} is what turns
 * the qualifier into an effective block. Several rows below would route as accessible on
 * {@code bike_access} alone but are correctly closed once {@code bike_road_access} is consulted.
 */
class BikeAccessAndRoadAccessTest {

    static Stream<Arguments> defaultConfig() {
        return Stream.of(
                //                  highway        extra tags                                                  access  bike_road_access

                // Plain accessible highways with no bike-specific tag — qualifier is MISSING
                // (the encoded default), bike_access is permissive.
                Arguments.of("residential",  "",                                                                true,  BikeRoadAccess.MISSING),
                Arguments.of("unclassified", "",                                                                true,  BikeRoadAccess.MISSING),
                Arguments.of("tertiary",     "",                                                                true,  BikeRoadAccess.MISSING),
                Arguments.of("track",        "",                                                                true,  BikeRoadAccess.MISSING),

                // Explicit bicycle=* tags propagate to both signals.
                Arguments.of("residential",  "bicycle=yes",                                                     true,  BikeRoadAccess.YES),
                Arguments.of("residential",  "bicycle=designated",                                              true,  BikeRoadAccess.DESIGNATED),
                Arguments.of("residential",  "bicycle=dismount",                                                true,  BikeRoadAccess.DISMOUNT),
                Arguments.of("residential",  "bicycle=destination",                                             true,  BikeRoadAccess.DESTINATION),
                Arguments.of("residential",  "bicycle=no",                                                      false, BikeRoadAccess.NO),
                Arguments.of("residential",  "bicycle=private",                                                 false, BikeRoadAccess.PRIVATE),

                // Highway types with implied bicycle defaults from the OSM wiki.
                Arguments.of("cycleway",     "",                                                                true,  BikeRoadAccess.DESIGNATED),
                Arguments.of("path",         "",                                                                true,  BikeRoadAccess.YES),
                Arguments.of("bridleway",    "",                                                                true,  BikeRoadAccess.YES),

                // Pedestrian/footway: implied default is bicycle=dismount (you can push the
                // bike). bike_access stays true and bike_road_access becomes DISMOUNT — the
                // bike profile's bike_road_access==NO block does NOT fire here. An *explicit*
                // bicycle=no still resolves to NO (treated as a hard block today, even though
                // pushing might be physically possible — that's a separate inconsistency).
                Arguments.of("pedestrian",   "",                                                                true,  BikeRoadAccess.DISMOUNT),
                Arguments.of("footway",      "",                                                                true,  BikeRoadAccess.DISMOUNT),

                // Explicit bicycle tag overrides the implied default.
                Arguments.of("pedestrian",   "bicycle=yes",                                                     true,  BikeRoadAccess.YES),
                Arguments.of("pedestrian",   "bicycle=designated",                                              true,  BikeRoadAccess.DESIGNATED),
                Arguments.of("footway",      "bicycle=yes",                                                     true,  BikeRoadAccess.YES),
                Arguments.of("cycleway",     "bicycle=no",                                                      false, BikeRoadAccess.NO),

                // Motorway: bike_access is hard-blocked (special-cased in BikeCommonAccessParser),
                // bike_road_access=NO via implied default.
                Arguments.of("motorway",     "",                                                                false, BikeRoadAccess.NO),

                // corridor isn't in BikeCommonAccessParser.allowedHighways → bike_access=false.
                // Implied bicycle=no (not "dismount") so bike_road_access stays consistent with that.
                Arguments.of("corridor",     "",                                                                false, BikeRoadAccess.NO),

                // Generic vehicle/access tags. bike_access only checks "bicycle" and "access",
                // not "vehicle" — so vehicle=no leaves bike_access=true while bike_road_access=NO
                // (its restriction list is bicycle/vehicle/access). Same kind of mismatch as
                // pedestrian above. motor_vehicle is in neither list, so it's ignored entirely.
                Arguments.of("residential",  "vehicle=no",                                                      true,  BikeRoadAccess.NO),
                Arguments.of("residential",  "access=no",                                                       false, BikeRoadAccess.NO),
                Arguments.of("residential",  "motor_vehicle=no",                                                true,  BikeRoadAccess.MISSING),

                // Conditional restrictions. With no base, "least restrictive wins" against the
                // implicit base means a conditional alone never tightens things on a highway
                // that has no implied bicycle default. So a conditional bicycle=no on residential
                // is correctly ignored.
                Arguments.of("residential",  "bicycle:conditional=no @ (06:00-11:00)",                          true,  BikeRoadAccess.MISSING),
                Arguments.of("residential",  "bicycle:conditional=destination @ (06:00-11:00)",                 true,  BikeRoadAccess.MISSING),

                // On pedestrian/footway the implied base is DISMOUNT (ordinal 3). "Least
                // restrictive wins" against that, so a conditional DESIGNATED (2) or YES (1)
                // wins, but DESTINATION (4) is *more* restrictive and so the implied DISMOUNT
                // is kept.
                Arguments.of("pedestrian",   "bicycle:conditional=destination @ (06:00-11:00)",                 true,  BikeRoadAccess.DISMOUNT),
                Arguments.of("pedestrian",   "bicycle:conditional=designated @ (Mo-Fr 06:00-11:00)",            true,  BikeRoadAccess.DESIGNATED),
                Arguments.of("pedestrian",   "bicycle:conditional=yes @ (Mo-Fr 06:00-11:00)",                   true,  BikeRoadAccess.YES),

                // Explicit bicycle=no relaxed by a permissive temporal exception:
                // bike_access becomes true (per hasPermissiveTemporalRestriction) and the
                // qualifier reflects the relaxed value.
                Arguments.of("residential",  "bicycle=no|bicycle:conditional=yes @ (06:00-11:00)",              true,  BikeRoadAccess.YES),
                Arguments.of("residential",  "bicycle=no|bicycle:conditional=designated @ (06:00-11:00)",       true,  BikeRoadAccess.DESIGNATED),

                // Conditional with no temporal spec is not parsed as a conditional — base wins.
                Arguments.of("residential",  "bicycle=no|bicycle:conditional=yes @ pupsaffe",                   false, BikeRoadAccess.NO)
        );
    }

    @ParameterizedTest(name = "default: highway={0} {1} -> bike_access={2}, bike_road_access={3}")
    @MethodSource("defaultConfig")
    void testDefault(String highway, String extraTags, boolean expectedAccess, BikeRoadAccess expectedBikeRoadAccess) {
        Fixture f = new Fixture();
        TagParser accessParser = new BikeAccessParser(f.bikeAccessEnc, f.roundaboutEnc);
        f.check(accessParser, highway, extraTags, expectedAccess, expectedBikeRoadAccess);
    }

    private static class Fixture {
        final BooleanEncodedValue bikeAccessEnc = VehicleAccess.create("bike");
        final BooleanEncodedValue roundaboutEnc = Roundabout.create();
        final EnumEncodedValue<BikeRoadAccess> bikeRoadAccessEnc = BikeRoadAccess.create();

        Fixture() {
            new EncodingManager.Builder().add(bikeAccessEnc).add(roundaboutEnc).add(bikeRoadAccessEnc).build();
        }

        void check(TagParser accessParser, String highway, String extraTags,
                   boolean expectedAccess, BikeRoadAccess expectedBikeRoadAccess) {
            OSMRoadAccessParser<BikeRoadAccess> roadAccessParser = OSMRoadAccessParser.forBike(bikeRoadAccessEnc);

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

            assertEquals(expectedAccess, bikeAccessEnc.getBool(false, edgeId, edgeIntAccess), "bike_access");
            assertEquals(expectedBikeRoadAccess, bikeRoadAccessEnc.getEnum(false, edgeId, edgeIntAccess), "bike_road_access");
        }
    }
}
