package com.graphhopper.reader.datexupdates;

import com.graphhopper.util.shapes.GHPoint;

public class LatLongMetaData {

	private String speed;
	private GHPoint location;

	public LatLongMetaData(String speed, String lat, String lon) {
		this(speed, Double.parseDouble(lat), Double.parseDouble(lon));
	}

	public LatLongMetaData(String speed, double lat, double lon) {
		this.speed = speed;
		location = new GHPoint(lat, lon);
	}

	public Object getMetaData(String speedTag) {
		return speed;
	}

	public GHPoint getLocation() {
		return location;
	}

}
