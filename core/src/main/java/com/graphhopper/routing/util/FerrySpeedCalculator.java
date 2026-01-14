package com.graphhopper.routing.util;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.util.parsers.TagParser;
import com.graphhopper.storage.IntsRef;

public class FerrySpeedCalculator implements TagParser {
    private final DecimalEncodedValue ferrySpeedEnc;

    public FerrySpeedCalculator(DecimalEncodedValue ferrySpeedEnc) {
        this.ferrySpeedEnc = ferrySpeedEnc;
    }

    public static boolean isFerry(ReaderWay way) {
        return way.hasTag("route", "ferry") && !way.hasTag("ferry", "no") ||
                // TODO shuttle_train is sometimes also used in relations, e.g. https://www.openstreetmap.org/relation/1932780
                way.hasTag("route", "shuttle_train") && !way.hasTag("shuttle_train", "no");
    }

    static double getSpeed(ReaderWay way) {
        // todo: We currently face two problems related to ferry speeds:
        //       1) We cannot account for waiting times for short ferries (when we do the ferry speed is slower than the slowest we can store)
        //       2) When the ferry speed is larger than the maximum speed of the encoder (like 15km/h for foot) the
        //          ferry speed will be faster than what we can store.

        // OSMReader adds the artificial 'speed_from_duration' and 'way_distance' tags that we can
        // use to set the ferry speed. Otherwise we need to use fallback values.
        double speedInKmPerHour = way.getTag("speed_from_duration", Double.NaN);
        if (!Double.isNaN(speedInKmPerHour)) {
            // we reduce the speed to account for waiting time (we increase the duration by 40%)
            return Math.round(speedInKmPerHour / 1.4);
        } else {
            int shuttleFactor = way.hasTag("route", "shuttle_train") ? 2 : 1;
            // we have no speed value to work with because there was no valid duration tag.
            // we have to take a guess based on the distance.
            double wayDistance = way.getTag("edge_distance", Double.NaN);
            if (Double.isNaN(wayDistance)) {
                throw new IllegalStateException("No 'edge_distance' set for edge created for way: " + way.getId());
            } else if (wayDistance < 1000) {
                // Use the slowest possible speed for very short ferries. Note that sometimes these aren't really ferries
                // that take you from one harbour to another, but rather ways that only represent the beginning of a
                // longer ferry connection and that are used by multiple different connections, like here: https://www.openstreetmap.org/way/107913687
                // It should not matter much which speed we use in this case, so we have no special handling for these.
                return 5 * shuttleFactor;
            } else if (wayDistance < 30_000) {
                return 15 * shuttleFactor;
            } else {
                return 30 * shuttleFactor;
            }
        }
    }

    public static double minmax(double speed, DecimalEncodedValue avgSpeedEnc) {
        return Math.max(avgSpeedEnc.getSmallestNonZeroValue(), Math.min(speed, avgSpeedEnc.getMaxStorableDecimal()));
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        if (isFerry(way)) {
            double ferrySpeed = minmax(getSpeed(way), ferrySpeedEnc);
            ferrySpeedEnc.setDecimal(false, edgeId, edgeIntAccess, ferrySpeed);
        }
    }
}
