package com.graphhopper.routing.util;

import com.graphhopper.reader.ReaderWay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FerrySpeedCalculator {

    private static final Logger LOGGER = LoggerFactory.getLogger(FerrySpeedCalculator.class);
    private final double unknownSpeed, minSpeed, maxSpeed;

    public FerrySpeedCalculator(double minSpeed, double maxSpeed, double unknownSpeed) {
        this.minSpeed = minSpeed;
        this.maxSpeed = maxSpeed;
        this.unknownSpeed = unknownSpeed;
    }

    /**
     * Special handling for ferry ways.
     */
    public double getSpeed(ReaderWay way) {
        // todo: we should re-consider whether we should deal with ferries by determining (only) a speed at all.
        //       for example this way we cannot account for waiting times for short ferries (speed is too low) or
        //       'fast' ferries that exceed the max speed for e.g. the foot encoder. Maybe we could add an additional
        //       encoded value that stores the ferry waiting time (maybe just short/medium/long or so) and add a few
        //       more possible speed values to account for high ferry speeds for the slow vehicles like foot.

        // During the reader process we have added the artificial "road_distance" tag and converted the duration tag to
        // an artificial tag called "duration:seconds"
        double distanceInKm = way.getTag("road_distance", -1.0) / 1000;
        if (distanceInKm < 0)
            throw new IllegalStateException("The artificial 'road_distance' tag is missing for way: " + way.getId());
        Long duration = way.getTag("duration:seconds", 0L);
        double durationInHours = duration / 60d / 60d;
        if (durationInHours > 0) {
            // If duration is available we can calculate the speed. We make it slower by a factor of 1.4 to account
            // for waiting time.
            double calculatedTripSpeed = distanceInKm / durationInHours / 1.4;
            // Plausibility check especially for the case of wrongly used PxM format with the intention to
            // specify the duration in minutes, but actually using months
            if (calculatedTripSpeed > 0.01d) {
                if (calculatedTripSpeed > maxSpeed)
                    return maxSpeed;
                // If the speed is lower than the speed we can store, we have to set it to the minSpeed, but > 0
                if (Math.round(calculatedTripSpeed) < minSpeed)
                    return minSpeed;
                return Math.round(calculatedTripSpeed);
            } else {
                LOGGER.warn("Unrealistic long duration ignored in way with way ID=" + way.getId() + " : Duration tag value="
                        + way.getTag("duration") + " (=" + Math.round(duration / 60d) + " minutes)");
            }
        }

        // duration was not present or calculated trip speed was too small
        if (distanceInKm <= 0.3)
            return minSpeed;
        else
            // unknown speed -> put penalty on ferry transport
            return unknownSpeed;
    }
}
