package com.graphhopper.reader.dem;

import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.PointList;

public class NodeElevationInterpolator {

	private final GraphHopperStorage storage;
	private final ElevationInterpolator elevationInterpolator = new ElevationInterpolator();

	public NodeElevationInterpolator(GraphHopperStorage storage) {
		this.storage = storage;
	}

	public void interpolateElevationsOfInnerNodes(int[] outerNodeIds, int[] innerNodeIds) {
		if (outerNodeIds.length == 0) {
			// do nothing
		} else if (outerNodeIds.length == 1) {
			interpolateElevationsOfInnerNodesForOneOuterNode(outerNodeIds[0], innerNodeIds);
		} else if (outerNodeIds.length == 2) {
			interpolateElevationsOfInnerNodesForTwoOuterNodes(outerNodeIds[0], outerNodeIds[1], innerNodeIds);
		} else if (outerNodeIds.length == 3) {
			interpolateElevationsOfInnerNodesForThreeOuterNodes(outerNodeIds[0], outerNodeIds[1], outerNodeIds[2], innerNodeIds);
		} else if (outerNodeIds.length > 3) {
			interpolateElevationsOfInnerNodesForNOuterNodes(outerNodeIds, innerNodeIds);
		}
	}

	private void interpolateElevationsOfInnerNodesForOneOuterNode(int outerNodeId, int[] innerNodeIds) {
		double ele = storage.getNodeAccess().getEle(outerNodeId);
		for (int innerNodeId : innerNodeIds) {
			double lat = storage.getNodeAccess().getLat(innerNodeId);
			double lon = storage.getNodeAccess().getLon(innerNodeId);
			storage.getNodeAccess().setNode(innerNodeId, lat, lon, ele);
		}
	}

	private void interpolateElevationsOfInnerNodesForTwoOuterNodes(int firstOuterNodeId, int secondOuterNodeId,
			int[] innerNodeIds) {
		double lat0 = storage.getNodeAccess().getLat(firstOuterNodeId);
		double lon0 = storage.getNodeAccess().getLon(firstOuterNodeId);
		double ele0 = storage.getNodeAccess().getEle(firstOuterNodeId);

		double lat1 = storage.getNodeAccess().getLat(secondOuterNodeId);
		double lon1 = storage.getNodeAccess().getLon(secondOuterNodeId);
		double ele1 = storage.getNodeAccess().getEle(secondOuterNodeId);

		for (int innerNodeId : innerNodeIds) {
			double lat = storage.getNodeAccess().getLat(innerNodeId);
			double lon = storage.getNodeAccess().getLon(innerNodeId);
			double ele = elevationInterpolator.calculateElevation(lat, lon, lat0, lon0, ele0, lat1, lon1, ele1);
			storage.getNodeAccess().setNode(innerNodeId, lat, lon, ele);
		}
	}
	
	private void interpolateElevationsOfInnerNodesForThreeOuterNodes(int firstOuterNodeId, int secondOuterNodeId,int thirdOuterNodeId,
			int[] innerNodeIds) {
		double lat0 = storage.getNodeAccess().getLat(firstOuterNodeId);
		double lon0 = storage.getNodeAccess().getLon(firstOuterNodeId);
		double ele0 = storage.getNodeAccess().getEle(firstOuterNodeId);

		double lat1 = storage.getNodeAccess().getLat(secondOuterNodeId);
		double lon1 = storage.getNodeAccess().getLon(secondOuterNodeId);
		double ele1 = storage.getNodeAccess().getEle(secondOuterNodeId);

		double lat2 = storage.getNodeAccess().getLat(thirdOuterNodeId);
		double lon2 = storage.getNodeAccess().getLon(thirdOuterNodeId);
		double ele2 = storage.getNodeAccess().getEle(thirdOuterNodeId);

		for (int innerNodeId : innerNodeIds) {
			double lat = storage.getNodeAccess().getLat(innerNodeId);
			double lon = storage.getNodeAccess().getLon(innerNodeId);
			double ele = elevationInterpolator.calculateElevation(lat, lon, lat0, lon0, ele0, lat1, lon1, ele1, lat2, lon2, ele2);
			storage.getNodeAccess().setNode(innerNodeId, lat, lon, ele);
		}
	}
	

	private void interpolateElevationsOfInnerNodesForNOuterNodes(int[] outerNodeIds, int[] innerNodeIds) {
		PointList pointList = new PointList(outerNodeIds.length, true);
		for (int outerNodeId : outerNodeIds) {
			pointList.add(storage.getNodeAccess().getLat(outerNodeId), storage.getNodeAccess().getLon(outerNodeId),
					storage.getNodeAccess().getEle(outerNodeId));
		}
		for (int innerNodeId : innerNodeIds) {
			double lat = storage.getNodeAccess().getLat(innerNodeId);
			double lon = storage.getNodeAccess().getLon(innerNodeId);
			double ele = elevationInterpolator.calculateElevation(lat, lon, pointList);
			storage.getNodeAccess().setNode(innerNodeId, lat, lon, ele);
		}
	}
}
