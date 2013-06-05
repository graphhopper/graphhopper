package com.graphhopper.storage.virtual;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.PathSplitter;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.LevelGraphStorage;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPlace;

public class VirtualGraphStorage extends LevelGraphStorage {

	
	private static final Logger logger = LoggerFactory.getLogger(VirtualGraphStorage.class);
	
	private PathSplitter splitter = new PathSplitter();
	
	public VirtualGraphStorage(Directory dir, EncodingManager encodingManager) {
		super(new PushDirectory(dir), encodingManager);
	}
	
	public int cutEdge(EdgeIterator edge, GHPlace gpsPt) {
		logger.debug("Splitting edge {} - {}", edge, getFullEdgeWkt(edge));
		int nodeId = this.nodes();
		splitEdge(edge, nodeId, gpsPt.lat, gpsPt.lon);
		return nodeId;
	}
	
	@Override
	protected long getGraphSignature() {
		return nodes.getHeader(0);
	}
	
	/**
	 * Create the cut point on the edge at the closes location on the edge.
	 * An virtual node is added to the graph at this cut location.
	 * @param lat GPS latitude of the point
	 * @param lon GPS longitude of the point
	 * @param fullGeom full geometry of the edge including tower nodes
	 * @param eg1 first edge' segment (edge.baseNode -> cutPoint)
	 * @param eg2 second edge' segment (cutPoint -> edge.adjNode) 
	 * @return
	 */
	private GHPlace splitEdge(EdgeIterator baseEdge, int cutNode, double lat, double lon) {
		PointList fullGeom = splitter.extractFullGeom(baseEdge, this);
		GHPlace cutPt = null;
		// find cut index
		int cutIndex = splitter.findInsertIndex(fullGeom, lat, lon);
		DistanceCalc calc = new DistanceCalc();
		double cutDist = 0;
		double prevLat=-1, prevLon=-1, plat, plon;
		PointList cutPoints = new PointList();
		EdgeIterator addedEdge;
		// populates split edges.
		// aggregate in first edge until the cut then aggregate in second edge.
		for(int i=0 ; i < fullGeom.size() ; i++) {
			plat = fullGeom.latitude(i);
			plon = fullGeom.longitude(i);

			if(i == cutIndex) {
				// compute cut coordinates.
				cutPt = splitter.getCutPoint(lon, lat, prevLon, prevLat, plon, plat);
				this.setNode(cutNode, cutPt.lat, cutPt.lon);
				logger.debug("Added virtual node ID : {} - {}", cutNode, getNodeWkt(cutNode));
				
				cutDist += calc.calcDist(prevLat, prevLon, cutPt.lat, cutPt.lon);
				addedEdge = this.edge(baseEdge.baseNode(), cutNode, cutDist, baseEdge.flags());
				addedEdge.wayGeometry(cutPoints);
				logger.debug("Added virtual edge {} - {}", addedEdge, getFullEdgeWkt(addedEdge));
				
				// after cut, reset aggregate to populate the 2nd edge
				cutDist = 0;
				cutPoints = new PointList();
				prevLat = cutPt.lat;
				prevLon = cutPt.lon;
			}
			if(i > 0 && i < fullGeom.size()) {
				// do not add towers to final geometry
				cutPoints.add(plat, plon);
			}
			if(i > 0) {
				// aggregate distance
				cutDist += calc.calcDist(prevLat, prevLon, plat, plon);
			}
			prevLat = plat;
			prevLon = plon;		
		}
		addedEdge = this.edge(cutNode, baseEdge.adjNode(), cutDist, baseEdge.flags());
		addedEdge.wayGeometry(cutPoints);
		logger.debug("Added virtual edge {} - {}", addedEdge, getFullEdgeWkt(addedEdge));
		
		return cutPt;
	}
	
	private String getNodeWkt(int node) {
		return new StringBuffer("POINT(")
			.append(getLongitude(node)).append(' ')
			.append(getLatitude(node)).append(')')
			.toString();
	}
	
	private String getFullEdgeWkt(int edge) {
		return getFullEdgeWkt(getEdgeProps(edge, -1));
	}
	
	private String getFullEdgeWkt(EdgeIterator eg) {
		StringBuilder sb = new StringBuilder("LINESTRING(")
			.append(getLongitude(eg.baseNode())).append(' ')
			.append(getLatitude(eg.baseNode()));
		PointList pts = eg.wayGeometry();
		for(int i=0 ; i < pts.size() ; i++) {
			sb.append(',').append(pts.longitude(i))
			  .append(' ').append(pts.latitude(i));
		}
		sb.append(',').append(getLongitude(eg.adjNode()))
		  .append(' ').append(getLatitude(eg.adjNode()))
		  .append(')');
		return sb.toString();
	}
}
