package com.graphhopper.routing;

import com.graphhopper.routing.util.EdgePropertyEncoder;
import com.graphhopper.routing.util.PathSplitter;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.index.LocationIDResult;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.DistanceCalc2D;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPlace;

/**
 * 
 * @author NG
 *
 */
public class PathFinisher {

	private final LocationIDResult fromLoc;
	private final LocationIDResult toLoc;
	private final GHPlace from;
	private final GHPlace to;
	private final Path path;
	private final Graph graph;
	
	private boolean finished = false;
	private double distance = -1D;
	private long time = -1L;
	private PointList points;
	private PathSplitter splitter = new PathSplitter();
	
	public PathFinisher(LocationIDResult fromLoc, LocationIDResult toLoc,
			GHPlace from, GHPlace to, Path path, Graph graph) {
		super();
		this.fromLoc = fromLoc;
		this.toLoc = toLoc;
		this.from = from;
		this.to = to;
		this.path = path;
		this.graph = graph;
	}
	
	/**
	 * The path's distance enhanced with last edges data
	 * @return
	 */
	public double getFinishedDistance() {
		if(!finished) {
			this.finishPath();
		}
		return this.distance;
	}
	
	/**
	 * The path's time enhanced with last edges data
	 * @return
	 */
	public double getFinishedTime() {
		if(!this.finished) {
			this.finishPath();
		}
		return this.time;
	}
	
	/**
	 * The path's geometry enhanced with last edges data
	 * @return
	 */
	public PointList getFinishedPointList() {
		if(!this.finished) {
			this.finishPath();
		}
		return this.points;
	}
	
	private void finishPath() {
		this.points = this.path.calcPoints();
		this.distance  = this.path.distance();
		this.time = this.path.time();
		
		this.finishStart(this.fromLoc, this.points, this.from);
		
		finished = true;
	}
	
	private void finishStart(LocationIDResult loc, PointList pathPoints, GHPlace gpsStartPoint) {
		if(loc.closestEdge() == null) {
			// the starting edge is unknown, it's not possible to finish the path.
			return;
		}
		
		PointListIndex edgePointIdx = insertClosestGpsPoint(loc.closestEdge(), gpsStartPoint);
		
		// find common points between edge and path
		PointList edgePoints = edgePointIdx.points;
		double lat1, lon1;
		int idx1 = -1, idx2 = -1;
//		int pathLoop = edgePoints.size() + 5;
//		if(pathLoop > pathPoints.size()) {
//			pathLoop = pathPoints.size();
//		}
		for(int i=0 ; i < edgePoints.size() ; i++) {
			lat1 = edgePoints.latitude(i);
			lon1 = edgePoints.longitude(i);
			for(int j=0 ; j < pathPoints.size() ; j++) {
				// no need to loop on the whole path
				if(samePlace(lat1, lon1, pathPoints.latitude(j), pathPoints.longitude(j))) {
					if(idx1 < 0) {
						idx1 = i;
					} else {
						idx2 = i;
						break;
					}
				}
			}
			if(idx2 >=0) {
				break;
			}
		}
		
		if(idx1 >= 0) {
			if(idx2 >= 0) {
				// edge and path have 2 common points
			} else {
				// edge and path have only 1 common point -> need to add missing edge part
				int fromIdx = edgePointIdx.insertedPointIndex-1;
				appendPath(edgePoints, loc.closestEdge().flags(), fromIdx, idx1, idx1 == 0, true);
			}
		}
	}
	
	/**
	 * Insert a point in the edge's geometry at the closes location to the GPS
	 * point in the edge's geometry.
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
	 * @param edgePoints
	 * @param gps
	 * @return
	 */
	private PointListIndex insertClosestGpsPoint(EdgeIterator edge, GHPlace gps) {
		// add start/end edg's point
		PointList edgePoints = splitter.extractFullGeom(edge, graph);
		edgePoints.toWkt();
		// find split index
		int index = splitter.findInsertIndex(edgePoints, gps.lat, gps.lon);
		PointList newList = new PointList(edgePoints.size()+1);
		if(index >= 0) {
			for(int i=0 ; i<edgePoints.size() ; i++) {
				if(i == index-1) {
					// insert before
					GHPlace intersectPoint = splitter.getCutPoint(gps, edgePoints.point(index-1), edgePoints.point(index));
					newList.add(intersectPoint.lat, intersectPoint.lon);
				}
				newList.add(edgePoints.latitude(i), edgePoints.longitude(i));
			}
		}
		newList.toWkt();
		return new PointListIndex(newList, index);
	}
	
	/**
	 * Compare the points on their coordinates and see if they are equals. It's
	 * not a strict equality but an equality with a small tolerance. For such
	 * small distance, no need to take earth curve into account, planar distance
	 * is enough.
	 * 
	 * @param p1
	 * @param p2
	 * @return
	 */
	public boolean samePlace(double lat1, double lon1, double lat2, double lon2) {
		DistanceCalc2D calc = new DistanceCalc2D();
		double dist = calc.calcDist(lat1, lon1, lat2, lon2);
		return dist < 0.0001;
	}
	
	private void appendPath(PointList edgePoints, int edgeFlags, int fromIdx, int toIdx, boolean revert, boolean addBefore) {
		PointList newList;
		edgePoints.toWkt();
		if(addBefore) {
			newList = new PointList();
		} else {
			newList = points;
		}
		double dist = 0;
		DistanceCalc calc = new DistanceCalc();
		double prevLon = -1,
			   prevLat = -1,
			   lon, lat;
		int loopCpt = toIdx > fromIdx ? toIdx - fromIdx : fromIdx-toIdx;
		for(int i=0 ; i<loopCpt ; i++) {
			if(!revert) {
				lat = edgePoints.latitude(i);
				lon = edgePoints.longitude(i);				
			} else {
				lat = edgePoints.latitude(toIdx-i);
				lon = edgePoints.longitude(toIdx-i);	
			}
			newList.add(lat, lon);
			if(prevLon > -1) {
				dist += calc.calcDist(prevLat, prevLon, lat, lon);
			}
		}
		if(dist > 0) {
			EdgePropertyEncoder enc = path.encoder;
			long time = (long)(distance * 3.6 / enc.getSpeed(edgeFlags));
			this.time += time;
		}
		
		if(addBefore) {
			for(int i=0 ; i<points.size() ; i++) {
				newList.add(points.latitude(i), points.longitude(i));
			}
		}
		
		this.points = newList;
	}
	
	private class PointListIndex {
		final PointList points;
		final int insertedPointIndex;
		PointListIndex(PointList points, int index) {
			this.points = points;
			this.insertedPointIndex = index;
		}
	}
}
