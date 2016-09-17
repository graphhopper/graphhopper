package com.graphhopper.reader.dem;

import com.graphhopper.util.PointList;

public class ElevationInterpolator {

	public static final double EPSILON = 0.00001;
	public static final double EPSILON2 = EPSILON * EPSILON;

	public double calculateElevation(double lat, double lon, double lat0, double lon0, double ele0, double lat1,
			double lon1, double ele1) {
		double dlat0 = lat0 - lat;
		double dlon0 = lon0 - lon;
		double dlat1 = lat1 - lat;
		double dlon1 = lon1 - lon;
		double l0 = Math.sqrt(dlon0 * dlon0 + dlat0 * dlat0);
		double l1 = Math.sqrt(dlon1 * dlon1 + dlat1 * dlat1);
		double l = l0 + l1;
		double ele = l < EPSILON ? (l0 <= l1 ? ele0 : ele1) : (ele0 + (ele1 - ele0) * l0 / l);
		return ele;
	}

	public double calculateElevation(double lat, double lon, PointList pointList) {
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
