/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
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
public abstract class AbstractDirectoryTester
{
    protected String location = "./target/tmp/dir";
    private DataAccess da;

    abstract Directory createDir();

    @After
    public void tearDown()
    {
        if (da != null)
            da.close();
        Helper.removeDir(new File(location));
    }

    @Before
    public void setUp()
    {
        Helper.removeDir(new File(location));
    }

    @Test
    public void testRequestedDataAccessHasToBeTheIdenticalType()
    {
        Directory dir = createDir();
        da = dir.find("testing", new DAType(DAType.MemRef.HEAP, false, false, false, false));
        try
        {
            dir.find("testing", new DAType(DAType.MemRef.HEAP, false, false, false, true));
            assertFalse(true);
        } catch (Exception ex)
        {
        }
    }

    @Test
    public void testSynched()
    {
        Directory dir = createDir();
        DataAccess da1 = dir.find("testing", new DAType(DAType.MemRef.HEAP, false, false, true, false));
        da = dir.find("testing_synched", new DAType(DAType.MemRef.HEAP, false, false, true, true));
        assertFalse(da.getClass() == da1.getClass());
        da1.close();
    }

    @Test
    public void testNoDuplicates()
    {
        Directory dir = createDir();
        DataAccess da1 = dir.find("testing");
        DataAccess da2 = dir.find("testing");
        assertTrue(da1 == da2);
        da1.close();
        da2.close();
    }

    @Test
    public void testNoErrorForDACreate()
    {
        Directory dir = createDir();
        da = dir.find("testing");
        da.create(100);
        da.flush();
    }
}
