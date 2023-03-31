package com.graphhopper.reader.osm.pointlist;

import com.graphhopper.util.PointList;
import com.graphhopper.util.RamerDouglasPeucker;

public class RamerDouglasPeuckerProcessor implements PointListProcessor {
    final private double maxWayPointDistance;
    private final RamerDouglasPeucker algorithm;

    public RamerDouglasPeuckerProcessor(double maxWayPointDistance, double elevationMaxWayPointDistance) {
        this.maxWayPointDistance = maxWayPointDistance;
        algorithm = new RamerDouglasPeucker().setMaxDistance(maxWayPointDistance).setElevationMaxDistance(elevationMaxWayPointDistance);
    }

    @Override
    public PointList process(PointList pointList) {
        if (maxWayPointDistance > 0 && pointList.size() > 2) {
            algorithm.simplify(pointList);
        }
        return pointList;
    }
}
