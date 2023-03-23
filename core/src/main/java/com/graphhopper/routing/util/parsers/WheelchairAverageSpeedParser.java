package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.ev.VehicleSpeed;
import com.graphhopper.util.PMap;
import com.graphhopper.util.PointList;

public class WheelchairAverageSpeedParser extends FootAverageSpeedParser {
    private final int maxInclinePercent = 6;

    public WheelchairAverageSpeedParser(EncodedValueLookup lookup, PMap properties) {
        this(lookup.getDecimalEncodedValue(properties.getString("name", VehicleSpeed.key("wheelchair"))));
    }

    protected WheelchairAverageSpeedParser(DecimalEncodedValue speedEnc) {
        super(speedEnc);

        safeHighwayTags.add("footway");
        safeHighwayTags.add("pedestrian");
        safeHighwayTags.add("living_street");
        safeHighwayTags.add("residential");
        safeHighwayTags.add("service");
        safeHighwayTags.add("platform");

        safeHighwayTags.remove("steps");
        safeHighwayTags.remove("track");

        allowedHighwayTags.clear();
        allowedHighwayTags.addAll(safeHighwayTags);
        allowedHighwayTags.addAll(avoidHighwayTags);
        allowedHighwayTags.add("cycleway");
        allowedHighwayTags.add("unclassified");
        allowedHighwayTags.add("road");
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way) {
        String highwayValue = way.getTag("highway");
        if (highwayValue == null) {
            if (way.hasTag("route", ferries)) {
                double ferrySpeed = ferrySpeedCalc.getSpeed(way);
                setSpeed(edgeId, edgeIntAccess, true, true, ferrySpeed);
            }
            if (!way.hasTag("railway", "platform") && !way.hasTag("man_made", "pier"))
                return;
        }

        setSpeed(edgeId, edgeIntAccess, true, true, MEAN_SPEED);
        applyWayTags(way, edgeId, edgeIntAccess);
    }

    /**
     * Calculate slopes from elevation data and set speed according to that. In-/declines between smallInclinePercent
     * and maxInclinePercent will reduce speed to SLOW_SPEED. In-/declines above maxInclinePercent will result in zero
     * speed.
     */
    public void applyWayTags(ReaderWay way, int edgeId, EdgeIntAccess edgeIntAccess) {
        PointList pl = way.getTag("point_list", null);
        if (pl == null)
            throw new IllegalArgumentException("The artificial point_list tag is missing");
        if (!way.hasTag("edge_distance"))
            throw new IllegalArgumentException("The artificial edge_distance tag is missing");
        double fullDist2D = way.getTag("edge_distance", 0d);
        if (Double.isInfinite(fullDist2D))
            throw new IllegalStateException("Infinite distance should not happen due to #435. way ID=" + way.getId());

        // skip elevation data adjustment for too short segments, TODO use custom model for elevation handling
        if (fullDist2D < 20 || !pl.is3D())
            return;

        double prevEle = pl.getEle(0);
        double eleDelta = pl.getEle(pl.size() - 1) - prevEle;
        double elePercent = eleDelta / fullDist2D * 100;
        int smallInclinePercent = 3;
        double fwdSpeed = 0, bwdSpeed = 0;
        if (elePercent > smallInclinePercent && elePercent < maxInclinePercent) {
            fwdSpeed = SLOW_SPEED;
            bwdSpeed = MEAN_SPEED;
        } else if (elePercent < -smallInclinePercent && elePercent > -maxInclinePercent) {
            fwdSpeed = MEAN_SPEED;
            bwdSpeed = SLOW_SPEED;
        } else if (elePercent > maxInclinePercent || elePercent < -maxInclinePercent) {
            // it can be problematic to exclude roads due to potential bad elevation data (e.g.delta for narrow nodes could be too high)
            // so exclude only when we are certain
            if (fullDist2D > 50) {
                setSpeed(edgeId, edgeIntAccess, true, false, 0);
                setSpeed(edgeId, edgeIntAccess, true, true, 0);
                return;
            }

            fwdSpeed = SLOW_SPEED;
            bwdSpeed = SLOW_SPEED;
        }

        if (fwdSpeed > 0) setSpeed(edgeId, edgeIntAccess, true, false, fwdSpeed);
        if (bwdSpeed > 0) setSpeed(edgeId, edgeIntAccess, false, true, bwdSpeed);
    }
}
