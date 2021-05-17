/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.graphhopper.util.shapes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertTrue(square.contains(10,0.1));
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
