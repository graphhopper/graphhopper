package com.graphhopper.routing.util;

import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.FetchMode;
import com.graphhopper.util.PointList;

public class CurvatureCalculator {

    private final DecimalEncodedValue curvatureEnc;

    public CurvatureCalculator(DecimalEncodedValue curvatureEnc) {
        this.curvatureEnc = curvatureEnc;
    }

    public void execute(Graph graph) {
        AllEdgesIterator iter = graph.getAllEdges();
        while (iter.next()) {
            PointList pointList = iter.fetchWayGeometry(FetchMode.ALL);
            double edgeDistance = iter.getDistance();
            if (!pointList.isEmpty() && edgeDistance > 0) {
                double lat0 = pointList.getLat(0), lon0 = pointList.getLon(0);
                double latEnd = pointList.getLat(pointList.size() - 1), lonEnd = pointList.getLon(pointList.size() - 1);
                double beeline = pointList.is3D() ? DistanceCalcEarth.DIST_EARTH.calcDist3D(lat0, lon0, pointList.getEle(0), latEnd, lonEnd, pointList.getEle(pointList.size() - 1))
                        : DistanceCalcEarth.DIST_EARTH.calcDist(lat0, lon0, latEnd, lonEnd);
                // For now keep the formula simple. Maybe later use quadratic value as it might improve the "resolution"
                double curvature = beeline / edgeDistance;
                iter.set(curvatureEnc, Math.max(curvatureEnc.getMinStorableDecimal(), Math.min(curvatureEnc.getMaxStorableDecimal(),
                        curvature)));
            } else {
                iter.set(curvatureEnc, 1.0);
            }
        }
    }
}
