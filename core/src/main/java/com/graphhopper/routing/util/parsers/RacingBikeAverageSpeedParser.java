package com.graphhopper.routing.util.parsers;

import com.graphhopper.routing.ev.*;

public class RacingBikeAverageSpeedParser extends BikeCommonAverageSpeedParser {

    public RacingBikeAverageSpeedParser(EncodedValueLookup lookup) {
        this(lookup.getDecimalEncodedValue(VehicleSpeed.key("racingbike")),
                lookup.getEnumEncodedValue(Smoothness.KEY, Smoothness.class),
                lookup.getDecimalEncodedValue(FerrySpeed.KEY));
    }

    protected RacingBikeAverageSpeedParser(DecimalEncodedValue speedEnc, EnumEncodedValue<Smoothness> smoothnessEnc, DecimalEncodedValue ferrySpeedEnc) {
        super(speedEnc, smoothnessEnc, ferrySpeedEnc);

        setTrackTypeSpeed("grade1", 20); // paved
        setTrackTypeSpeed("grade2", 10); // now unpaved ...
        setTrackTypeSpeed("grade3", PUSHING_SECTION_SPEED);
        setTrackTypeSpeed("grade4", PUSHING_SECTION_SPEED);
        setTrackTypeSpeed("grade5", PUSHING_SECTION_SPEED);

        setSurfaceSpeed("paved", 20);
        setSurfaceSpeed("asphalt", 20);
        setSurfaceSpeed("concrete", 20);
        setSurfaceSpeed("concrete:lanes", 16);
        setSurfaceSpeed("concrete:plates", 16);
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
        setSurfaceSpeed("pebblestone", PUSHING_SECTION_SPEED);
        setSurfaceSpeed("salt", MIN_SPEED);
        setSurfaceSpeed("sand", MIN_SPEED);
        setSurfaceSpeed("wood", MIN_SPEED);

        setHighwaySpeed("track", MIN_SPEED); // assume unpaved

        setHighwaySpeed("trunk", 20);
        setHighwaySpeed("trunk_link", 20);
        setHighwaySpeed("primary", 20);
        setHighwaySpeed("primary_link", 20);
        setHighwaySpeed("secondary", 20);
        setHighwaySpeed("secondary_link", 20);
        setHighwaySpeed("tertiary", 20);
        setHighwaySpeed("tertiary_link", 20);

        // overwite map from BikeCommon
        setSmoothnessSpeedFactor(Smoothness.EXCELLENT, 1.2d);
        setSmoothnessSpeedFactor(Smoothness.VERY_BAD, 0.1);
        setSmoothnessSpeedFactor(Smoothness.HORRIBLE, 0.1);
        setSmoothnessSpeedFactor(Smoothness.VERY_HORRIBLE, 0.1);
    }
}
