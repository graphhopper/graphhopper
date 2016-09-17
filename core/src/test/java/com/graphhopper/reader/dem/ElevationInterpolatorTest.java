package com.graphhopper.reader.dem;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.graphhopper.util.Helper;

public class ElevationInterpolatorTest {
	
	private static final double PRECISION = ElevationInterpolator.EPSILON2;
	
	private ElevationInterpolator elevationInterpolator = new ElevationInterpolator();
	
	@Test
	public void calculatesElevationOnTwoPoints()
	{
		assertEquals(15, elevationInterpolator.calculateElevation(0, 0, -10, -10, 10, 10, 10, 20), PRECISION);
		assertEquals(15, elevationInterpolator.calculateElevation(-10, 10, -10, -10, 10, 10, 10, 20), PRECISION);
		assertEquals(15, elevationInterpolator.calculateElevation(-5, 5, -10, -10, 10, 10, 10, 20), PRECISION);
		assertEquals(19, elevationInterpolator.calculateElevation(8, 8, -10, -10, 10, 10, 10, 20), PRECISION);
		assertEquals(10, elevationInterpolator.calculateElevation(0, 0, - ElevationInterpolator.EPSILON/3, 0, 10, ElevationInterpolator.EPSILON/2, 0, 20), PRECISION);
		assertEquals(20, elevationInterpolator.calculateElevation(0, 0, - ElevationInterpolator.EPSILON/2, 0, 10, ElevationInterpolator.EPSILON/3, 0, 20), PRECISION);
		assertEquals(10, elevationInterpolator.calculateElevation(0, 0, 0, 0, 10, 0, 0, 20), PRECISION);
	}
	
	@Test
	public void calculatesElevationOnThreePoints()
	{
		assertEquals(-15d/17d, elevationInterpolator.calculateElevation(0, 0, 1, 2, 3, 4, 6, 9, 12, 11, 9), PRECISION);
		assertEquals(15d, elevationInterpolator.calculateElevation(10, 0, 0, 0, 0, 10, 10, 10, 10, -10, 20), PRECISION);
		assertEquals(5, elevationInterpolator.calculateElevation(5, 5, 0, 0, 0, 10, 10, 10, 20, 20, 20), PRECISION);
	}
	
	
	@Test
	public void calculatesElevationOnNPoints()	{
		assertEquals(0, elevationInterpolator.calculateElevation(5, 5, 
				Helper.createPointList3D(
						0, 0, 0,
						10, 0, 0,
						10, 10, 0,
						0, 10, 0)), PRECISION);
		assertEquals(10, elevationInterpolator.calculateElevation(5, 5, 
				Helper.createPointList3D(
						0, 0, 0,
						10, 0, 10,
						10, 10, 20,
						0, 10, 10)), PRECISION);
		assertEquals(5, elevationInterpolator.calculateElevation(5, 5, 
				Helper.createPointList3D(
						0, 0, 0,
						10, 0, 10,
						10, 10, 0,
						0, 10, 10)), PRECISION);
		assertEquals(2.6470588235, elevationInterpolator.calculateElevation(2.5, 2.5, 
				Helper.createPointList3D(
						0, 0, 0,
						10, 0, 10,
						10, 10, 0,
						0, 10, 10)), PRECISION);
		assertEquals(0.0040787192, elevationInterpolator.calculateElevation(0.1, 0.1, 
				Helper.createPointList3D(
						0, 0, 0,
						10, 0, 10,
						10, 10, 0,
						0, 10, 10)), PRECISION);
		assertEquals(0, elevationInterpolator.calculateElevation(ElevationInterpolator.EPSILON/2, ElevationInterpolator.EPSILON/2, 
				Helper.createPointList3D(
						0, 0, 0,
						10, 0, 10,
						10, 10, 0,
						0, 10, 10)), PRECISION);
		assertEquals(0, elevationInterpolator.calculateElevation(5, 0, 
				Helper.createPointList3D(
						0, 0, 0,
						10, 1, 10,
						10, -1, -10,
						20, 0, 0)), PRECISION);
	}

}
