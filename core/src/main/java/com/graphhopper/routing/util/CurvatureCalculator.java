package com.graphhopper.routing.util;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.IntAccess;
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
    public void handleWayTags(int edgeId, IntAccess intAccess, ReaderWay way, IntsRef relationFlags) {
        PointList pointList = way.getTag("point_list", null);
        Double edgeDistance = way.getTag("edge_distance", null);
        if (pointList != null && edgeDistance != null && !pointList.isEmpty()) {
            double beeline = DistanceCalcEarth.DIST_EARTH.calcDist(pointList.getLat(0), pointList.getLon(0),
                    pointList.getLat(pointList.size() - 1), pointList.getLon(pointList.size() - 1));
            // For now keep the formula simple. Maybe later use quadratic value as it might improve the "resolution"
            double curvature = beeline / edgeDistance;
            curvatureEnc.setDecimal(false, edgeId, intAccess, Math.max(curvatureEnc.getMinStorableDecimal(), Math.min(curvatureEnc.getMaxStorableDecimal(),
                    curvature)));
        } else {
            curvatureEnc.setDecimal(false, edgeId, intAccess, 1.0);
        }
    }
}
