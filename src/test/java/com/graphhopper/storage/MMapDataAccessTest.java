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

import static org.junit.Assert.*;
import org.junit.Test;

/**
 * @author Peter Karich
 */
public class MMapDataAccessTest extends DataAccessTest {

    @Override
    public DataAccess createDataAccess(String location) {
        return new MMapDataAccess(location, location).setSegmentSize(128);
    }

    @Test
    public void textMixRAM2MMAP() {
        DataAccess da = new RAMDataAccess(location, location, true);
        assertFalse(da.loadExisting());
        da.createNew(100);
        da.setInt(7, 123);
        da.flush();
        da.close();
        da = createDataAccess(location);
        assertTrue(da.loadExisting());
        assertEquals(123, da.getInt(7));
    }

    @Test
    public void textMixMMAP2RAM() {
        DataAccess da = createDataAccess(location);
        assertFalse(da.loadExisting());
        da.createNew(100);
        da.setInt(7, 123);
        
        // TODO "memory mapped flush" is expensive and not required. only writing the header is required.
        da.flush();
        da.close();
        da = new RAMDataAccess(location, location, true);
        assertTrue(da.loadExisting());
        assertEquals(123, da.getInt(7));
    }
}
