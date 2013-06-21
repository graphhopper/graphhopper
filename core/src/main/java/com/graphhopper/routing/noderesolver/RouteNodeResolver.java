package com.graphhopper.routing.noderesolver;

import com.graphhopper.storage.index.LocationIDResult;

/**
 * Find the best node to route out of a given edge
 * 
 * @author NG
 */
public interface RouteNodeResolver 
{

	/**
	 * Find the most suitable node for the routing from/to an edge. If the
	 * closest edge is null, then the closest node is returned with the
	 * potential error this involves. Otherwise edge direction will be taken
	 * into account.
	 * 
	 * @param closestLocation
	 *            path start location
	 * @param lat
	 *            searched point's latitude
	 * @param lon
	 *            searched point's longitude
	 * @param isOrigin
	 *            true if the node to find will the the origin of the road (false =
	 *            destination)
	 * @param sameEdge
	 * 			  true is the from edge = the end edge
	 * @return
	 */
	public int findRouteNode( LocationIDResult closestLocation, double lat,
			double lon, boolean isOrigin, boolean sameEdge );
}
