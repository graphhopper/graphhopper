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

    public double getSpeed(ReaderWay way) {
        // todo: We currently face twe problems related to ferry speeds:
        //       1) We cannot account for waiting times for short ferries (makes the ferry speed slower than we can store)
        //       2) When the ferry speed is larger than the maximum speed of the encoder (like 15km/h for foot) the
        //          ferry speed will be faster than we can store.
        //       Maybe we could add an additional encoded value that stores the ferry waiting time
        //       (maybe just short/medium/long or so) and add a few more possible speed values to account for high ferry
        //       speeds for the slow vehicles like foot.

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
            double wayDistance = way.getTag("way_distance", Double.NaN);
            if (Double.isNaN(wayDistance))
                // we don't know the distance of this way either. probably a broken way at the border of the map.
                return unknownSpeed;
            else if (wayDistance < 300)
                // use the slowest possible speed for very short ferries
                return minSpeed;
            else {
                LOGGER.warn("Long ferry OSM way without valid duration: " + way.getId() + ", distance: " + wayDistance);
                // todonow: use the unknown speed for now, but check how many such ferries actually exist and maybe use a
                //          faster speed for longer ones
                return unknownSpeed;
            }
        }
    }
}
