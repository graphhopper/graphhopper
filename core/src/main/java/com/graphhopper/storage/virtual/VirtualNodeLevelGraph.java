package com.graphhopper.storage.virtual;

import java.util.ArrayList;
import java.util.List;

import com.graphhopper.routing.util.AllEdgesSkipIterator;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.PathSplitter;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.LevelGraph;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeSkipIterator;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.GHPlace;

public class VirtualNodeLevelGraph implements LevelGraph {

	private static final int FROM_ND_ID = Integer.MAX_VALUE;
	private static final int TO_ND_ID = Integer.MAX_VALUE-1;
	private static final int FROM_EG_ID1 = Integer.MAX_VALUE-2;
	private static final int FROM_EG_ID2 = Integer.MAX_VALUE-3;
	private static final int TO_EG_ID1 = Integer.MAX_VALUE-4;
	private static final int TO_EG_ID2 = Integer.MAX_VALUE-5;
	
	private final LevelGraph graph;
	private GHPlace fromNode;
	private GHPlace toNode;
	private EdgeIterator baseFromEdge;
	private EdgeIterator baseToEdge;
	private EdgeInfo[] fromEdges = new EdgeInfo[2];
	private EdgeInfo[] toEdges = new EdgeInfo[2];
	private PathSplitter splitter = new PathSplitter();
	
	public VirtualNodeLevelGraph(LevelGraph graph) {
		this.graph = graph;
	}
	
	public GHPlace getFromNode() {
		return this.fromNode;
	}
	
	private void setFromNode(double lat, double lon, int nodeId) {
		this.fromNode = new GHPlace(lat, lon);
		this.fromNode.name("fake_start");
		this.fromNode.nodeId(nodeId);
	}
	
	public GHPlace getToNode() {
		return this.toNode;
	}
	
	private void setToNode(double lat, double lon, int nodeId) {
		this.toNode = new GHPlace(lat, lon);
		this.toNode.name("fake_to");
		this.toNode.nodeId(nodeId);
	}
	
	public int setFromEdges(EdgeIterator fromEdge, double lat, double lon) {
		this.baseFromEdge = fromEdge;
		PointList baseGeom = splitter.extractFullGeom(fromEdge, graph);
		int fromNodeId = FROM_ND_ID;
		EdgeInfo eg1 = new EdgeInfo(FROM_EG_ID1, fromEdge.baseNode(), fromNodeId, fromEdge.flags());
		EdgeInfo eg2 = new EdgeInfo(FROM_EG_ID2, fromNodeId, fromEdge.adjNode(), fromEdge.flags());
		GHPlace cutPt = splitEdge(baseGeom, lat, lon, eg1, eg2);
		
		setFromNode(cutPt.lat, cutPt.lon, fromNodeId);
		this.fromEdges[0] = eg1;
		this.fromEdges[1] = eg2;
		
		return fromNodeId;
	}
	
	public int setToEdges(EdgeIterator toEdge, double lat, double lon) {
		this.baseToEdge = toEdge;
		PointList baseGeom = splitter.extractFullGeom(toEdge, graph);
		int toNdId = TO_ND_ID;
		EdgeInfo eg1 = new EdgeInfo(TO_EG_ID1, toEdge.baseNode(), toNdId, toEdge.flags());
		EdgeInfo eg2 = new EdgeInfo(TO_EG_ID2, toNdId, toEdge.adjNode(), toEdge.flags());
		GHPlace cutPt = splitEdge(baseGeom, lat, lon, eg1, eg2);
		
		setToNode(cutPt.lat, cutPt.lon, toNdId);
		this.toEdges[0] = eg1;
		this.toEdges[1] = eg2;
		
		return toNdId;
	}

	/**
	 * Create the cut point on the edge at the closes location on the edge.
	 * An virtual node is added to the graph at this cut location.
	 * @param fullGeom full geometry of the edge including tower nodes
	 * @param lat GPS latitude of the point
	 * @param lon GPS longitude of the point
	 * @param eg1 first edge' segment (edge.baseNode -> cutPoint)
	 * @param eg2 second edge' segment (cutPoint -> edge.adjNode) 
	 * @return
	 */
	private GHPlace splitEdge(PointList fullGeom, double lat, double lon, EdgeInfo eg1, EdgeInfo eg2) {
		GHPlace cutPt = null;
		// find cut index
		int cutIndex = splitter.findInsertIndex(fullGeom, lat, lon);
		DistanceCalc calc = new DistanceCalc();
		double cutDist = 0;
		double prevLat=-1, prevLon=-1, plat, plon;
		PointList cutPoints = new PointList();
		// populates split edges.
		// aggregate in first edge until the cut then aggregate in second edge.
		for(int i=0 ; i < fullGeom.size() ; i++) {
			plat = fullGeom.latitude(i);
			plon = fullGeom.longitude(i);

			if(i == cutIndex) {
				// compute cut coordinates.
//				System.out.println("cut egde: LINESTRING("+prevLon+" "+prevLat+","+plon+" "+plat+")");
				cutPt = splitter.getCutPoint(lon, lat, prevLon, prevLat, plon, plat);
				cutDist += calc.calcDist(prevLat, prevLon, cutPt.lat, cutPt.lon);
				eg1.distance = cutDist;
				eg1.points = cutPoints;
				
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
		eg2.distance = cutDist;
		eg2.points = cutPoints;
		
//		System.out.println("Find cut point for POINT("+lon+" "+lat+") on "+fullGeom.toWkt()+"result: "+cutPt.toWkt());		
		
		return cutPt;
	}
	
	private GHPlace getNode(int nodeId) {
		if(nodeId == fromNode.nodeId())
			return fromNode;
		if(nodeId == toNode.nodeId()) {
			return toNode;
		}
		return null;
	}
	
	private EdgeInfo getEdge(int edgeId) {
		switch (edgeId) {
			case FROM_EG_ID1: return fromEdges[0];
			case FROM_EG_ID2: return fromEdges[1];
			case TO_EG_ID1: return toEdges[0];
			case TO_EG_ID2: return toEdges[1];
		}
		return null;
	}
	
	@Override
	public int nodes() {
		return graph.nodes() + 2;
	}

	@Override
	public void setNode(int node, double lat, double lon) {
		this.graph.setNode(node, lat, lon);
	}

	@Override
	public double getLatitude(int node) {
		GHPlace nd = getNode(node);
		if(nd != null) {
			return nd.lat;
		}
		return this.graph.getLatitude(node);
	}

	@Override
	public double getLongitude(int node) {
		GHPlace nd = getNode(node);
		if(nd != null) {
			return nd.lon;
		}
		return this.graph.getLongitude(node);
	}

	@Override
	public BBox bounds() {
		return this.graph.bounds();
	}

	@Override
	public EdgeSkipIterator edge(int a, int b, double distance, int flags) {
		return this.graph.edge(a, b, distance, flags);
	}

	@Override
	public EdgeSkipIterator edge(int a, int b, double distance,
			boolean bothDirections) {
		return this.graph.edge(a, b, distance, bothDirections);
	}

	@Override
	public EdgeSkipIterator getEdgeProps(int edgeId, int endNode) {
		EdgeInfo edge = this.getEdge(edgeId);
		EdgeSkipIterator esi = null;
		if(edge != null) {
			if(endNode == FROM_ND_ID) {
				esi = this.graph.getEdgeProps(baseFromEdge.edge(), endNode);
			} else {
				esi = this.graph.getEdgeProps(baseToEdge.edge(), endNode);
			}
			esi = new VirtualEdge(edge, esi);
		}
		if(endNode == FROM_ND_ID) {
			endNode = baseFromEdge.baseNode();
			esi = this.graph.getEdgeProps(edgeId, endNode);
			if(esi.isEmpty()) {
				endNode = baseFromEdge.adjNode();
				esi = this.graph.getEdgeProps(edgeId, endNode);
			}
		} else if(endNode == TO_ND_ID) {
			endNode = baseToEdge.baseNode();
			esi = this.graph.getEdgeProps(edgeId, endNode);
			if(esi.isEmpty()) {
				endNode = baseToEdge.adjNode();
				esi = this.graph.getEdgeProps(edgeId, endNode);
			}
		} else {
			esi = this.graph.getEdgeProps(edgeId, endNode);
		}
		return esi;
	}

	@Override
	public AllEdgesSkipIterator getAllEdges() {
		return this.graph.getAllEdges();
	}

	@Override
	public EdgeSkipIterator getEdges(int index, EdgeFilter filter) {
		if(index == this.fromNode.nodeId()) {
			// from cutNode : can go to other baseFromEdge's baseNode
			EdgeSkipIterator edge = this.graph.getEdges(this.fromEdges[0].baseNode, filter);
			edge.next();
			EdgeSkipIterator ve1 = new VirtualEdge(this.fromEdges[0], edge);
			EdgeSkipIterator edge2 = this.graph.getEdges(this.fromEdges[1].adjNode, filter);
			edge2.next();
			VirtualEdge ve2 = new VirtualEdge(this.fromEdges[1], edge2);
			ve2.addEdge(ve1);
			return ve2;
		} else if(index == toNode.nodeId()) {
			// to cutNode : can go to other baseToEdge's baseNode
			EdgeSkipIterator edge = this.graph.getEdges(this.toEdges[0].baseNode, filter);
			edge.next();
			EdgeSkipIterator ve1 = new VirtualEdge(this.toEdges[0], edge);
			EdgeSkipIterator edge2 = this.graph.getEdges(this.toEdges[1].adjNode, filter);
			edge2.next();
			VirtualEdge ve2 = new VirtualEdge(this.toEdges[1], edge2);
			ve2.addEdge(ve1);
			return ve2;
		}
		EdgeSkipIterator edge = this.graph.getEdges(index, filter);
		// when getting one of the 4 nodes of the bounding edges, add also the edges to the cut nodes
		if(index == fromEdges[0].baseNode) {
			edge = new VirtualEdge(fromEdges[0], edge);
		} else if(index == fromEdges[1].adjNode) {
			edge = new VirtualEdge(fromEdges[0], edge);
		} else if(index == toEdges[0].baseNode) {
			edge = new VirtualEdge(toEdges[0], edge);
		} else if(index == toEdges[1].adjNode) {
			edge = new VirtualEdge(toEdges[1], edge);
		}
		return edge;
	}

	@Override
	public EdgeSkipIterator getEdges(int index) {
		return this.getEdges(index, EdgeFilter.ALL_EDGES);
	}

	@Override
	public Graph copyTo(Graph g) {
		return this.graph.copyTo(g);
	}

	@Override
	public void markNodeRemoved(int index) {
		this.graph.markNodeRemoved(index);
	}

	@Override
	public boolean isNodeRemoved(int index) {
		return this.graph.isNodeRemoved(index);
	}

	@Override
	public void optimize() {
		this.graph.optimize();
	}

	@Override
	public void setLevel(int index, int level) {
		this.graph.setLevel(index, level);
	}

	@Override
	public int getLevel(int index) {
		return this.graph.getLevel(index);
	}
	
	protected class EdgeInfo {
		protected int baseNode;
		protected int adjNode;
		protected int edgeId;
		protected PointList points;
		protected double distance;
		protected int flags;
		
		protected EdgeInfo(int edgeId, int baseNode, int adjNode, int flags) {
			this.edgeId = edgeId;
			this.baseNode = baseNode;
			this.adjNode = adjNode;
			this.flags = flags;
		}
		
		@Override
		public String toString() {
			return String.format("EdgeInfo %s %s-%s", edgeId, baseNode, adjNode);
		}
	}
	
	protected class VirtualEdge implements EdgeSkipIterator {

		private EdgeInfo vEdge;
		private List<EdgeSkipIterator> nextEdges;
		protected EdgeSkipIterator curredge;
		private int idx;
		
		public VirtualEdge(EdgeInfo vEdge, EdgeSkipIterator base) {
			this.vEdge = vEdge;
			nextEdges = new ArrayList<EdgeSkipIterator>();
			idx = -1;
			curredge = null;
			if(base != null) {
				nextEdges.add(base);
			}
		}
		
		public void addEdge(EdgeSkipIterator edge) {
			this.nextEdges.add(edge);
		}
		
		@Override
		public boolean next() {
			if(idx == -1) {
				idx++;
				if(idx < nextEdges.size()) {
					curredge = nextEdges.get(idx);
				}
				return idx < nextEdges.size();
			}
			curredge = nextEdges.get(idx);
			boolean next = curredge.next();
			if(next) {
				return true;
			} else if(idx < nextEdges.size()-1) {
				idx ++;
				return true;
			}
			return false;
		}

		@Override
		public int edge() {
			if(idx == -1) {
				return this.vEdge.edgeId;
			}
			return curredge.edge();
		}

		@Override
		public int baseNode() {
			if(idx == -1) {
				return this.vEdge.baseNode;
			}
			return curredge.baseNode();
		}

		@Override
		public int adjNode() {
			if(idx == -1) {
				return this.vEdge.adjNode;
			}
			return curredge.adjNode();
		}

		@Override
		public PointList wayGeometry() {
			if(idx == -1) {
				return this.vEdge.points;
			}
			return curredge.wayGeometry();
		}

		@Override
		public void wayGeometry(PointList list) {
			this.vEdge.points = list;
		}

		@Override
		public double distance() {
			if(idx == -1) {
				return this.vEdge.distance;
			}
			return curredge.distance();
		}

		@Override
		public void distance(double dist) {
			this.vEdge.distance = dist;
		}

		@Override
		public int flags() {
			if(idx == -1) {
				return this.vEdge.flags;
			}
			return curredge.flags();
		}

		@Override
		public void flags(int flags) {
			this.vEdge.flags = flags;
		}

		@Override
		public boolean isEmpty() {
			return false;
		}

		@Override
		public int skippedEdge1() {
			if(idx == -1) {
				return -1;
			}
			return curredge.skippedEdge1();
		}

		@Override
		public int skippedEdge2() {
			if(idx == -1) {
				return -1;
			}
			return curredge.skippedEdge2();
		}

		@Override
		public void skippedEdges(int edge1, int edge2) {
			if(idx == -1) {
				return;
			}
			curredge.skippedEdges(edge1, edge2);
		}

		@Override
		public boolean isShortcut() {
			if(idx == -1) {
				return false;
			}
			return curredge.isShortcut();
		}
		
		@Override
		public String toString() {
			if(idx == -1) {
				return "VirtualEdge : " + vEdge.toString();
			} else {
				return curredge.getClass().getSimpleName() +  " " +  curredge.toString();
			}
		}
	}
}
