/*
 *  Licensed to Peter Karich under one or more contributor license 
 *  agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  Peter Karich licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except 
 *  in compliance with the License. You may obtain a copy of the 
 *  License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.util;

import java.io.File;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Before;

/**
 * 
 * @author Peter Karich
 */
public class HelperTest {

    @Before
    public void setUp() {
        Helper.removeDir(new File("test"));
    }

    @After
    public void tearDown() {
        Helper.removeDir(new File("test"));
    }

    @Test
    public void testUnzip() throws Exception {
        String to = "./target/tmp/test";
        Helper.removeDir(new File(to));
        Helper.unzip("./src/test/resources/com/graphhopper/util/test.zip", to, false);
        assertTrue(new File("./target/tmp/test/file2 bäh").exists());
        assertTrue(new File("./target/tmp/test/folder1").isDirectory());
        assertTrue(new File("./target/tmp/test/folder1/folder 3").isDirectory());
        Helper.removeDir(new File(to));
    }

    @Test
    public void testLongToInt() {
        long numberLng = 8746822910082L;

        int numberIntLeft = Helper.longToIntLeft(numberLng);
        int numberIntRight = Helper.longToIntRight(numberLng);

        assertEquals(0x000007F4, numberIntLeft);
        assertEquals(0x8745C082, numberIntRight);

        long numberLngBack = Helper.intToLong(numberIntLeft, numberIntRight);

        assertEquals(numberLng, numberLngBack);
    }

    @Test
    public void testLongToIntNegative() {
        long numberLng = -982739918826609783L;

        int numberIntLeft = Helper.longToIntLeft(numberLng);
        int numberIntRight = Helper.longToIntRight(numberLng);

        assertEquals(0xF25C9B40, numberIntLeft);
        assertEquals(0x27BE6389, numberIntRight);

        long numberLngBack = Helper.intToLong(numberIntLeft, numberIntRight);

        assertEquals(numberLng, numberLngBack);
    }

    @Test
    public void testLongToIntSimpleNumber() {
        long numberLng = 13;

        int numberIntLeft = Helper.longToIntLeft(numberLng);
        int numberIntRight = Helper.longToIntRight(numberLng);

        assertEquals(0x00000000, numberIntLeft);
        assertEquals(0x0000000D, numberIntRight);

        long numberLngBack = Helper.intToLong(numberIntLeft, numberIntRight);

        assertEquals(numberLng, numberLngBack);
    }
}
