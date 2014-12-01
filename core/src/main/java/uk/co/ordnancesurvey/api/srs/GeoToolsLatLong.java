package uk.co.ordnancesurvey.api.srs;

import org.geotools.geometry.DirectPosition2D;

public class GeoToolsLatLong implements LatLong {
	double lat;
	double lon;
	
	public GeoToolsLatLong(DirectPosition2D pos) {
		lon = pos.y;
		lat = pos.x;
	}
	
	@Override
	public double getLatAngle() {
		return lat;
	}
	
	@Override
	public double getLongAngle() {
		return lon;
	}

}
