package com.graphhopper.search;

import com.graphhopper.util.shapes.GHPlace;
import java.util.List;

/**
 * Interface to convert from points to place names or node ids.
 * <p/>
 * @author Peter Karich
 */
public interface ReverseGeocoding
{
    /**
     * Tries to retrieve a locational string from the specified points (list of lat,lon).
     */
    List<GHPlace> point2name( GHPlace... points );
}
