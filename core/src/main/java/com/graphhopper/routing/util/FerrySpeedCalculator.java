package com.graphhopper.routing.util;

import com.graphhopper.reader.ReaderWay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FerrySpeedCalculator {

    private static final Logger LOGGER = LoggerFactory.getLogger(FerrySpeedCalculator.class);
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
        // During the reader process we have added the artificial "point_list" tag and converted the duration tag to
        // an artificial tag called "duration:seconds"
        double distanceInKm = way.getTag("road_distance", -1.0) / 1000;
        if (distanceInKm < 0)
            throw new IllegalStateException("The artificial 'road_distance' tag is missing for way: " + way.getId());
        Long duration = way.getTag("duration:seconds", 0L);
        // seconds to hours
        double durationInHours = duration / 60d / 60d;
        if (durationInHours > 0) {
            // If duration is available we can calculate the speed. We make it slower by a factor of 1.4 to account
            // for waiting time.
            double calculatedTripSpeed = distanceInKm / durationInHours / 1.4;
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
                    LOGGER.warn("Unrealistic long duration ignored in way with way ID=" + way.getId() + " : Duration tag value="
                            + way.getTag("duration") + " (=" + Math.round(duration / 60d) + " minutes)");
                durationInHours = 0;
            }
        }

        if (durationInHours == 0) {
            if (distanceInKm <= 0.3)
                return speedFactor / 2;
            // unknown speed -> put penalty on ferry transport
            return unknownSpeed;
        } else if (durationInHours > 1) {
            // todo: can never happen because the road_distance is always there... -> longSpeed and shortSpeed are never used!
            // lengthy ferries should be faster than short trip ferry
            return longSpeed;
        } else {
            return shortSpeed;
        }
    }
}
