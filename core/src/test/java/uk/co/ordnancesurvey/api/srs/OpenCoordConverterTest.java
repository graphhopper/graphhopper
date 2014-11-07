package uk.co.ordnancesurvey.api.srs;

import static org.junit.Assert.*;

import org.geotools.geometry.DirectPosition2D;
import org.junit.Test;
import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.TransformException;

public class OpenCoordConverterTest {

	@Test
	public void testToWGS84() throws MismatchedDimensionException, FactoryException, TransformException {
		double northing = 50;
		double easting = -1;
		
		LatLong wgs84 = OpenCoordConverter.toWGS84(easting, northing);
		assertEquals(-7.557224956983527, wgs84.getLongAngle(), 0);
		assertEquals(49.76725419905698, wgs84.getLatAngle(), 0);
		
		LatLong wgs842 = CoordConverter.toWGS84(easting, northing);
		
		assertEquals(49.76725419905698, wgs842.getLatAngle(), 0.00001);
		assertEquals(-7.557224956983527, wgs842.getLongAngle(), 0.0001);
	}

}
