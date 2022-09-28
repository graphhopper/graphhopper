package com.graphhopper.routing.util;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.util.parsers.TagParser;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.PointList;

public class CurvatureCalculator implements TagParser {

    private final DecimalEncodedValue curvatureEnc;

    public CurvatureCalculator(DecimalEncodedValue curvatureEnc) {
        this.curvatureEnc = curvatureEnc;
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way, IntsRef relationFlags) {
        PointList pointList = way.getTag("point_list", null);
        double edgeDistance = way.getTag("edge_distance", null);
        if (pointList != null && !pointList.isEmpty()) {
            double beeline = DistanceCalcEarth.DIST_EARTH.calcDist(pointList.getLat(0), pointList.getLon(0),
                    pointList.getLat(pointList.size() - 1), pointList.getLon(pointList.size() - 1));
            // use quadratic value as otherwise values are too close (0.9 - 1.0)
            double curvature = beeline / edgeDistance;
            curvatureEnc.setDecimal(false, edgeFlags, Math.max(curvatureEnc.getMinStorableDecimal(), Math.min(curvatureEnc.getMaxStorableDecimal(),
                    curvature * curvature)));
        } else
            curvatureEnc.setDecimal(false, edgeFlags, 1.0);
        return edgeFlags;
    }
}
