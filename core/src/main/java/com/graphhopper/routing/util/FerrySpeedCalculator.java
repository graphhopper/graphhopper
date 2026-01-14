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
        // OSMReader adds the artificial 'duration_in_seconds' and 'way_distance' tags that we can
        // use to set the ferry speed. Otherwise we need to use fallback values.
        long durationInSeconds = way.getTag("duration_in_seconds", 0L);
        if (durationInSeconds > 0) {
            double waitTime = 30 * 60;
            double wayDistance = way.getTag("way_distance", Double.NaN);
            return Math.round(wayDistance / 1000 / ((durationInSeconds + waitTime) / 60.0 / 60.0));
        } else {
            double edgeDistance = way.getTag("edge_distance", Double.NaN);
            int shuttleFactor = way.hasTag("route", "shuttle_train") ? 2 : 1;
            if (Double.isNaN(edgeDistance))
                throw new IllegalStateException("No 'edge_distance' set for edge created for way: " + way.getId());
            // When we have no speed value to work with we have to take a guess based on the distance.
            if (edgeDistance < 1000) {
                // Use the slowest possible speed for very short ferries. Note that sometimes these aren't really ferries
                // that take you from one harbour to another, but rather ways that only represent the beginning of a
                // longer ferry connection and that are used by multiple different connections, like here: https://www.openstreetmap.org/way/107913687
                // It should not matter much which speed we use in this case, so we have no special handling for these.
                return 5 * shuttleFactor;
            } else if (edgeDistance < 30_000) {
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
