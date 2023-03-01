package com.graphhopper.routing.util;

import com.graphhopper.reader.ReaderWay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FerrySpeedCalculator {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final double speedFactor;
    private final double unknownSpeed, longSpeed, shortSpeed, maxSpeed;

    public FerrySpeedCalculator(double speedFactor, double maxSpeed, double longSpeed, double shortSpeed, double unknownSpeed) {
        this.speedFactor = speedFactor;
        this.unknownSpeed = unknownSpeed;
        this.longSpeed = longSpeed;
        this.shortSpeed = shortSpeed;
        this.maxSpeed = maxSpeed;
    }

    /**
     * Special handling for ferry ways.
     */
    public double getSpeed(ReaderWay way) {
        long duration = 0;

        try {
            // During the reader process we have converted the duration value into a artificial tag called "duration:seconds".
            duration = Long.parseLong(way.getTag("duration:seconds"));
        } catch (Exception ex) {
        }
        // seconds to hours
        double durationInHours = duration / 60d / 60d;
        // Check if our graphhopper specific artificially created estimated_distance way tag is present
        // OSM MOD start
        Number estimatedLength = way.getTag("exact_distance", null);
        if (estimatedLength == null)
            estimatedLength = way.getTag("estimated_distance", null);
        // OSM MOD end
        if (durationInHours > 0)
            try {
                if (estimatedLength != null) {
                    double estimatedLengthInKm = estimatedLength.doubleValue() / 1000;
                    // If duration AND distance is available we can calculate the speed more precisely
                    // and set both speed to the same value. Factor 1.4 slower because of waiting time!
                    double calculatedTripSpeed = estimatedLengthInKm / durationInHours / 1.4;
                    // Plausibility check especially for the case of wrongly used PxM format with the intention to
                    // specify the duration in minutes, but actually using months
                    if (calculatedTripSpeed > 0.01d) {
                        if (calculatedTripSpeed > maxSpeed) {
                            return maxSpeed;
                        }
                        // If the speed is lower than the speed we can store, we have to set it to the minSpeed, but > 0
                        if (Math.round(calculatedTripSpeed) < speedFactor / 2) {
                            return speedFactor / 2;
                        }

                        return Math.round(calculatedTripSpeed);
                    } else {
                        long lastId = way.getNodes().isEmpty() ? -1 : way.getNodes().get(way.getNodes().size() - 1);
                        long firstId = way.getNodes().isEmpty() ? -1 : way.getNodes().get(0);
                        if (firstId != lastId)
                            logger.warn("Unrealistic long duration ignored in way with way ID=" + way.getId() + " : Duration tag value="
                                    + way.getTag("duration") + " (=" + Math.round(duration / 60d) + " minutes)");
                        durationInHours = 0;
                    }
                }
            } catch (Exception ex) {
            }

        if (durationInHours == 0) {
            if (estimatedLength != null && estimatedLength.doubleValue() <= 300)
                return speedFactor / 2;
            // unknown speed -> put penalty on ferry transport
            return unknownSpeed;
        } else if (durationInHours > 1) {
            // lengthy ferries should be faster than short trip ferry
            return longSpeed;
        } else {
            return shortSpeed;
        }
    }
}
