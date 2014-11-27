package com.graphhopper.reader.datexupdates;

import uk.co.ordnancesurvey.api.srs.LatLong;

public class SimpleLatLong implements LatLong {

	private double latAngle;
	private double longAngle;

	public SimpleLatLong(String latitude, String longitude) {
		latAngle = Double.parseDouble(latitude);
		longAngle = Double.parseDouble(longitude);
	}

	@Override
	public double getLatAngle() {
		return latAngle;
	}

	@Override
	public double getLongAngle() {
		return longAngle;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof LatLong) {
			LatLong location = (LatLong) obj;
			return (this.latAngle == location.getLatAngle() 
					&& this.longAngle == location.getLongAngle());

		}
		return false;
	}
}
