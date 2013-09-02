package com.graphhopper.routing.noderesolver;

import com.graphhopper.routing.util.AbstractFlagEncoder;
import com.graphhopper.storage.index.LocationIDResult;
import com.graphhopper.util.EdgeBase;

/**
 * Basic implementation of the RouteNodeResolver. This implementation takes care
 * of the direction of the edge but is not optimal for bidirectional edges as it
 * only returns the closest routable node on the edge which is not always the
 * most suitable
 * 
 * @author NG
 * 
 */
public class DummyNodeResolver implements RouteNodeResolver
{

	/** the encoder to read edge flags in node resolution */
	private final AbstractFlagEncoder edgeEncoder;

	/**
	 * @param edgenEncoder
	 *            the encoder to read edge flags in node resolution
	 */
	public DummyNodeResolver( AbstractFlagEncoder edgenEncoder )
	{
		this.edgeEncoder = edgenEncoder;
	}

	@Override
	public int findRouteNode( LocationIDResult closestLocation, double lat, double lon, boolean isOrigin, boolean sameEdge )
	{
		
		if (closestLocation.getClosestEdge() == null)
		{
			// edge is unknown : return the closest node
			return closestLocation.getClosestNode();
		}
		
		EdgeBase closestEdge = closestLocation.getClosestEdge();
		int flags = closestEdge.getFlags();
		if (edgeEncoder.isBackward(flags) && edgeEncoder.isForward(flags))
		{
			// Bidirectional edge
			return findRouteNodeBidirectional(closestLocation, lat, lon,
					isOrigin);
		} else
		{
			// one way
			// (routing from-to the same edge is an exception to the one way behavior)
			if (isOrigin ^ edgeEncoder.isForward(flags) ^ sameEdge)
			{
				// start on forward OR arrive on backward => Start node
				return closestEdge.getBaseNode();
			} else
			{
				// start on backward OR arrive on forward => End node
				return closestEdge.getAdjNode();
			}
		}
	}

	/**
	 * Find the most suitable node for the routing from/to a bidirectional edge.
	 * This implementation simply returns the closest tower node to the lat/lon
	 * but this is sub-optimal.
	 * 
	 * @param closestLocation
	 * @param lat
	 * @param lon
	 * @param isOrigin
	 * @return
	 */
	protected int findRouteNodeBidirectional( LocationIDResult closestLocation, double lat, double lon, boolean isOrigin )
	{
		return closestLocation.getClosestNode();
	}

}
