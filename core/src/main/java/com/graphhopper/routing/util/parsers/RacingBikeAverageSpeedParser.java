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

        setTrackTypeSpeed("grade1", 28); // paved
        setTrackTypeSpeed("grade2", 10); // now unpaved ...
        setTrackTypeSpeed("grade3", PUSHING_SECTION_SPEED);
        setTrackTypeSpeed("grade4", PUSHING_SECTION_SPEED);
        setTrackTypeSpeed("grade5", PUSHING_SECTION_SPEED);

        setSurfaceSpeed("paved", 28);
        setSurfaceSpeed("asphalt", 28);
        setSurfaceSpeed("concrete", 28);
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

        setHighwaySpeed("trunk", 28);
        setHighwaySpeed("trunk_link", 28);
        setHighwaySpeed("primary", 28);
        setHighwaySpeed("primary_link", 28);
        setHighwaySpeed("secondary", 28);
        setHighwaySpeed("secondary_link", 28);
        setHighwaySpeed("tertiary", 28);
        setHighwaySpeed("tertiary_link", 28);
        setHighwaySpeed("cycleway", 28);
        setHighwaySpeed("residential", 28);
        setHighwaySpeed("unclassified", 28);

        // overwrite map from BikeCommon
        setSmoothnessSpeedFactor(Smoothness.EXCELLENT, 1.0d);
        setSmoothnessSpeedFactor(Smoothness.VERY_BAD, 0.1);
        setSmoothnessSpeedFactor(Smoothness.HORRIBLE, 0.1);
        setSmoothnessSpeedFactor(Smoothness.VERY_HORRIBLE, 0.1);
    }
}
