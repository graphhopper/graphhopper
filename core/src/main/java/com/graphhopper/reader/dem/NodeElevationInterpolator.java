package com.graphhopper.reader.dem;

import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.PointList;

public class NodeElevationInterpolator {

	private static final double EPSILON = 0.00001;
	private static final double EPSILON2 = EPSILON * EPSILON;
	private final GraphHopperStorage storage;

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
		} else if (outerNodeIds.length > 2) {
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
			double ele = calculateElevation(lat0, lon0, ele0, lat1, lon1, ele1, lat, lon);
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
			double ele = calculateElevation(lat, lon, pointList);
			storage.getNodeAccess().setNode(innerNodeId, lat, lon, ele);
		}
	}

	public double calculateElevation(double lat0, double lon0, double ele0, double lat1, double lon1, double ele1,
			double lat, double lon) {
		double dlat0 = lat0 - lat;
		double dlon0 = lon0 - lon;
		double dlat1 = lat1 - lat;
		double dlon1 = lon1 - lon;
		double l0 = Math.sqrt(dlon0 * dlon0 + dlat0 * dlat0);
		double l1 = Math.sqrt(dlon1 * dlon1 + dlat1 * dlat1);
		double l = l0 + l1;

		double ele = l < EPSILON ? (l0 < l1 ? ele0 : ele1) : (ele0 + (ele1 - ele0) * l0 / l);
		return ele;
	}

	private double calculateElevation(double lat, double lon, PointList pointList) {
		double[] vs = new double[pointList.size()];
		double[] eles = new double[pointList.size()];
		double v = 0;
		for (int index = 0; index < pointList.size(); index++) {
			double lati = pointList.getLat(index);
			double loni = pointList.getLat(index);
			double dlati = lati - lat;
			double dloni = loni - lon;
			double l2 = (dlati * dlati + dloni * dloni);
			eles[index] = pointList.getEle(index);
			if (l2 < EPSILON2) {
				return eles[index];
			}
			vs[index] = 1 / l2;
			v += vs[index];
		}

		double ele = 0;

		for (int index = 0; index < pointList.size(); index++) {
			ele += eles[index] * vs[index] / v;
		}
		return ele;
	}

}
