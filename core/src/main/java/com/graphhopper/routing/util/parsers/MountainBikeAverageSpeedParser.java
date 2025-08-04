package com.graphhopper.routing.util.parsers;

import com.graphhopper.routing.ev.*;

public class MountainBikeAverageSpeedParser extends BikeCommonAverageSpeedParser {

    public MountainBikeAverageSpeedParser(EncodedValueLookup lookup) {
        this(lookup.getDecimalEncodedValue(VehicleSpeed.key("mtb")),
                lookup.getEnumEncodedValue(Smoothness.KEY, Smoothness.class),
                lookup.getDecimalEncodedValue(FerrySpeed.KEY));
    }

    protected MountainBikeAverageSpeedParser(DecimalEncodedValue speedEnc, EnumEncodedValue<Smoothness> smoothnessEnc, DecimalEncodedValue ferrySpeedEnc) {
        super(speedEnc, smoothnessEnc, ferrySpeedEnc);
        setTrackTypeSpeed("grade1", 18); // paved
        setTrackTypeSpeed("grade2", 16); // now unpaved ...
        setTrackTypeSpeed("grade3", 12);
        setTrackTypeSpeed("grade4", 8);
        setTrackTypeSpeed("grade5", 6); // like sand/grass

        setSurfaceSpeed("concrete", 14);
        setSurfaceSpeed("concrete:lanes", 16);
        setSurfaceSpeed("concrete:plates", 16);
        setSurfaceSpeed("dirt", 14);
        setSurfaceSpeed("earth", 14);
        setSurfaceSpeed("fine_gravel", 18);
        setSurfaceSpeed("grass", 14);
        setSurfaceSpeed("grass_paver", 14);
        setSurfaceSpeed("compacted", 16);
        setSurfaceSpeed("gravel", 16);
        setSurfaceSpeed("ground", 16);
        setSurfaceSpeed("ice", MIN_SPEED);
        setSurfaceSpeed("metal", 10);
        setSurfaceSpeed("mud", 12);
        setSurfaceSpeed("salt", 12);
        setSurfaceSpeed("sand", 10);
        setSurfaceSpeed("wood", 10);

        setHighwaySpeed("path", 18);
        setHighwaySpeed("footway", PUSHING_SECTION_SPEED);
        setHighwaySpeed("track", 18);
        setHighwaySpeed("residential", 16);
    }
}
