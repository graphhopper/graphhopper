package com.graphhopper.reader.dem;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

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

}
