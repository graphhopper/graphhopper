package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.util.FerrySpeedCalculator;
import com.graphhopper.routing.util.parsers.helpers.OSMValueExtractor;
import com.graphhopper.storage.IntsRef;

import java.util.HashSet;
import java.util.Set;

import static com.graphhopper.routing.util.parsers.AbstractAccessParser.FERRIES;

public abstract class AbstractAverageSpeedParser implements TagParser {
    // http://wiki.openstreetmap.org/wiki/Mapfeatures#Barrier
    protected final DecimalEncodedValue avgSpeedEnc;
    protected final Set<String> ferries = new HashSet<>(FERRIES);
    protected final FerrySpeedCalculator ferrySpeedCalc;

    protected AbstractAverageSpeedParser(DecimalEncodedValue speedEnc) {
        this.avgSpeedEnc = speedEnc;

        ferrySpeedCalc = new FerrySpeedCalculator(speedEnc.getSmallestNonZeroValue(), speedEnc.getMaxStorableDecimal(), 5);
    }

    /**
     * @return {@link Double#NaN} if no maxspeed found
     */
    public static double getMaxSpeed(ReaderWay way, boolean bwd) {
        double maxSpeed = OSMValueExtractor.stringToKmh(way.getTag("maxspeed"));
        double directedMaxSpeed = OSMValueExtractor.stringToKmh(way.getTag(bwd ? "maxspeed:backward" : "maxspeed:forward"));
        return isValidSpeed(directedMaxSpeed) ? directedMaxSpeed : maxSpeed;
    }

    /**
     * @return <i>true</i> if the given speed is not {@link Double#NaN}
     */
    protected static boolean isValidSpeed(double speed) {
        return !Double.isNaN(speed);
    }

    public final DecimalEncodedValue getAverageSpeedEnc() {
        return avgSpeedEnc;
    }

    public final String getName() {
        return avgSpeedEnc.getName();
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        handleWayTags(edgeId, edgeIntAccess, way);
    }

    public abstract void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way);

    @Override
    public String toString() {
        return getName();
    }
}
