package com.graphhopper.util.shapes;

import com.graphhopper.util.shapes.Polygon;
import org.junit.Test;

import static org.junit.Assert.*;


public class PolygonTest {

    @Test
    public void testContains(){

        
      Polygon square = new Polygon(new double[]{0,0,10,10}, new double[]{0,10,10,0});
    assertTrue(square.contains(5,5));
    assertTrue(square.contains(8,5));
    assertFalse(square.contains(5,-10));
    assertTrue(square.contains(5,0));
    assertFalse(square.contains(5,10));
    assertTrue(square.contains(5,8));
    assertFalse(square.contains(10,10));



    }

}
