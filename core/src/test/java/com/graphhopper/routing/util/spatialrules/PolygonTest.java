package com.graphhopper.routing.util.spatialrules;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Robin Boldt
 */
public class PolygonTest {

    @Test
    public void testContains(){

        /*
         * |----|
         * |    |
         * |----|
         */
        Polygon square = new Polygon(new double[]{0,0,20,20}, new double[]{0,20,20,0});
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
        Polygon squareHole = new Polygon(new double[]{0,0,20,20,15,15,5,5}, new double[]{0,20,20,0,5,15,15,5});
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
        square = new Polygon(new double[]{1, 1, 2, 2}, new double[]{1, 2, 2, 1});

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
