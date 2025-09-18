package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.FerrySpeedCalculator;
import com.graphhopper.storage.IntsRef;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.graphhopper.routing.util.parsers.AbstractAccessParser.INTENDED;

public abstract class BikeCommonAverageSpeedParser extends AbstractAverageSpeedParser implements TagParser {

    private static final Set<String> CYCLEWAY_KEYS = Set.of("cycleway", "cycleway:left", "cycleway:both", "cycleway:right");
    protected static final int PUSHING_SECTION_SPEED = 4;
    protected static final int MIN_SPEED = 2;
    private final Map<String, Integer> trackTypeSpeeds = new HashMap<>();
    private final Map<String, Integer> surfaceSpeeds = new HashMap<>();
    private final Map<Smoothness, Double> smoothnessFactor = new HashMap<>();
    private final Map<String, Integer> highwaySpeeds = new HashMap<>();
    private final EnumEncodedValue<Smoothness> smoothnessEnc;
    private final Set<String> restrictedValues = Set.of("no", "agricultural", "forestry", "restricted", "military", "emergency", "private", "permit");
    private final EnumEncodedValue<RouteNetwork> bikeRouteEnc;

    protected BikeCommonAverageSpeedParser(DecimalEncodedValue speedEnc,
                                           EnumEncodedValue<Smoothness> smoothnessEnc,
                                           DecimalEncodedValue ferrySpeedEnc,
                                           EnumEncodedValue<RouteNetwork> bikeRouteEnc) {
        super(speedEnc, ferrySpeedEnc);
        this.bikeRouteEnc = bikeRouteEnc;
        this.smoothnessEnc = smoothnessEnc;

        setTrackTypeSpeed("grade1", 18); // paved
        setTrackTypeSpeed("grade2", 12); // now unpaved ...
        setTrackTypeSpeed("grade3", 8);
        setTrackTypeSpeed("grade4", 6);
        setTrackTypeSpeed("grade5", 4); // like sand/grass

        setSurfaceSpeed("paved", 18);
        setSurfaceSpeed("asphalt", 18);
        setSurfaceSpeed("cobblestone", 8);
        setSurfaceSpeed("cobblestone:flattened", 10);
        setSurfaceSpeed("sett", 10);
        setSurfaceSpeed("concrete", 18);
        setSurfaceSpeed("concrete:lanes", 16);
        setSurfaceSpeed("concrete:plates", 16);
        setSurfaceSpeed("paving_stones", 16);
        setSurfaceSpeed("paving_stones:30", 16);
        setSurfaceSpeed("unpaved", 12);
        setSurfaceSpeed("compacted", 14);
        setSurfaceSpeed("dirt", 10);
        setSurfaceSpeed("earth", 12);
        setSurfaceSpeed("fine_gravel", 14); // should not be faster than compacted
        setSurfaceSpeed("grass", 8);
        setSurfaceSpeed("grass_paver", 8);
        setSurfaceSpeed("gravel", 12);
        setSurfaceSpeed("ground", 12);
        setSurfaceSpeed("ice", MIN_SPEED);
        setSurfaceSpeed("metal", 10);
        setSurfaceSpeed("mud", 10);
        setSurfaceSpeed("pebblestone", 14);
        setSurfaceSpeed("salt", PUSHING_SECTION_SPEED);
        setSurfaceSpeed("sand", PUSHING_SECTION_SPEED);
        setSurfaceSpeed("wood", PUSHING_SECTION_SPEED);

        setHighwaySpeed("living_street", 6);
        setHighwaySpeed("steps", MIN_SPEED);

        setHighwaySpeed("cycleway", 18);
        setHighwaySpeed("path", 6);
        setHighwaySpeed("footway", 6);
        setHighwaySpeed("platform", 6);
        setHighwaySpeed("pedestrian", 6);
        setHighwaySpeed("bridleway", 6);
        setHighwaySpeed("track", 12);
        setHighwaySpeed("service", 12);
        setHighwaySpeed("residential", 18);
        // no other highway applies:
        setHighwaySpeed("unclassified", 16);
        // unknown road:
        setHighwaySpeed("road", 12);

        setHighwaySpeed("trunk", 18);
        setHighwaySpeed("trunk_link", 18);
        setHighwaySpeed("primary", 18);
        setHighwaySpeed("primary_link", 18);
        setHighwaySpeed("secondary", 18);
        setHighwaySpeed("secondary_link", 18);
        setHighwaySpeed("tertiary", 18);
        setHighwaySpeed("tertiary_link", 18);

        // special case see tests and #191
        setHighwaySpeed("motorway", 18);
        setHighwaySpeed("motorway_link", 18);

        // note that this factor reduces the speed but only until MIN_SPEED
        setSmoothnessSpeedFactor(Smoothness.MISSING, 1.0d);
        setSmoothnessSpeedFactor(Smoothness.OTHER, 0.7d);
        setSmoothnessSpeedFactor(Smoothness.EXCELLENT, 1.1d);
        setSmoothnessSpeedFactor(Smoothness.GOOD, 1.0d);
        setSmoothnessSpeedFactor(Smoothness.INTERMEDIATE, 0.9d);
        setSmoothnessSpeedFactor(Smoothness.BAD, 0.7d);
        setSmoothnessSpeedFactor(Smoothness.VERY_BAD, 0.4d);
        setSmoothnessSpeedFactor(Smoothness.HORRIBLE, 0.3d);
        setSmoothnessSpeedFactor(Smoothness.VERY_HORRIBLE, 0.1d);
        setSmoothnessSpeedFactor(Smoothness.IMPASSABLE, 0);
    }

    /**
     * @param way   needed to retrieve tags
     * @param speed speed guessed e.g. from the road type or other tags
     * @return The assumed average speed.
     */
    double applyMaxSpeed(ReaderWay way, double speed, boolean bwd) {
        double maxSpeed = OSMMaxSpeedParser.parseMaxSpeed(way, bwd);
        // We strictly obey speed limits, see #600
        return Math.min(speed, maxSpeed);
    }

    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way) {
        throw new IllegalArgumentException("use handleWayTags with relationFlags");
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        String highwayValue = way.getTag("highway", "");
        if (highwayValue.isEmpty()) {
            if (FerrySpeedCalculator.isFerry(way)) {
                double ferrySpeed = FerrySpeedCalculator.minmax(ferrySpeedEnc.getDecimal(false, edgeId, edgeIntAccess), avgSpeedEnc);
                setSpeed(false, edgeId, edgeIntAccess, ferrySpeed);
                if (avgSpeedEnc.isStoreTwoDirections())
                    setSpeed(true, edgeId, edgeIntAccess, ferrySpeed);
            }
            if (!way.hasTag("railway", "platform") && !way.hasTag("man_made", "pier"))
                return;
        }

        double speed = highwaySpeeds.getOrDefault(highwayValue, PUSHING_SECTION_SPEED);
        String surfaceValue = way.getTag("surface");
        String trackTypeValue = way.getTag("tracktype");
        boolean pushingRestriction = Arrays.stream(way.getTag("vehicle", "").split(";")).anyMatch(restrictedValues::contains);
        Integer surfaceSpeed = surfaceSpeeds.get(surfaceValue);
        Integer trackTypeSpeed = trackTypeSpeeds.get(trackTypeValue);
        if (trackTypeSpeed != null)
            surfaceSpeed = surfaceSpeed == null ? trackTypeSpeed : Math.min(surfaceSpeed, trackTypeSpeed);

        if (way.hasTag("surface") && surfaceSpeed == null
                || way.hasTag("bicycle", "dismount")
                || way.hasTag("railway", "platform")
                || pushingRestriction && !way.hasTag("bicycle", INTENDED)
                || way.hasTag("service")) {
            speed = PUSHING_SECTION_SPEED;

        } else {

            boolean bikeDesignated = isDesignated(way) || RouteNetwork.MISSING != bikeRouteEnc.getEnum(false, edgeId, edgeIntAccess);
            boolean bikeAllowed = way.hasTag("bicycle", "yes") || bikeDesignated;
            boolean isRacingBike = this instanceof RacingBikeAverageSpeedParser;

            // increase speed for certain highway tags because of a good surface or a more permissive bike access
            switch (highwayValue) {
                case "track": // assume bicycle=yes even if no bicycle tag
                    bikeAllowed = bikeAllowed || !way.hasTag("bicycle");

                case "path", "bridleway":
                    if (surfaceSpeed != null)
                        speed = Math.max(speed, bikeAllowed ? surfaceSpeed : surfaceSpeed * 0.7);
                    else if (isRacingBike)
                        break; // no speed increase if no surface tag

                case "footway", "pedestrian", "platform":
                    // speed increase if bike allowed or even designated
                    if (bikeDesignated)
                        speed = Math.max(speed, highwaySpeeds.get("cycleway"));
                    else if (bikeAllowed)
                        speed = Math.max(speed, 12);
            }
        }

        // speed reduction if bad surface
        if (surfaceSpeed != null)
            speed = Math.min(surfaceSpeed, speed);

        Smoothness smoothness = smoothnessEnc.getEnum(false, edgeId, edgeIntAccess);
        speed = Math.max(MIN_SPEED, smoothnessFactor.get(smoothness) * speed);
        setSpeed(false, edgeId, edgeIntAccess, applyMaxSpeed(way, speed, false));
        if (avgSpeedEnc.isStoreTwoDirections())
            setSpeed(true, edgeId, edgeIntAccess, applyMaxSpeed(way, speed, true));
    }

    private boolean isDesignated(ReaderWay way) {
        return way.hasTag("bicycle", "designated") || way.hasTag("bicycle", "official") || way.hasTag("segregated", "yes")
                || CYCLEWAY_KEYS.stream().anyMatch(k -> way.getTag(k, "").equals("track"));
    }

    void setHighwaySpeed(String highway, int speed) {
        highwaySpeeds.put(highway, speed);
    }

    void setTrackTypeSpeed(String tracktype, int speed) {
        trackTypeSpeeds.put(tracktype, speed);
    }

    void setSurfaceSpeed(String surface, int speed) {
        surfaceSpeeds.put(surface, speed);
    }

    void setSmoothnessSpeedFactor(Smoothness smoothness, double speedfactor) {
        smoothnessFactor.put(smoothness, speedfactor);
    }
}
