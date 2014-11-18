package uk.co.ordnancesurvey.api.srs;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.TransformException;

public class OpenCoordConverterTest {

	private static final int ZERO_POINT = 0;
	private static final double LAT_ANGLE = 49.76680727257757;
	private static final double LON_ANGLE = -7.557159804822196;

	@Test
	public void testToWGS84() throws MismatchedDimensionException, FactoryException, TransformException {
		double northing = ZERO_POINT;
		double easting = ZERO_POINT;
		
		LatLong wgs84 = OpenCoordConverter.toWGS84(easting, northing);
		assertEquals(LON_ANGLE, wgs84.getLongAngle(), 0);
		assertEquals(LAT_ANGLE, wgs84.getLatAngle(), 0);
		
		
		System.err.println("EASTING:NORTHING");
		System.err.println(easting + ":" + northing);
		System.err.println("    Lat   :     Long   " );
		System.err.println(wgs84.getLatAngle() + ":" + wgs84.getLongAngle() );
	}
}
