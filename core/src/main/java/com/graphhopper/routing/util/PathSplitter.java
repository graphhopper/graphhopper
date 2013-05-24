package com.graphhopper.routing.util;

import com.graphhopper.storage.Graph;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.DistancePlaneProjection;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.LineEquation;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPlace;

public class PathSplitter {

	private DistanceCalc distCalc = new DistancePlaneProjection();
	
	/**
	 * Compute the point where to cut the line segment defined by A(ax,ay) and
	 * B(bx,by) in such way that the resulting point is the orthogonal
	 * projection of point P(px,py) on AB
	 * 
	 * @param px abscissa of point P
	 * @param py coordinate of point P
	 * @param ax abscissa of point A
	 * @param ay coordinate of point A
	 * @param bx abscissa of point B
	 * @param by coordinate of point B
	 * @return
	 */
	public GHPlace getCutPoint(double px, double py, double ax, double ay, double bx, double by) {
		LineEquation pointCalc = new LineEquation(new double[]{ax, ay}, new double[]{bx, by});
		return pointCalc.getIntersection(py, px);
	}
	
	/**
	 * Compute the point where to cut the line segment defined by A(ax,ay) and
	 * B(bx,by) in such way that the resulting point is the orthogonal
	 * projection of point P(px,py) on AB
	 */
	public GHPlace getCutPoint(GHPlace p, GHPlace a, GHPlace b) {
		return getCutPoint(p.lon, p.lat, a.lon, a.lat, b.lon, b.lat);
	}
	
	/**
	 * Find the location where the cut node has to be inserted.
	 * This place is on the closest edge' segment to the GPS point.
	 * The returned index is the index of the node right after the cut, so the cut will have to be between index-1 and index.
	 * @param baseGeom
	 * @param lat GPS point latitude
	 * @param lon  GPS point longitude
	 * @return index
	 */
	public int findInsertIndex(PointList baseGeom, double lat, double lon) {
		double minDist= Double.MAX_VALUE;
		int index=-1;
		double ptdist, plon, plat;
		double prevLat = baseGeom.latitude(0), 
			   prevLon = baseGeom.longitude(0);
		for(int i=1 ; i < baseGeom.size() ; i++) {
			plat = baseGeom.latitude(i);
			plon = baseGeom.longitude(i);
			// check that point can be projected on segment (between the two points, not outside)
			if(distCalc.validEdgeDistance(lat, lon, prevLat, prevLon, plat, plon)) {
				ptdist = distCalc.calcNormalizedEdgeDistance(lat, lon, prevLat, prevLon, plat, plon);
				if(ptdist < minDist) {
					minDist = ptdist;
					index = i;
				}
			}
			prevLat = plat;
			prevLon = plon;
		}
		if(index == -1) {
			// point could not be placed on any of the edge' segment.
			// compute distance between GPS point and first point
			ptdist = distCalc.calcNormalizedDist(baseGeom.latitude(0), baseGeom.longitude(0), lat, lon);
			// compute distance between GPS and last point
			minDist = distCalc.calcNormalizedDist(baseGeom.latitude(baseGeom.size()-1), baseGeom.longitude(baseGeom.size()-1), lat, lon);
			if(ptdist < minDist) {
				index = 0;
			} else {
				index = baseGeom.size();
			}
		}
		
		return index;
	}

//	/**
//	 * Compute the 2D distance from point M:(xM,yM), to the line segment BE.
//	 * B:(xB,yB) E:(xE,yE) By using the power of a point to a line Advantage of
//	 * this formula:
//	 * <ul>
//	 * <li>it does not compute the distance between the point and line passing
//	 * by B and E but only the distance between M and BE.</li>
//	 * <li>it uses simple math operators and avoid quotient as much as possible.
//	 * </li>
//	 * </ul>
//	 * 
//	 * @param xM
//	 *            abscissa of M point
//	 * @param yM
//	 *            coordinate of M point
//	 * @param xB
//	 *            abscissa of B point
//	 * @param yB
//	 *            coordinate of B point
//	 * @param xE
//	 *            abscissa of E point
//	 * @param yE
//	 *            coordinate of E point
//	 * @return
//	 */
//	private double segmentDist(double xM, double yM, double xB, double yB, double xE, double yE) {
//		// coordinates a,b of vector EB
//		double a = xE - xB;
//		double b = yE - yB;
//		// equation of the perpendicular D1 in B to (EB): ax+by+w1
//		double w1 = -a * xB - b * yB;
//		// equation of the perpendicular D2 in E to (EB): ax+by+w2
//		double w2 = -a * xE - b * yE;
//		// equation of the line (EB) : bx-ay+w3
//		double w3 = a * yB - b * xB;
//		// power of M in relation to D1
//		double PMD1 = a * xM + b * yM + w1;
//		// power of M in relation to D2
//		double PMD2 = a * xM + b * yM + w2;
//		// power of B in relation to D2
//		double PBD2 = a * xB + b * yB + w2;
//		// power of E in relation to D1
//		double PED1 = a * xE + b * yE + w1;
//		// at this point, no sqrt nor quotient were computed.
//		if (PMD1 * PED1 < 0) { // M and E are on either side of D1
//			return Math.sqrt((xM - xB) * (xM - xB) + (yM - yB) * (yM - yB)); // no quotient
//		}
//		if (PMD2 * PBD2 < 0) { // M and B are on either side of D2
//			return Math.sqrt((xM - xE) * (xM - xE) + (yM - yE) * (yM - yE)); // no quotient
//		}
//		return Math.abs(b * xM - a * yM + w3) / Math.sqrt(a * a + b * b); // no choice but doing a quotient
//	}
	
	
	/**
	 * Create a full geometry of edge's pillars + towers
	 * @param edge
	 * @return
	 */
	public PointList extractFullGeom(EdgeIterator edge, Graph graph) {
		double fromLat = graph.getLatitude(edge.baseNode());
		double fromLon = graph.getLongitude(edge.baseNode());
		double tolat = graph.getLatitude(edge.adjNode());
		double tolon = graph.getLongitude(edge.adjNode());
		PointList baseGeom = edge.wayGeometry();
		PointList fullGeom = new PointList(baseGeom.size()+2);
		fullGeom.add(fromLat, fromLon);
		for(int i=0 ; i < baseGeom.size() ; i++) {
			fullGeom.add(baseGeom.latitude(i), baseGeom.longitude(i));
		}
		fullGeom.add(tolat, tolon);
		return fullGeom;
	}
}
