/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor license 
 *  agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except 
 *  in compliance with the License. You may obtain a copy of the 
 *  License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.routing;

import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.PathSplitter;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.index.LocationIDResult;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.EdgeBase;
import com.graphhopper.util.NumHelper;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPlace;

/**
 * The PathFinisher is a tool meant to "finish" a path. Finishing a path
 * consists of improving the path around it's extremities as the underlying
 * routing algorithm only route graph nodes and not actual GPS locations. 
 * To improve these endings, the PathFinisher uses the start/end edges of the
 * base path, projects the GPS points on them and then adapt the path by
 * adding/removing the needed points to have a more accurate routing.<br> 
 * <br>
 * The path finisher can "finish" only one path it's meant to be disposed once the path
 * has been finished, do not try to reuse the same instance of PathFinisher to
 * finish two different path.
 * 
 * @author NG
 * 
 */
public class PathFinisher
{

	/** Start edge data */
	private final LocationIDResult fromLoc;
	/** To edge data */
	private final LocationIDResult toLoc;
	/** GPS from location */
	private final GHPlace from;
	/** GPS to location */
	private final GHPlace to;
	/** Base path between 2 graph's node */
	private final Path path;
	/** vehicle encoder used to compute the path */
	private final FlagEncoder vehicleEncoder;
	/** graph used to compute the path */
	private final Graph graph;
	
	private boolean scaleDistances = false;
	
	private final DistanceCalc calc = new DistanceCalc();
	
	private boolean finished = false;
	private double distance = -1D;
	private long time = -1L;
	private PointList points;
	private PathSplitter splitter = new PathSplitter();
	
	/**
	 * Initializes the PathFinisher.
	 * 
	 * @param fromLoc
	 *            location containing the "from" edge
	 * @param toLoc
	 *            location containing the "to" edge
	 * @param from
	 *            GPS location of the start point (true GPS point, not yet
	 *            projected on the edges)
	 * @param to
	 *            GPS location of the end point (true GPS point, not yet
	 *            projected on the edges)
	 * @param path
	 *            the base path between two nodes of the graph and that need to
	 *            be finished
	 * @param encoder
	 *            encoder used to create the path
	 * @param graph
	 *            graph used to create the path
	 */
	public PathFinisher( LocationIDResult fromLoc, LocationIDResult toLoc,
			GHPlace from, GHPlace to, Path path )
	{
		this.fromLoc = fromLoc;
		this.toLoc = toLoc;
		this.from = from;
		this.to = to;
		this.path = path;
		this.vehicleEncoder = path.encoder();
		this.graph = path.graph();
	}
	
	/**
	 * The path's distance enhanced with with ending edges' data
	 * @return
	 */
	public double getFinishedDistance()
	{
		if(!finished)
		{
			this.finishPath();
		}
		return this.distance;
	}
	
	/**
	 * The path's time enhanced with ending edges' data
	 * @return
	 */
	public long getFinishedTime()
	{
		if(!this.finished) {
			this.finishPath();
		}
		return this.time;
	}
	
	/**
	 * The path's geometry enhanced with ending edges' data
	 * @return
	 */
	public PointList getFinishedPointList()
	{
		if(!this.finished) {
			this.finishPath();
		}
		return this.points;
	}
	
	/**
	 * "Finish" the path fixing it's geometry, distance and time to better match
	 * the start/end GPS points.
	 */
	private void finishPath()
	{
		this.points = this.path.calcPoints();
		this.distance  = this.path.getDistance();
		this.time = this.path.getTime();
		
		if(this.points.getSize() == 0)
		{
			if(fromLoc.getClosestNode() == toLoc.getClosestNode())
			{
				this.buildEdgeToEdgePath();
			} else
			{
				return; // cannot finish path. throw an exception ?
			}
		}
		this.finishEdge(this.fromLoc, this.points, this.from, true);
		this.finishEdge(this.toLoc, this.points, this.to, false);
		
		finished = true;
	}
	
	/**
	 * Take an inaccurate path (between two graph's nodes) and cut/add the
	 * unnecessary nodes to start/stop at the closest points to GPS coordinates.
	 * The logic used is to first find the "cut node" as the closest point on
	 * the path to the GPS coordinate then remove all nodes that path and edges
	 * have in common to finally add only the necessary edge's nodes
	 * 
	 * @param loc :
	 *            edge used to finish the path
	 * @param pathPoints :
	 *            the points of the base path
	 * @param gpsStartPoint :
	 *            the GPS point to project on the path
	 * @param start :
	 *            true if passed edge is the beginning of the path<br>
	 *            false if it's the ending.
	 */
	private void finishEdge( LocationIDResult loc, PointList pathPoints, GHPlace gpsStartPoint, boolean start )
	{
		if (loc.getClosestEdge() == null)
		{
			// the starting edge is unknown, it's not possible to finish the path.
			return;
		}
		
		PointListIndex edgePointIdx = insertClosestGpsPoint(loc.getClosestEdge(), gpsStartPoint);
		
		// find points shared between edge and path
		PointList edgePoints = edgePointIdx.points;
		PointList newPoints = new PointList(pathPoints.getSize()+edgePoints.getSize()); // slightly too big
		double lat1, lon1;
		int idx1 = -1, idx2 = -1;
		// compute an index before/after which it's not necessary to compare edge's node.
		int skipIndex = (start ? edgePoints.getSize() : pathPoints.getSize() - edgePoints.getSize());
		boolean shared;
		double removedDist = 0;
		double prevLat = Double.NaN, prevLon = Double.NaN;
		for(int j=0 ; j < pathPoints.getSize() ; j++)
		{
			lat1 = pathPoints.getLatitude(j);
			lon1 = pathPoints.getLongitude(j);
			shared = false;

			// don't loop for every path's node but only on few first/last points
			if(start && j <= skipIndex || !start && j >= skipIndex)
			{
				for(int i=0 ; i < edgePoints.getSize() ; i++)
				{
					if(samePlace(lat1, lon1, edgePoints.getLatitude(i), edgePoints.getLongitude(i)))
					{
						// init first and last shared nodes
						if(idx1 < 0)
						{
							idx1 = i;
						} else
						{
							idx2 = i;
						}
						shared = true;
					}
				}
			}
			
			if (!shared)
			{
				newPoints.add(lat1, lon1);
			} else if (!Double.isNaN(prevLon))
			{
				// sum the removed distance
				removedDist += calc.calcDist(prevLat, prevLon, lat1, lon1);				
			}
			prevLat = lat1;
			prevLon = lon1;
		}
		
		// update distance/time data
		updateTimeDist(-removedDist, loc.getClosestEdge(), edgePoints);
		
		// invert for endings
		if(!start && idx2 >= 0)
		{
			int tmp = idx1;
			idx1 = idx2;
			idx2 = tmp;
		}
		
		// ensure cutIndex is on the edge (may be out if cut point is out of the edge)
		int cutIdx = Math.min(edgePointIdx.insertedPointIndex, edgePoints.getSize()-1);
		cutIdx = Math.max(0, cutIdx);
		
		double dist = 0;
		if(idx1 >= 0)
		{
			if(idx2 >= 0)
			{
				// edge and path have 2 common points
				// add all edge's points between cutPoint and idx2
				dist = appendPath(edgePoints, newPoints, cutIdx, idx2, start);
			} else
			{
				// edges and path are sharing only one point
				// add all edge's points between cutPoint and idx1
				dist = appendPath(edgePoints, newPoints, cutIdx, idx1, start);
			}
		} else
		{
			throw new IllegalArgumentException("Could not find common point between edge and path");
		}
		// update distance/time data
		updateTimeDist(dist, loc.getClosestEdge(), edgePoints);
	}
	
	/**
	 * Insert a point in the edge's geometry at the closest location to the GPS
	 * point in the edge's geometry. If the point can be located between two of
	 * the edge's nodes if will be inserted, otherwise the edges's geometry is
	 * returned unchanged. In case the point cannot be located on the edge the
	 * index returned will be either 0 or full edges' size <code>(
	 * {@link EdgeBase#wayGeometry()}.size()+2 )</code> depending whether
	 * the GPS point is closer to the start or to the end of the edge.
	 * 
	 * <pre>
	 *               P
	 *               |
	 *               |  
	 * A----B--------+-C--------D
	 * 
	 * A----B--------E-C--------D
	 * </pre>
	 * 
	 * @param edge
	 * @param gps
	 * @return
	 */
	private PointListIndex insertClosestGpsPoint( EdgeBase edge, GHPlace gps )
	{
		// add start/end edge's point
		PointList edgePoints = splitter.extractFullGeom(edge, graph);

		// find split index
		int index = splitter.findInsertIndex(edgePoints, gps.lat, gps.lon);
		PointList newList;
		if(index <= 0 || index >= edgePoints.getSize())
		{
			// cut point is outside the edge
			newList = edgePoints;
		} else
		{
			// insert cut point on the edge
			newList = new PointList(edgePoints.getSize()+1);
			for(int i=0 ; i<edgePoints.getSize() ; i++)
			{
				if(i == index)
				{
					// insert
					GHPlace intersectPoint = splitter.getCutPoint(gps, edgePoints.point(index-1), edgePoints.point(index));
					newList.add(intersectPoint.lat, intersectPoint.lon);
				}
				newList.add(edgePoints.getLatitude(i), edgePoints.getLongitude(i));
			}
		}
		
		return new PointListIndex(newList, index);
	}
	
	/**
	 * Adapt the finished distance and time by adding some distance to the base
	 * path
	 * 
	 * @param addedDist
	 *            distance to add (remove if negative) to the base path
	 * @param edge
	 *            edge from which the distance is add/removed
	 */
	private void updateTimeDist( double addedDist, EdgeBase edge, PointList fullEdge )
	{
		if(addedDist != 0)
		{
			if(scaleDistances)
			{
				double totPtDist = 0;
				for(int i=1 ; i < fullEdge.getSize() ; i++)
				{
					totPtDist += calc.calcDist(fullEdge.getLongitude(i-1), fullEdge.getLatitude(i-1), fullEdge.getLongitude(i), fullEdge.getLatitude(i)); 
				}
				addedDist = addedDist * edge.getDistance() / totPtDist;
			}
			this.distance += addedDist;
			this.time += (long) (addedDist * 3.6 / vehicleEncoder.getSpeed(edge.getFlags()));
		}
	}

	/**
	 * Builds a virtual path out of the FromEdge "AB" and the ToEdge "BC" when
	 * they are connected by (at least) one of their (tower) node "B". This
	 * allows to handle routing between two adjacent edges when fromNode =
	 * toNode or even routing in the same edge. This creates a full path as the
	 * sum of both edges, it will later need to be finished.
	 * 
	 * @return
	 */
	private void buildEdgeToEdgePath()
	{
		EdgeBase fromEdge = this.fromLoc.getClosestEdge();
		EdgeBase toEdge = this.toLoc.getClosestEdge();
		if (fromEdge == null || toEdge == null)
		{
			// cannot do anything.
			return;
		}
		PointList fromPts = splitter.extractFullGeom(fromEdge, graph);
		PointList toPts = splitter.extractFullGeom(toEdge, graph);
		
		// init path's data
		this.distance = fromEdge.getDistance();
		this.time = (long) (fromEdge.getDistance() * 3.6 / vehicleEncoder.getSpeed(fromEdge.getFlags()));
		
		// check which id the common node between from and to edges
		boolean revertFrom = false,
				revertTo = false;
		if ( fromEdge.getEdge() != toEdge.getEdge() )
		{
			if (fromEdge.getAdjNode() == toEdge.getAdjNode())
			{
				// case : A -->-- B --<-- C 
				revertTo = true;
			} else if (fromEdge.getBaseNode() == toEdge.getBaseNode())
			{
				// case A --<-- B -->-- C
				revertFrom = true;
			} else if (fromEdge.getBaseNode() == toEdge.getAdjNode())
			{
				// case A --<-- B --<-- C
				revertFrom = revertTo = true;
			} else if (fromEdge.getAdjNode() != toEdge.getBaseNode())
			{
				throw new IllegalArgumentException("From and To edges are not connected by their base/adj nodes");
			}
		} else
		{
			// routing from - to the same edge
			this.points = fromPts;
			return;
		}
		
		PointList path = new PointList(fromPts.getSize() + toPts.getSize());
		// add fromEdge's points to path
		int i, len = fromPts.getSize();
		for ( i=0 ; i < len ; i++ )
		{
			if (!revertFrom)
			{
				path.add(fromPts.getLatitude(i), fromPts.getLongitude(i));
			} else
			{
				path.add(fromPts.getLatitude(len-1-i), fromPts.getLongitude(len-1-i));
			}
		}
		
		// add toEdge's points to path
		len = toPts.getSize();
		for(i=1 ; i < len ; i++)
		{// do not add 1st point, as it's the shared point between the two edges
			if(!revertTo) {
				path.add(toPts.getLatitude(i), toPts.getLongitude(i));
			} else {
				path.add(toPts.getLatitude(len-1-i), toPts.getLongitude(len-1-i));
			}
		}
		// compute distance
		this.distance += toEdge.getDistance();
		this.time += (long) (toEdge.getDistance() * 3.6 / vehicleEncoder.getSpeed(toEdge.getFlags()));
		this.points = path;
	}
	
	/**
	 * Compare the points on their coordinates and see if they are equals. It's
	 * not a strict equality but an equality with a small (1.0E-06) tolerance.
	 * 
	 * @param lat1
	 *            1st point's latitude
	 * @param lon1
	 *            1st point's longitude
	 * @param lat2
	 *            2nd point's latitude
	 * @param lon2
	 *            2nd point's longitude
	 * @return
	 */
	private boolean samePlace( double lat1, double lon1, double lat2, double lon2 )
	{
		return NumHelper.equalsEps(lat1, lat2, 1e-6)
				&& NumHelper.equalsEps(lon1, lon2, 1e-6);
	}
	
	/**
	 * Adds a split of the edge's geometry to the path's geometry. The subset of
	 * points is taken out of the edge between the passed indexes and appended
	 * before or after the path. edgeFrom <= edgeTo is not always true it
	 * depends on the geocoded direction of edge's points. the edge from index
	 * must be the first index of the edge's node encountered when moving along
	 * the path from it's start to it's end. The method will handle the
	 * inversion when necessary.
	 * 
	 * @param edgePoints
	 *            full edge's points (including towers)
	 * @param pathPoints
	 *            path containing none of the edge's node
	 * @param edgeFrom
	 *            index of the first edge's node to add on the path (last node
	 *            if <code>before = true</code>)
	 * @param edgeTo
	 *            index of the last edge's node to add on the path (first node
	 *            if <code>before = true</code>)
	 * @param before
	 *            true : edge's nodes are added before the path<br>
	 *            false: edge's nodes are added after the path
	 * @return the distance appended to the path
	 */
	private double appendPath( PointList edgePoints, PointList pathPoints, int edgeFrom, int edgeTo, boolean before )
	{
		int startIdx = Math.min(edgeFrom, edgeTo);
		int endIdx = Math.max(edgeFrom, edgeTo);
		// invert index to loop in the positive direction
		boolean revert = edgeFrom > edgeTo;
		
		PointList newList;
		if(before)
		{
			// not possible to add before => copy points in a new PoinList
			newList = new PointList(pathPoints.getSize());
		} else
		{
			// invert loop for path's endings
			revert = !revert;
			newList = pathPoints;
		}
		
		double lat, lon, dist=0,
			   prevLon = Double.NaN,
			   prevLat = Double.NaN;
		// chain nodes from edge
		for ( int i=startIdx ; i <= endIdx ; i++ )
		{
			if(!revert)
			{
				lat = edgePoints.getLatitude(i);
				lon = edgePoints.getLongitude(i);
			} else
			{
				lat = edgePoints.getLatitude(endIdx-i+startIdx);
				lon = edgePoints.getLongitude(endIdx-i+startIdx);
			}
			newList.add(lat, lon);
			if(!Double.isNaN(prevLat))
			{
				dist += calc.calcDist(prevLat, prevLon, lat, lon);
			}
			prevLat = lat;
			prevLon = lon;
		}
		
		if(before)
		{
			// add the rest of the points
			for ( int i=0 ; i<pathPoints.getSize() ; i++ )
			{
				newList.add(pathPoints.getLatitude(i), pathPoints.getLongitude(i));
			}
		}
		this.points = newList;
		return dist;
	}
	
	/**
	 * This enables the "scale distance feature" which influences the way
	 * distance and time of path are updated. When computing points
	 * removed/added to the path, distance between these points is added/removed
	 * from the path. But sometimes edge's distance is a lot different from the
	 * sum of all distances between it's points. Therefore, to avoid negative
	 * time and duration it's possible to scale the removed distance into the
	 * edge's distance. When enabling scale distance a rule of 3 will be
	 * applied.
	 * 
	 * <pre>
	 * distance = dist2scale * edgeDist / totPtDist
	 * </pre>
	 * 
	 * @param b
	 */
	public void setScaleDistance(boolean b)
	{
		this.scaleDistances = b;
	}
	
	/**
	 * Utility class to group a PointList and the index at which a cut point was
	 * inserted to this list.
	 * 
	 * @author NG
	 * 
	 */
	private class PointListIndex
	{
		/** points including the cut point */
		final PointList points;
		/** cut point's index */
		final int insertedPointIndex;
		PointListIndex(PointList points, int index)
		{
			this.points = points;
			this.insertedPointIndex = index;
		}
	}
}