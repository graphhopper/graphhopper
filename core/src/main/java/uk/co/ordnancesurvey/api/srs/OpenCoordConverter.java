package uk.co.ordnancesurvey.api.srs;

import org.geotools.geometry.DirectPosition2D;
import org.geotools.referencing.CRS;
import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;

/**
 * Utility class which enables us to translate Cartesian references between the BNG and WGS84 coordinate reference systems.
 * @author Stuart Adam
 * @author Mat Brett
 */
public class OpenCoordConverter {
	/* Coordinate reference systems */
	private static CoordinateReferenceSystem bngCoordRefSystem;
	private static CoordinateReferenceSystem wgs84CoordRefSystem;
	
	/* EPSG codes for coordinate reference systems */
	private static final String BNG_CRS_CODE = "EPSG:27700";
	private static final String WGS84_CRS_CODE = "EPSG:4326";

	static {
		try {
			bngCoordRefSystem = CRS.decode(BNG_CRS_CODE);
			wgs84CoordRefSystem = CRS.decode(WGS84_CRS_CODE);
		} catch (NoSuchAuthorityCodeException e) {
			e.printStackTrace();
		} catch (FactoryException e) {
			e.printStackTrace();
		}
	}
	
	/** With respect to the WGS84 datum, this method converts the BNG easting and northing coordinates provided as arguments to their equivalent
	 *  decimal latitude and longitude coordinates and returns them via a @link{LatLong} wrapper instance.
	 *  
	 *   @param bngEasting				double		(Required) BNG easting coordinate value.
	 *   @param bngNorthing				double		(Required) BNG northing coordinate value.
	 *   @return						LatLong		Wrapper class containing the converted WGS84 decimal coordinate values.
	 * 	 @throws FactoryException
	 *   @throws MismatchedDimensionException
	 *   @throws TransformException
	 *  */
	public static LatLong toWGS84(double bngEasting, double bngNorthing)
			throws FactoryException, MismatchedDimensionException,
			TransformException {
		return transformFromSourceCRSToTargetCRS(bngCoordRefSystem, wgs84CoordRefSystem, bngEasting, bngNorthing, false);
	}

	/** With respect to the BNG datum/projection pairing, this method converts the WGS84 decimal latitude and longitude coordinates provided as arguments to their equivalent
	 *  BNG easting and northing coordinates and returns them via a @link{LatLong} wrapper instance.
	 *  
	 *  The transformation is accurate to one decimal place (rounded).
	 *  
	 *   @param wsg84Latitude				double		(Required) WSG84 decimal latitude coordinate value.
	 *   @param wsg84Longitude				double		(Required) WSG84 decimal longitude value.
	 *   @return							LatLong		Wrapper class containing the converted BNG easting and northing values.
	 * 	 @throws FactoryException
	 *   @throws MismatchedDimensionException
	 *   @throws TransformException
	 *  */
	public static LatLong toBNG(double wsg84Latitude, double wsg84Longitude)
			throws FactoryException, MismatchedDimensionException,
			TransformException {
		return transformFromSourceCRSToTargetCRS(wgs84CoordRefSystem, bngCoordRefSystem, wsg84Latitude, wsg84Longitude, false);
	}
	
	/** 
	 * Convenience method which translates the sourceEasting/Latitude and sourceNorthing/Longitude coordinates expressed in the sourceCRS form to coordinates expressed in the targetCRS form,
	 * and returns those coordinates via a @link{LatLong} wrapper instance.
	 * 
	 * @param sourceCRS				CoordinateReferenceSystem	(Required) Source Coordinate Reference System.	
	 * @param targetCRS				CoordinateReferenceSystem	(Required) Target Coordinate Reference System.	
	 * @param sourceXCoordinate		double						Easting/Latitude coordinates expressed with reference to the Source Coordinate Reference System.
	 * @param sourceyCoordinate		double						Northing/Longitude coordinates expressed with reference to the Source Coordinate Reference System.
	 * @param lenient				boolean						Set to true if the math transform should be created even when there is no information available for a datum shift.		
	 * @return
	 * @throws FactoryException
	 * @throws MismatchedDimensionException
	 * @throws TransformException
	 */
	private static LatLong transformFromSourceCRSToTargetCRS(CoordinateReferenceSystem sourceCRS, CoordinateReferenceSystem targetCRS, 
			double sourceXCoordinate, double sourceyCoordinate, boolean lenient) throws FactoryException, MismatchedDimensionException, TransformException {
		
		if(null == sourceCRS || null == targetCRS) {
			throw new IllegalArgumentException("Cannot transform between co-ordinate systems if either or neither system is specified.");
		}

		MathTransform transform = CRS.findMathTransform(sourceCRS,targetCRS, lenient);
		DirectPosition2D srcPos = new DirectPosition2D(sourceCRS, sourceXCoordinate, sourceyCoordinate);
		DirectPosition2D targetPos = new DirectPosition2D();

		transform.transform(srcPos, targetPos);
		return new GeoToolsLatLong(targetPos);
	}

}
