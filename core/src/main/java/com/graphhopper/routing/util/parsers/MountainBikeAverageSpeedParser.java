package com.graphhopper.routing.util.parsers;

import com.graphhopper.routing.ev.*;

public class MountainBikeAverageSpeedParser extends BikeCommonAverageSpeedParser {

    public MountainBikeAverageSpeedParser(EncodedValueLookup lookup) {
        this(lookup.getDecimalEncodedValue(VehicleSpeed.key("mtb")),
                lookup.getEnumEncodedValue(Smoothness.KEY, Smoothness.class),
                lookup.getDecimalEncodedValue(FerrySpeed.KEY),
                lookup.getEnumEncodedValue(BikeNetwork.KEY, RouteNetwork.class));
    }

    protected MountainBikeAverageSpeedParser(DecimalEncodedValue speedEnc,
                                             EnumEncodedValue<Smoothness> smoothnessEnc,
                                             DecimalEncodedValue ferrySpeedEnc,
                                             EnumEncodedValue<RouteNetwork> bikeRouteEnc) {
        super(speedEnc, smoothnessEnc, ferrySpeedEnc, bikeRouteEnc);
        setTrackTypeSpeed("grade1", 18); // paved
        setTrackTypeSpeed("grade2", 16); // now unpaved ...
        setTrackTypeSpeed("grade3", 12);
        setTrackTypeSpeed("grade4", 8);
        setTrackTypeSpeed("grade5", 6); // like sand/grass

        setSurfaceSpeed("dirt", 14);
        setSurfaceSpeed("earth", 14);
        setSurfaceSpeed("fine_gravel", 16);
        setSurfaceSpeed("grass", 14);
        setSurfaceSpeed("ground", 14);
        setSurfaceSpeed("grass_paver", 14);
        setSurfaceSpeed("compacted", 16);

        setSurfaceSpeed("ice", MIN_SPEED);

        setSurfaceSpeed("salt", 6);
        setSurfaceSpeed("sand", 6);
        setSurfaceSpeed("wood", 6);
    }
}
