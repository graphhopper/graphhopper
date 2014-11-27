package com.graphhopper.reader.datexupdates;

import uk.co.ordnancesurvey.api.srs.LatLong;

public class LatLongMetaData {

	private String speed;
	private SimpleLatLong location;

	public LatLongMetaData(String speed, String lat, String lon) {
		this.speed = speed;
		location = new SimpleLatLong(lat, lon);
	}

	public Object getMetaData(String speedTag) {
		return speed;
	}

	public LatLong getLocation() {
		return location;
	}

}
