/*
 *  Copyright 2012 Peter Karich 
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.storage;

import com.graphhopper.util.Helper;
import java.io.File;
import org.junit.After;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author Peter Karich
 */
public class RAMDirectoryTest {

    String location = "./tmp/ramdir";
    File file = new File(location);

    @After
    @Before
    public void setUp() {
        Helper.deleteDir(file);
    }

    @Test
    public void testDataAccessSingletons() {
        RAMDirectory dir = new RAMDirectory();
        dir.createDataAccess("testing");
        try {
            dir.createDataAccess("testing");
            assertTrue(false);
        } catch (Exception ex) {
        }
    }

//    @Test
//    public void testClear() {
//        file.mkdirs();
//        File fileContent = new File(location + "/anotherone");
//        fileContent.mkdirs();
//        RAMDirectory dir = new RAMDirectory(location);
//        dir.createDataAccess("testing");
//        dir.clear();
//        assertTrue(file.exists());
//        assertTrue(fileContent.exists());
//        // no error
//        dir.createDataAccess("testing");
//
//        dir = new RAMDirectory(location, true);
//        // make sure directory content does not get deleted - load existing should work!
//        assertTrue(fileContent.exists());
//        dir.createDataAccess("testing");
//        dir.clear();
//        assertTrue(file.exists());
//        assertFalse(fileContent.exists());
//        // no error
//        dir.createDataAccess("testing");
//    }
    @Test
    public void testNoErrorForDACreate() {
        RAMDirectory dir = new RAMDirectory(location, true);
        DataAccess da = dir.createDataAccess("testing");
        da.createNew(100);
        da.flush();
    }
}
