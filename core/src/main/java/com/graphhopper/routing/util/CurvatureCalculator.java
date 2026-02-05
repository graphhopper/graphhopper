package com.graphhopper.routing.util;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EdgeIntAccess;
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
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        PointList pointList = way.getTag("point_list", null);
        Double edgeDistance = way.getTag("edge_distance", null);
        if (pointList != null && edgeDistance != null && !pointList.isEmpty()) {
            double lat0 = pointList.getLat(0), lon0 = pointList.getLon(0);
            double latEnd = pointList.getLat(pointList.size() - 1), lonEnd = pointList.getLon(pointList.size() - 1);
            double beeline = pointList.is3D() ? DistanceCalcEarth.DIST_EARTH.calcDist3D(lat0, lon0, pointList.getEle(0), latEnd, lonEnd, pointList.getEle(pointList.size() - 1))
                    : DistanceCalcEarth.DIST_EARTH.calcDist(lat0, lon0, latEnd, lonEnd);
            // For now keep the formula simple. Maybe later use quadratic value as it might improve the "resolution"
            double curvature = beeline / edgeDistance;
            curvatureEnc.setDecimal(false, edgeId, edgeIntAccess, Math.max(curvatureEnc.getMinStorableDecimal(), Math.min(curvatureEnc.getMaxStorableDecimal(),
                    curvature)));
        } else {
            curvatureEnc.setDecimal(false, edgeId, edgeIntAccess, 1.0);
        }
    }
}
