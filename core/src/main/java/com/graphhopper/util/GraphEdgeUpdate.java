package com.graphhopper.util;

import com.graphhopper.GraphHopper;
import com.graphhopper.reader.datexupdates.LatLongMetaData;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.shapes.GHPoint;

public class GraphEdgeUpdate {

	public static void updateEdge(GraphHopper graph,
			LatLongMetaData latLonMetadata) {
		LocationIndex locationIndex = graph.getLocationIndex();
		FlagEncoder encoder = graph.getEncodingManager().getEncoder("CAR");
		GHPoint location = latLonMetadata.getLocation();
		QueryResult findClosest = locationIndex.findClosest(location.getLat(), location.getLon(), EdgeFilter.ALL_EDGES);
		EdgeIteratorState closestEdge = findClosest.getClosestEdge();
		long existingFlags = closestEdge.getFlags();
		System.err.println(closestEdge.getName());
		System.err.println(closestEdge.getDistance());
		closestEdge.setFlags(encoder.setSpeed(existingFlags, Long.parseLong((String)latLonMetadata.getMetaData("speed"))));
		System.err.println(encoder.getSpeed(closestEdge.getFlags()));
	}
}
