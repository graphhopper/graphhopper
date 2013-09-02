package com.graphhopper.routing.util;

import com.graphhopper.storage.Graph;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.DistancePlaneProjection;
import com.graphhopper.util.EdgeBase;
import com.graphhopper.util.LineEquation;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPlace;

/**
 * Class meant for edge splitting purpose. Given a GPS point close to the edge,
 * this class is able to compute a "cut point" being the best point on the edge
 * where the PSG point can be projected.
 * 
 * @author NG
 * 
 */
public class PathSplitter
{

	private DistanceCalc distCalc = new DistancePlaneProjection();

	/**
	 * Compute the point where to cut the line segment defined by A(ax,ay) and
	 * B(bx,by) in such way that the resulting point is the orthogonal
	 * projection of point P(px,py) on AB
	 * 
	 * @param px
	 *            abscissa of point P
	 * @param py
	 *            coordinate of point P
	 * @param ax
	 *            abscissa of point A
	 * @param ay
	 *            coordinate of point A
	 * @param bx
	 *            abscissa of point B
	 * @param by
	 *            coordinate of point B
	 * @return
	 */
	public GHPlace getCutPoint( double px, double py, double ax, double ay,
			double bx, double by )
	{
		LineEquation pointCalc = new LineEquation(new double[] { ax, ay },
				new double[] { bx, by });
		return pointCalc.getIntersection(py, px);
	}

	/**
	 * Compute the point where to cut the line segment defined by A(ax,ay) and
	 * B(bx,by) in such way that the resulting point is the orthogonal
	 * projection of point P(px,py) on AB
	 */
	public GHPlace getCutPoint(GHPlace p, GHPlace a, GHPlace b)
	{
		return getCutPoint(p.lon, p.lat, a.lon, a.lat, b.lon, b.lat);
	}

	/**
	 * Find the location where the cut node has to be inserted. This place is on
	 * the closest edge' segment to the GPS point. The returned index is the
	 * index of the node right after the cut, so the cut will have to be between
	 * index-1 and index.
	 * 
	 * @param baseGeom
	 * @param lat
	 *            GPS point latitude
	 * @param lon
	 *            GPS point longitude
	 * @return index
	 */
	public int findInsertIndex( PointList baseGeom, double lat, double lon )
	{
		double minDist = Double.MAX_VALUE;
		int index = -1;
		double ptdist, plon, plat;
		double prevLat = baseGeom.getLatitude(0),
			   prevLon = baseGeom.getLongitude(0);
		for ( int i=1 ; i < baseGeom.getSize() ; i++ )
		{
			plat = baseGeom.getLatitude(i);
			plon = baseGeom.getLongitude(i);
			// check that point can be projected on segment (between the two
			// points, not outside)
			if (distCalc.validEdgeDistance(lat, lon, prevLat, prevLon, plat, plon))
			{
				ptdist = distCalc.calcNormalizedEdgeDistance(lat, lon, prevLat, prevLon, plat, plon);
				if (ptdist < minDist)
				{
					minDist = ptdist;
					index = i;
				}
			}
			prevLat = plat;
			prevLon = plon;
		}
		if (index == -1)
		{
			// point could not be placed on any of the edge' segment.
			// compute distance between GPS point and first point
			ptdist = distCalc.calcNormalizedDist(baseGeom.getLatitude(0),
					baseGeom.getLongitude(0), lat, lon);
			// compute distance between GPS and last point
			minDist = distCalc.calcNormalizedDist(
					baseGeom.getLatitude(baseGeom.getSize() - 1),
					baseGeom.getLongitude(baseGeom.getSize() - 1), lat, lon);
			if (ptdist < minDist)
			{
				index = 0;
			} else
			{
				index = baseGeom.getSize();
			}
		}

		return index;
	}

	/**
	 * Create a full geometry of edge's pillars + towers
	 * 
	 * @param edge
	 * @return
	 */
	public PointList extractFullGeom( EdgeBase edge, Graph graph )
	{
		double fromLat = graph.getLatitude(edge.getBaseNode());
		double fromLon = graph.getLongitude(edge.getBaseNode());
		double tolat = graph.getLatitude(edge.getAdjNode());
		double tolon = graph.getLongitude(edge.getAdjNode());
		PointList baseGeom = edge.getWayGeometry();
		PointList fullGeom = new PointList(baseGeom.getSize() + 2);
		fullGeom.add(fromLat, fromLon);
		for (int i = 0 ; i < baseGeom.getSize() ; i++)
		{
			fullGeom.add(baseGeom.getLatitude(i), baseGeom.getLongitude(i));
		}
		fullGeom.add(tolat, tolon);
		return fullGeom;
	}
}
