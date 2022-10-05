package com.graphhopper.reader.osm;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.util.OSMParsers;

public abstract class WayHandlerBase {
	protected final OSMParsers osmParsers;
    /**
     * All OSM ways that are not accepted here and all nodes 
     * that are not referenced by any such way will be ignored.
     */
	public WayHandlerBase(OSMParsers osmParsers) {
		this.osmParsers = osmParsers;
	}
	
    protected boolean acceptWay(ReaderWay way) {
        // ignore broken geometry
        if (way.getNodes().size() < 2)
            return false;

        // ignore multipolygon geometry
        if (!way.hasTags())
            return false;

        return osmParsers.acceptWay(way);
    }

}
