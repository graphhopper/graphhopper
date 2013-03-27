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
import com.graphhopper.storage.LevelGraph;
import com.graphhopper.storage.LevelGraphStorage;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeSkipIterator;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class Location2NodesNtreeLGTest extends Location2NodesNtreeTest {

    @Override
    public Location2IDIndex createIndex(Graph g, int resolution) {
        Directory dir = new RAMDirectory(location);
        return new Location2NodesNtreeLG((LevelGraph) g, dir).subEntries(4).resolution(1000000).prepareIndex();
    }

    @Override
    LevelGraph createGraph(Directory dir) {
        return new LevelGraphStorage(dir).createNew(100);
    }

    @Test
    public void testLevelGraph() {
        LevelGraph g = createGraph(new RAMDirectory());
        // 0
        // 1
        // 2
        //  3
        //   4

        g.setNode(0, 1, 0);
        g.setNode(1, 0.5, 0);
        g.setNode(2, 0, 0);
        g.setNode(3, -1, 1);
        g.setNode(4, -2, 2);

        EdgeIterator iter1 = g.edge(0, 1, 10, true);
        EdgeIterator iter2 = g.edge(1, 2, 10, true);
        EdgeIterator iter3 = g.edge(2, 3, 14, true);
        EdgeIterator iter4 = g.edge(3, 4, 14, true);

        // create shortcuts
        EdgeSkipIterator iter5 = g.edge(0, 2, 20, true);
        iter5.skippedEdges(iter1.edge(), iter2.edge());
        EdgeSkipIterator iter6 = g.edge(2, 4, 28, true);
        iter6.skippedEdges(iter3.edge(), iter4.edge());
        g.edge(0, 4, 40, true).skippedEdges(iter5.edge(), iter6.edge());

        Location2IDIndex index = createIndex(g, -1);
        assertEquals(2, index.findID(0, 0.5));
    }
}
