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
import org.junit.Before;

/**
 * @author Peter Karich
 */
public class GraphStorageViaMMapTest extends AbstractGraphTester {

    @Override
    public GraphStorage createGraph(int size) {
        return new GraphStorage(new MMapDirectory(location)).setSegmentSize(size / 2).createNew(size);
    }

    @Before
    public void tearDown() {
        Helper.deleteDir(new File(location));
    }

    @Before
    public void setUp() {
        Helper.deleteDir(new File(location));
    }
}
