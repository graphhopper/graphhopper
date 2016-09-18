package com.graphhopper.reader.dem;

import com.graphhopper.coll.GHBitSet;
import com.graphhopper.coll.GHBitSetImpl;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.DataFlagEncoder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.BreadthFirstSearch;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PointList;

import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;

public abstract class AbstractEdgeElevationInterpolator {

	private final GraphHopperStorage storage;
	protected final DataFlagEncoder dataFlagEncoder;
	private final NodeElevationInterpolator nodeElevationInterpolator;
	private final ElevationInterpolator elevationInterpolator = new ElevationInterpolator();

	public AbstractEdgeElevationInterpolator(GraphHopperStorage storage, DataFlagEncoder dataFlagEncoder) {
		this.storage = storage;
		this.dataFlagEncoder = dataFlagEncoder;
		this.nodeElevationInterpolator = new NodeElevationInterpolator(storage);
	}

	protected abstract boolean isStructureEdge(EdgeIteratorState edge);
	
	public GraphHopperStorage getStorage() {
		return storage;
	}

	public void execute() {
		interpolateElevationsOfTowerNodes();
		interpolateElevationsOfPillarNodes();
	}

	private void interpolateElevationsOfTowerNodes() {
		final AllEdgesIterator edge = storage.getAllEdges();
		final GHBitSet visitedEdgeIds = new GHBitSetImpl(edge.getMaxId());
		final EdgeExplorer edgeExplorer = storage.createEdgeExplorer();

		while (edge.next()) {
			final int edgeId = edge.getEdge();
			if (isStructureEdge(edge)) {
				if (!visitedEdgeIds.contains(edgeId)) {
					processStructureEdge(edge, visitedEdgeIds, edgeExplorer, edgeId);
				}
			}
			visitedEdgeIds.add(edgeId);
		}
	}

	private void processStructureEdge(final EdgeIteratorState structureEdge, final GHBitSet visitedEdgeIds,
			final EdgeExplorer edgeExplorer, int edgeId) {
		final TIntSet outerNodeIds = new TIntHashSet();
		final TIntSet innerNodeIds = new TIntHashSet();
		gatherOuterAndInnerNodeIdsOfStructure(edgeExplorer, structureEdge, visitedEdgeIds, outerNodeIds,
				innerNodeIds);
		nodeElevationInterpolator.interpolateElevationsOfInnerNodes(outerNodeIds.toArray(), innerNodeIds.toArray());
	}

	public void gatherOuterAndInnerNodeIdsOfStructure(final EdgeExplorer edgeExplorer,
			final EdgeIteratorState structureEdge, final GHBitSet visitedEdgesIds, final TIntSet outerNodeIds,
			final TIntSet innerNodeIds) {
		final BreadthFirstSearch gatherOuterAndInnerNodeIdsOfStructureSearch = new BreadthFirstSearch() {
			protected boolean checkAdjacent(EdgeIteratorState edge) {
				visitedEdgesIds.add(edge.getEdge());
				final int baseNodeId = edge.getBaseNode();
				boolean isStructureEdge = isStructureEdge(edge);
				if (!isStructureEdge) {
					innerNodeIds.remove(baseNodeId);
					outerNodeIds.add(baseNodeId);
				} else if (!outerNodeIds.contains(baseNodeId)) {
					innerNodeIds.add(baseNodeId);
				}
				return isStructureEdge;
			}
		};
		gatherOuterAndInnerNodeIdsOfStructureSearch.start(edgeExplorer, structureEdge.getBaseNode());
	}

	private void interpolateElevationsOfPillarNodes() {
		final EdgeIterator edge = storage.getAllEdges();
		while (edge.next()) {
			if (isStructureEdge(edge)) {
				int firstNodeId = edge.getBaseNode();
				int secondNodeId = edge.getAdjNode();

				double lat0 = storage.getNodeAccess().getLat(firstNodeId);
				double lon0 = storage.getNodeAccess().getLon(firstNodeId);
				double ele0 = storage.getNodeAccess().getEle(firstNodeId);

				double lat1 = storage.getNodeAccess().getLat(secondNodeId);
				double lon1 = storage.getNodeAccess().getLon(secondNodeId);
				double ele1 = storage.getNodeAccess().getEle(secondNodeId);

				final PointList pointList = edge.fetchWayGeometry(0);
				final int count = pointList.size();
				for (int index = 0; index < count; index++) {
					double lat = pointList.getLat(index);
					double lon = pointList.getLon(index);
					double ele = elevationInterpolator.calculateElevation(lat, lon, lat0, lon0, ele0,
							lat1, lon1, ele1);
					pointList.set(index, lat, lon, ele);
				}
				edge.setWayGeometry(pointList);
			}
		}
	}
}
