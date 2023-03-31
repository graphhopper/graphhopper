package com.graphhopper.reader.osm.pointlist;

import com.graphhopper.util.PointList;
import org.codehaus.commons.nullanalysis.NotNull;

/**
 * Processor that takes the point list of an edge and may alter it
 */
public interface PointListProcessor {
    @NotNull PointList process(@NotNull PointList pointList);
}
