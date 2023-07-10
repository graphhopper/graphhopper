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
    // This value determines the maximal possible speed of any road regardless of the maxspeed value
    // lower values allow more compact representation of the routing graph
    protected final double maxPossibleSpeed;
    protected final Set<String> ferries = new HashSet<>(FERRIES);
    protected final FerrySpeedCalculator ferrySpeedCalc;

    protected AbstractAverageSpeedParser(DecimalEncodedValue speedEnc, double maxPossibleSpeed) {
        this.maxPossibleSpeed = maxPossibleSpeed;
        this.avgSpeedEnc = speedEnc;

        ferrySpeedCalc = new FerrySpeedCalculator(speedEnc.getSmallestNonZeroValue(), maxPossibleSpeed, 5);
    }

    public double getMaxSpeed() {
        return maxPossibleSpeed;
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

    protected void setSpeed(boolean reverse, int edgeId, EdgeIntAccess edgeIntAccess, double speed) {
        // special case when speed is non-zero but would be "rounded down" to 0 due to the low precision of the EncodedValue
        if (speed > 0.1 && speed < avgSpeedEnc.getSmallestNonZeroValue())
            speed = avgSpeedEnc.getSmallestNonZeroValue();
        if (speed < avgSpeedEnc.getSmallestNonZeroValue()) {
            avgSpeedEnc.setDecimal(reverse, edgeId, edgeIntAccess, 0);
        } else {
            avgSpeedEnc.setDecimal(reverse, edgeId, edgeIntAccess, Math.min(speed, getMaxSpeed()));
        }
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
