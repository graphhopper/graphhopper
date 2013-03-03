package com.graphhopper.search;

import com.graphhopper.util.shapes.GHPlace;
import java.util.List;

/**
 * Interface to convert from place names to points.
 *
 * @author Peter Karich
 */
public interface Geocoding {

    /**
     * Returns a list of matching points for the specified place query string.
     */
    List<GHPlace> name2point(GHPlace... place);
}
