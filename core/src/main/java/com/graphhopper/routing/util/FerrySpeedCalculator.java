package com.graphhopper.routing.util;

import com.graphhopper.reader.ReaderWay;

public class FerrySpeedCalculator {
    private final double unknownSpeed, minSpeed, maxSpeed;

    public FerrySpeedCalculator(double minSpeed, double maxSpeed, double unknownSpeed) {
        this.minSpeed = minSpeed;
        this.maxSpeed = maxSpeed;
        this.unknownSpeed = unknownSpeed;
    }

    public double getSpeed(ReaderWay way) {
        // todo: We currently face two problems related to ferry speeds:
        //       1) We cannot account for waiting times for short ferries (when we do the ferry speed is slower than the slowest we can store)
        //       2) When the ferry speed is larger than the maximum speed of the encoder (like 15km/h for foot) the
        //          ferry speed will be faster than what we can store.

        // OSMReader adds the artificial 'speed_from_duration', 'duration:seconds' and 'way_distance' tags that we can
        // use to set the ferry speed. Otherwise we need to use fallback values.
        double speedInKmPerHour = way.getTag("speed_from_duration", Double.NaN);
        if (!Double.isNaN(speedInKmPerHour)) {
            // we reduce the speed to account for waiting time (we increase the duration by 40%)
            double speedWithWaitingTime = speedInKmPerHour / 1.4;
            return Math.round(Math.max(minSpeed, Math.min(speedWithWaitingTime, maxSpeed)));
        } else {
            // we have no speed value to work with because there was no valid duration tag.
            // we have to take a guess based on the distance.
            double wayDistance = way.getTag("edge_distance", Double.NaN);
            if (Double.isNaN(wayDistance))
                throw new IllegalStateException("No 'edge_distance' set for edge created for way: " + way.getId());
            else if (wayDistance < 500)
                // Use the slowest possible speed for very short ferries. Note that sometimes these aren't really ferries
                // that take you from one harbour to another, but rather ways that only represent the beginning of a
                // longer ferry connection and that are used by multiple different connections, like here: https://www.openstreetmap.org/way/107913687
                // It should not matter much which speed we use in this case, so we have no special handling for these.
                return minSpeed;
            else {
                // todo: distinguish speed based on the distance of the ferry, see #2532
                return unknownSpeed;
            }
        }
    }
}
