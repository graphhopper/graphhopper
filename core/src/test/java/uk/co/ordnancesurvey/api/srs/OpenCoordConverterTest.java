package uk.co.ordnancesurvey.api.srs;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.TransformException;

/**
 * Unit Test suite for the @link{OpenCoordConverter} utility class.
 * @author Stuart Adam
 * @author Mat Brett
 *
 */
public class OpenCoordConverterTest {
	
	/* reference zero coordinate for BNG co-ordinate system */
	private static final int ZERO_POINT = 0;
	/* latitude of BNG easting = 0 in WGS84*/
	private static final double LAT_ANGLE = 49.76680727257757;
	/* longitude of BNG northing = 0 in WGS84*/
	private static final double LON_ANGLE = -7.557159804822196;

	/**
	 * Verifies that the class under test correctly converts BNG coordinates into WGS84 coordinates.
	 */
	@Test
	public void shouldConvertFromBNGtoWGS84() throws MismatchedDimensionException, FactoryException, TransformException {
		final double bngEasting = ZERO_POINT;
		final double bngNorthing = ZERO_POINT;
		
		LatLong wgs84DerivedCoordinates = OpenCoordConverter.toWGS84(bngEasting, bngNorthing);
		assertEquals(LON_ANGLE, wgs84DerivedCoordinates.getLongAngle(), 0);
		assertEquals(LAT_ANGLE, wgs84DerivedCoordinates.getLatAngle(), 0);
		
		
		System.err.println("EASTING:NORTHING");
		System.err.println(bngEasting + ":" + bngNorthing);
		System.err.println("    Lat   :     Long   " );
		System.err.println(wgs84DerivedCoordinates.getLatAngle() + ":" + wgs84DerivedCoordinates.getLongAngle() );
	}
	
	/**
	 * Verifies that the class under test correctly converts WGS84 coordinates into BNG coordinates, accurate to one decimal place.
	 */
	@Test
	public void shouldConvertFromWGS84toBNG() throws MismatchedDimensionException, FactoryException, TransformException {
		
		final double wgs84Lat = LAT_ANGLE;
		final double wgs84Long = LON_ANGLE;
		
		LatLong bngDerivedCoordinates = OpenCoordConverter.toBNG(wgs84Lat, wgs84Long);
		
		double bngEasting = round(bngDerivedCoordinates.getLatAngle(), 1);
		assertEquals(ZERO_POINT, bngEasting, 0);
		
		double bngNorthing = round(bngDerivedCoordinates.getLongAngle(), 1);
		assertEquals(ZERO_POINT, bngNorthing, 0);
	}
	
	/**
	 * Convenience method which rounds a given double value to a given number of decimal places.
	 * @param 	value		double		Double value to be rounded. 
	 * @param 	places		int			Number of decimal places to round the value to.
	 * @return				double		Rounded value.
	 */
	private static double round(double value, int places) {
	    if (places < 0) throw new IllegalArgumentException();

	    long factor = (long) Math.pow(10, places);
	    value = value * factor;
	    long tmp = Math.round(value);
	    return (double) tmp / factor;
	}
}
