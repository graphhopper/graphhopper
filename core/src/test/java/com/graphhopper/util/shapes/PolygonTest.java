package com.graphhopper.util.shapes;

import com.graphhopper.routing.util.spatialrules.Polygon;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PolygonTest {

    @Test
    public void testContains(){

        /*
         * |----|
         * |    |
         * |----|
         */
        com.graphhopper.routing.util.spatialrules.Polygon square = new com.graphhopper.routing.util.spatialrules.Polygon(new double[]{0,0,20,20}, new double[]{0,20,20,0});
        assertTrue(square.contains(10,10));
        assertTrue(square.contains(16,10));
        assertFalse(square.contains(10,-20));
        assertTrue(square.contains(10,0));
        assertFalse(square.contains(10,20));
        assertTrue(square.contains(10,16));
        assertFalse(square.contains(20,20));

        /*
         * \-----|
         *   --| |
         *   --| |
         *  /----|
         */
        com.graphhopper.routing.util.spatialrules.Polygon squareHole = new com.graphhopper.routing.util.spatialrules.Polygon(new double[]{0,0,20,20,15,15,5,5}, new double[]{0,20,20,0,5,15,15,5});
        assertFalse(squareHole.contains(10,10));
        assertTrue(squareHole.contains(16,10));
        assertFalse(squareHole.contains(10,-20));
        assertFalse(squareHole.contains(10,0));
        assertFalse(squareHole.contains(10,20));
        assertTrue(squareHole.contains(10,16));
        assertFalse(squareHole.contains(20,20));



        /*
         * |----|
         * |    |
         * |----|
         */
        square = new com.graphhopper.routing.util.spatialrules.Polygon(new double[]{1, 1, 2, 2}, new double[]{1, 2, 2, 1});

        assertTrue(square.contains(1.5,1.5));
        assertFalse(square.contains(0.5,1.5));

        /*
         * |----|
         * | /\ |
         * |/  \|
         */
        squareHole = new Polygon(new double[]{1, 1, 2, 1.1, 2}, new double[]{1, 2, 2, 1.5, 1});

        assertTrue(squareHole.contains(1.1,1.1));
        assertFalse(squareHole.contains(1.5,1.5));
        assertFalse(squareHole.contains(0.5,1.5));

    }
}
