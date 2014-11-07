package uk.co.ordnancesurvey.api.srs;

public class CoordConverter {
	
	private static OSGrid2LatLong coordConvertor = new OSGrid2LatLong();
	
	public static LatLong toWGS84(double easting, double northing) {
		MapPoint osgb36Pt = new MapPoint(easting, northing);
		GeodeticPoint wgs84Pt = null;
		try {
			wgs84Pt = coordConvertor.transformHiRes(osgb36Pt);
		} catch (OutOfRangeException ore) {
			//REALLY? 
			//TODO should this be where the lowres route goes?
		}
		if (null==wgs84Pt) {
			try {
				wgs84Pt = coordConvertor.transformLoRes(osgb36Pt);
			} catch(OutOfRangeException ore) {
				
			}
		}
		return new OSLatLong(wgs84Pt);
	}

}
