package com.graphhopper.routing.util.parsers;

import com.graphhopper.routing.ev.*;

public class RacingBikeAverageSpeedParser extends BikeCommonAverageSpeedParser {

    public RacingBikeAverageSpeedParser(EncodedValueLookup lookup) {
        this(lookup.getDecimalEncodedValue(VehicleSpeed.key("racingbike")),
                lookup.getEnumEncodedValue(Smoothness.KEY, Smoothness.class),
                lookup.getEnumEncodedValue(BikeNetwork.KEY, RouteNetwork.class));
    }

    protected RacingBikeAverageSpeedParser(DecimalEncodedValue speedEnc,
                                           EnumEncodedValue<Smoothness> smoothnessEnc,
                                           EnumEncodedValue<RouteNetwork> bikeRouteEnc) {
        super(speedEnc, smoothnessEnc, bikeRouteEnc);

        setTrackTypeSpeed("grade1", 30); // paved
        setTrackTypeSpeed("grade2", 10); // now unpaved ...
        setTrackTypeSpeed("grade3", PUSHING_SECTION_SPEED);
        setTrackTypeSpeed("grade4", PUSHING_SECTION_SPEED);
        setTrackTypeSpeed("grade5", PUSHING_SECTION_SPEED);

        setSurfaceSpeed("paved", 30);
        setSurfaceSpeed("asphalt", 30);
        setSurfaceSpeed("concrete", 30);
        setSurfaceSpeed("concrete:lanes", 20);
        setSurfaceSpeed("concrete:plates", 20);
        setSurfaceSpeed("unpaved", MIN_SPEED);
        setSurfaceSpeed("compacted", MIN_SPEED);
        setSurfaceSpeed("dirt", MIN_SPEED);
        setSurfaceSpeed("earth", MIN_SPEED);
        setSurfaceSpeed("fine_gravel", PUSHING_SECTION_SPEED);
        setSurfaceSpeed("grass", MIN_SPEED);
        setSurfaceSpeed("grass_paver", MIN_SPEED);
        setSurfaceSpeed("gravel", MIN_SPEED);
        setSurfaceSpeed("ground", MIN_SPEED);
        setSurfaceSpeed("ice", MIN_SPEED);
        setSurfaceSpeed("metal", MIN_SPEED);
        setSurfaceSpeed("mud", MIN_SPEED);
        setSurfaceSpeed("clay", MIN_SPEED);
        setSurfaceSpeed("laterite", MIN_SPEED);
        setSurfaceSpeed("pebblestone", PUSHING_SECTION_SPEED);
        setSurfaceSpeed("salt", MIN_SPEED);
        setSurfaceSpeed("sand", MIN_SPEED);
        setHighwaySpeed("track", MIN_SPEED); // assume unpaved

        setHighwaySpeed("trunk", 30);
        setHighwaySpeed("trunk_link", 30);
        setHighwaySpeed("primary", 30);
        setHighwaySpeed("primary_link", 30);
        setHighwaySpeed("secondary", 30);
        setHighwaySpeed("secondary_link", 30);
        setHighwaySpeed("tertiary", 30);
        setHighwaySpeed("tertiary_link", 30);
        setHighwaySpeed("cycleway", 30);
        setHighwaySpeed("residential", 30);
        setHighwaySpeed("unclassified", 30);

        // overwrite map from BikeCommon
        setSmoothnessSpeedFactor(Smoothness.EXCELLENT, 1.0d);
        setSmoothnessSpeedFactor(Smoothness.VERY_BAD, 0.1);
        setSmoothnessSpeedFactor(Smoothness.HORRIBLE, 0.1);
        setSmoothnessSpeedFactor(Smoothness.VERY_HORRIBLE, 0.1);
    }
}
