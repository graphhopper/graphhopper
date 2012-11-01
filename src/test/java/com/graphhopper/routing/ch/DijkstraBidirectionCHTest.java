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
package com.graphhopper.routing.ch;

import com.graphhopper.routing.AbstractRoutingAlgorithmTester;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.util.CarStreetType;
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.routing.util.WeightCalculation;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.LevelGraph;
import com.graphhopper.storage.LevelGraphStorage;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.util.EdgeSkipIterator;
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

    // graph is expensive to create and to prepare!
    private static Graph preparedMatrixGraph;

    @Override public Graph getMatrixGraph() {
        if (preparedMatrixGraph == null) {
            LevelGraph lg = createGraph(getMatrixAlikeGraph().getNodes());
            getMatrixAlikeGraph().copyTo(lg);
            prepareGraph(lg);
            preparedMatrixGraph = lg;
        }
        return preparedMatrixGraph;
    }

    @Override
    protected LevelGraph createGraph(int size) {
        LevelGraphStorage lg = new LevelGraphStorage(new RAMDirectory());
        lg.createNew(size);
        return lg;
    }

    @Override
    public PrepareContractionHierarchies prepareGraph(Graph g, WeightCalculation calc) {
        PrepareContractionHierarchies ch = new PrepareContractionHierarchies().
                setGraph(g).setType(calc);
        // prepare matrixgraph only once
        if (g != preparedMatrixGraph)
            ch.doWork();
        return ch;
    }

    @Test
    public void testShortcutUnpacking() {
        LevelGraph g2 = createGraph(6);
        AbstractRoutingAlgorithmTester.initBiGraph(g2);
        Path p = prepareGraph(g2).createAlgo().calcPath(0, 4);
        assertEquals(p.toString(), 51, p.weight(), 1e-4);
        assertEquals(p.toString(), 6, p.nodes());
    }

    @Test @Override public void testPerformance() throws IOException {
        // TODO hmmh preparation takes a bit tooo long
        // super.testPerformance();
    }

    @Test
    public void testPathRecursiveUnpacking() {
        LevelGraphStorage g2 = (LevelGraphStorage) createGraph(6);

        g2.newEdge(0, 1, 1, true);
        EdgeSkipIterator iter = g2.newEdge(0, 2, 1.4, true);
        g2.newEdge(1, 2, 1, true);
        g2.newEdge(1, 3, 3, true);
        g2.newEdge(2, 3, 1, true);
        g2.newEdge(4, 3, 1, true);
        g2.newEdge(2, 5, 1.4, true);
        g2.newEdge(3, 5, 1, true);
        g2.newEdge(5, 6, 1, true);
        g2.newEdge(4, 6, 1, true);
        g2.newEdge(5, 7, 1.4, true);
        g2.newEdge(6, 7, 1, true);

        // simulate preparation
        EdgeSkipIterator iter2 = g2.shortcut(0, 5, 2.8, CarStreetType.flags(0, true), iter.edge());
        g2.shortcut(0, 7, 4.2, CarStreetType.flags(0, true), iter2.edge());
        g2.setLevel(1, 0);
        g2.setLevel(3, 1);
        g2.setLevel(4, 2);
        g2.setLevel(6, 3);
        g2.setLevel(2, 4);
        g2.setLevel(5, 5);
        g2.setLevel(7, 6);
        g2.setLevel(0, 7);

        Path p = new PrepareContractionHierarchies().setGraph(g2).createAlgo().calcPath(0, 7);
        assertEquals(4, p.nodes());
        assertEquals(4.2, p.distance(), 1e-5);
    }
}
