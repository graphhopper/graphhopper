package com.graphhopper.routing.util.parsers;

import com.graphhopper.routing.ev.*;
import com.graphhopper.util.PMap;

public class MountainBikeAverageSpeedParser extends BikeCommonAverageSpeedParser {

    public MountainBikeAverageSpeedParser(EncodedValueLookup lookup, PMap properties) {
        this(lookup.getDecimalEncodedValue(properties.getString("name", VehicleSpeed.key("mtb"))),
                lookup.getEnumEncodedValue(Smoothness.KEY, Smoothness.class));
    }

    protected MountainBikeAverageSpeedParser(DecimalEncodedValue speedEnc, EnumEncodedValue<Smoothness> smoothnessEnc) {
        super(speedEnc, smoothnessEnc);
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
        setSurfaceSpeed("gravel", 16);
        setSurfaceSpeed("ground", 16);
        setSurfaceSpeed("ice", MIN_SPEED);
        setSurfaceSpeed("metal", 10);
        setSurfaceSpeed("mud", 12);
        setSurfaceSpeed("salt", 12);
        setSurfaceSpeed("sand", 10);
        setSurfaceSpeed("wood", 10);

        setHighwaySpeed("living_street", PUSHING_SECTION_SPEED);
        setHighwaySpeed("steps", PUSHING_SECTION_SPEED);

        setHighwaySpeed("path", 18);
        setHighwaySpeed("footway", PUSHING_SECTION_SPEED);
        setHighwaySpeed("pedestrian", PUSHING_SECTION_SPEED);
        setHighwaySpeed("track", 18);
        setHighwaySpeed("residential", 16);
    }
}
