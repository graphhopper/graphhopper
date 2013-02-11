package com.graphhopper.search;

import com.graphhopper.util.shapes.GHInfoPoint;
import com.graphhopper.util.shapes.GHPoint;
import java.util.List;

/**
 * Simple interface to convert between places and points.
 *
 * @author Peter Karich
 */
public interface Geocoding {

    List<GHInfoPoint> search(String... place);

    List<GHInfoPoint> reverse(GHPoint... points);
}
