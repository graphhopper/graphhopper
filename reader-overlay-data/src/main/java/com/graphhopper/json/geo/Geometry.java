package com.graphhopper.json.geo;

import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPoint;

/**
 *
 * @author Peter Karich
 */
public interface Geometry
{
    String getType();

    boolean isPoint();

    GHPoint asPoint();

    boolean isPointList();

    PointList asPointList();
}
