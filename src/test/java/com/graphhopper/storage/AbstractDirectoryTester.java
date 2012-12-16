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
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public abstract class AbstractDirectoryTester {

    protected String location = "./target/tmp/ramdir";

    abstract Directory createDir();

    @After
    @Before
    public void setUp() {
        Helper.deleteDir(new File(location));
    }

    @Test
    public void testRename() {
        Directory dir = createDir();
        DataAccess da = dir.findCreate("testing");
        da.createNew(100);
        da.flush();
        dir.rename(da, "newtesting");
    }

    @Test
    public void testNoDuplicates() {
        Directory dir = createDir();
        DataAccess da1 = dir.findCreate("testing");
        DataAccess da2 = dir.findCreate("testing");
        assertTrue(da1 == da2);
    }

    @Test
    public void testNoErrorForDACreate() {
        Directory dir = createDir();
        DataAccess da = dir.findCreate("testing");
        da.createNew(100);
        da.flush();
    }
}
