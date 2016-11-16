package com.graphhopper.reader.shp;

import com.graphhopper.util.PointList;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;

public class Utils {
	public static String toWKT(PointList list) {
		int n = list.size();
		GeometryFactory factory = new GeometryFactory();
		Coordinate[] coords = new Coordinate[n];
		for (int i = 0; i < coords.length; i++) {
			coords[i] = new Coordinate(list.getLon(i), list.getLat(i));
		}
		return factory.createLineString(coords).toText();
	}
}
