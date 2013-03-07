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
package com.graphhopper.storage.index;

import com.graphhopper.storage.Directory;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.MMapDirectory;
import com.graphhopper.storage.RAMDirectory;
import static com.graphhopper.storage.index.Location2IDQuadtreeTest.*;
import java.io.File;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author Peter Karich
 */
public class Location2IDPreciseIndexTest extends AbstractLocation2IDIndexTester {

    @Override
    public Location2IDIndex createIndex(Graph g, int resolution) {
        Directory dir = new MMapDirectory(location);
        return new Location2IDPreciseIndex(g, dir).prepareIndex(resolution);
    }

    @Test
    public void testSave() {
        File file = new File(location);
        file.mkdirs();

        Graph g = createSampleGraph();
        Location2IDPreciseIndex idx = new Location2IDPreciseIndex(g, new RAMDirectory(location, true));
        idx.prepareIndex(8);
        assertIndex(idx);
        idx.flush();

        idx = new Location2IDPreciseIndex(g, new RAMDirectory(location, true));
        assertTrue(idx.loadExisting());
        assertIndex(idx);

        // throw exception if load is made with a wrong graph => store node count into header as check sum
        g.setNode(g.nodes(), 12, 23);
        idx = new Location2IDPreciseIndex(g, new RAMDirectory(location, true));
        try {
            idx.loadExisting();
            assertTrue(false);
        } catch (Exception ex) {
        }
    }

    private void assertIndex(Location2IDIndex idx) {
        // maxWidth is ~555km and with size==8 it will be exanded to 4*4 array => maxRasterWidth==555/4
        // assertTrue(idx.getMaxRasterWidthKm() + "", idx.getMaxRasterWidthKm() < 140);
        assertEquals(1, idx.findID(1.637, 2.23));
        assertEquals(10, idx.findID(3.649, 1.375));
        assertEquals(9, idx.findID(3.3, 2.2));
        assertEquals(6, idx.findID(3.0, 1.5));
    }

    @Override
    public void testSimpleGraph() {
        // skip for now
    }

    @Override
    public void testSimpleGraph2() {
        // skip for now
    }

    @Override
    public void testSinglePoints32() {
        // skip for now
    }
}
