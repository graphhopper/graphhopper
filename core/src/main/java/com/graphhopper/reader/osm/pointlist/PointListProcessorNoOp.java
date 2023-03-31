package com.graphhopper.reader.osm.pointlist;

import com.graphhopper.util.PointList;

public class PointListProcessorNoOp implements PointListProcessor {
    @Override
    public PointList process(PointList pointList) {
        return pointList;
    }
}
