package com.graphhopper.routing.weighting;

import com.graphhopper.routing.ev.*;

/**
 * The rolling resistance of a surface relative to asphalt, i.e. the factor for the crr of the bike (on asphalt).
 * The speed reduction of a bad surface is then a result of the power balance and not a separate assumption,
 * see BikeClimbSpeedTable. The values follow from the speeds of the former BikeAverageSpeedParser for 100W on
 * the flat (18.7km/h on asphalt), e.g. its 12km/h for gravel require 2.8 times the rolling resistance of asphalt
 * and the 8km/h for cobblestone 4.6 times, which agrees with rolling resistance measurements. The worst of
 * surface, track type and smoothness counts, like the parser picked the lowest speed.
 */
public class BikeRollingResistance {

    // pushing through sand or mud is not modelled as rolling, but the power balance yields the walking speed here
    public static final double PUSHING = 14;

    /**
     * @param bikeNetwork a track or path in a bike route network without surface tag is assumed to be paved
     */
    public static double factor(RoadClass roadClass, Surface surface, TrackType trackType, Smoothness smoothness, RouteNetwork bikeNetwork) {
        double factor = surface != Surface.MISSING ? surfaceFactor(surface) : bikeNetwork != RouteNetwork.MISSING ? 1 : defaultFactor(roadClass);
        return Math.max(factor, Math.max(trackTypeFactor(trackType), smoothnessFactor(smoothness)));
    }

    /**
     * @return the factor for roads without a surface tag, i.e. tracks are assumed to be gravel and paths dirt
     */
    static double defaultFactor(RoadClass roadClass) {
        return switch (roadClass) {
            case TRACK -> 2.8;
            case PATH, BRIDLEWAY -> 3.6;
            default -> 1;
        };
    }

    static double surfaceFactor(Surface surface) {
        return switch (surface) {
            case MISSING, PAVED, ASPHALT, CONCRETE -> 1;
            case PAVING_STONES, WOOD -> 1.6;
            case COMPACTED, FINE_GRAVEL -> 2.2;
            case GRAVEL, UNPAVED -> 2.8;
            case GROUND, DIRT -> 3.6;
            case COBBLESTONE, GRASS -> 4.6;
            case SAND -> PUSHING;
            case OTHER -> 2.8;
        };
    }

    static double trackTypeFactor(TrackType trackType) {
        return switch (trackType) {
            case MISSING, GRADE1 -> 1;
            case GRADE2 -> 2.2;
            case GRADE3 -> 2.8;
            case GRADE4 -> 3.6;
            case GRADE5 -> PUSHING;
        };
    }

    /**
     * The former speed factors of the parser (e.g. 0.7 for bad) are inverted: at the low speeds of a rough
     * surface the rolling resistance dominates the power balance, so the speed is inversely proportional to it.
     */
    static double smoothnessFactor(Smoothness smoothness) {
        return switch (smoothness) {
            case MISSING, EXCELLENT, GOOD -> 1;
            case INTERMEDIATE -> 1.1;
            case BAD, OTHER -> 1.4;
            case VERY_BAD -> 2.5;
            case HORRIBLE -> 3.3;
            case VERY_HORRIBLE -> 10;
            case IMPASSABLE -> PUSHING;
        };
    }
}
