package uk.co.ordnancesurvey.api.srs;

import org.geotools.geometry.DirectPosition2D;
import org.geotools.referencing.CRS;
import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;

public class OpenCoordConverter {
	private static CoordinateReferenceSystem sourceCRS;
	private static CoordinateReferenceSystem targetCRS;
	
	static {
		try {
			sourceCRS = CRS.decode("EPSG:27700");
			targetCRS = CRS.decode("EPSG:4326");
		} catch (NoSuchAuthorityCodeException e) {
			e.printStackTrace();
		} catch (FactoryException e) {
			e.printStackTrace();
		}
	}
	
	public static LatLong toWGS84(double easting, double northing) throws FactoryException, MismatchedDimensionException, TransformException {
		boolean lenient = false;  //need to update to full strict
		
		MathTransform transform = CRS.findMathTransform(sourceCRS, targetCRS, lenient);
		
		DirectPosition2D srcPos = new DirectPosition2D(sourceCRS, easting, northing);
		DirectPosition2D targetPos = new DirectPosition2D();
		
		transform.transform(srcPos, targetPos);
		return new GeoToolsLatLong(targetPos);
	}
}
