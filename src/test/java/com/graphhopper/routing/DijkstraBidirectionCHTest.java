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
package com.graphhopper.routing;

import com.graphhopper.routing.util.PrepareContractionHierarchies;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.LevelGraph;
import com.graphhopper.storage.LevelGraphStorage;
import com.graphhopper.storage.RAMDirectory;
import java.io.IOException;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 * Tests if a graph optimized by contraction hierarchies returns the same results as a none
 * optimized one. Additionally fine grained path unpacking is tested.
 *
 * @author Peter Karich
 */
public class DijkstraBidirectionCHTest extends AbstractRoutingAlgorithmTester {

    private static Graph preparedMatrixGraph;

    @Override public Graph getMatrixGraph() {
        if (preparedMatrixGraph == null) {
            LevelGraph lg = createGraph(matrixGraph.getNodes());
            matrixGraph.copyTo(lg);
            preparedMatrixGraph = prepareGraph(lg);
        }
        
        return preparedMatrixGraph;
    }

    @Override
    LevelGraph createGraph(int size) {
        LevelGraphStorage lg = new LevelGraphStorage(new RAMDirectory());
        lg.createNew(size);
        return lg;
    }

    @Override public RoutingAlgorithm createAlgo(Graph g) {
        return new PrepareContractionHierarchies((LevelGraph) g).createDijkstraBi();
    }

    @Override
    public LevelGraph prepareGraph(Graph g) {
        PrepareContractionHierarchies ch = new PrepareContractionHierarchies((LevelGraph) g);
        ch.doWork();
        return (LevelGraph) g;
    }

    @Test
    public void testShortcutUnpacking() {
        LevelGraph g2 = createGraph(6);
        AbstractRoutingAlgorithmTester.initBiGraph(g2);
        g2 = prepareGraph(g2);
        Path p = createAlgo(g2).calcPath(0, 4);
        assertEquals(p.toString(), 51, p.weight(), 1e-4);
        assertEquals(p.toString(), 6, p.locations());
    }

    @Test public void testCalcFastestPath() {
        // TODO how to make a difference between fast and shortest preparation?
    }

    @Test @Override public void testPerformance() throws IOException {
        // TODO why does it take so long?
    }

    @Test
    public void testPathUnpacking() {
        LevelGraph g2 = createGraph(6);
        prepareGraph(g2);

        // TODO recursively unpack the graph (difference to simple PrepareShortcuts)
        // TODO will fail while unpacking
    }
}
