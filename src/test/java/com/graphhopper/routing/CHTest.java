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

import com.graphhopper.routing.util.CarStreetType;
import com.graphhopper.routing.util.EdgeLevelFilter;
import com.graphhopper.routing.util.PrepareContractionHierarchies;
import com.graphhopper.routing.util.WeightCalculation;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.LevelGraph;
import com.graphhopper.storage.LevelGraphStorage;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.util.EdgeIterator;
import java.io.IOException;
import org.junit.Test;

/**
 *
 * @author Peter Karich
 */
public class CHTest extends AbstractRoutingAlgorithmTester {

    @Override
    Graph createGraph(int size) {
        LevelGraphStorage lg = new LevelGraphStorage(new RAMDirectory());
        return lg.createNew(size);
    }

    @Override public RoutingAlgorithm createAlgo(Graph g) {
        LevelGraph lg = (LevelGraph) g;
        PrepareContractionHierarchies ch = new PrepareContractionHierarchies(lg);
        ch.doWork();
        return new DijkstraBidirectionRef(lg) {
            @Override public RoutingAlgorithm setType(WeightCalculation wc) {
                // ignore changing of type
                return this;
            }

            @Override protected PathBidirRef createPath() {
                WeightCalculation wc = new WeightCalculation() {
                    @Override
                    public double getWeight(EdgeIterator iter) {
                        return iter.distance() * CarStreetType.getSpeedPart(iter.flags());
                    }

                    @Override public double apply(double currDistToGoal) {
                        throw new UnsupportedOperationException();
                    }

                    @Override public double apply(double currDistToGoal, int flags) {
                        throw new UnsupportedOperationException();
                    }

                    @Override public String toString() {
                        return "INVERSE";
                    }
                };
                return new Path4Level(graph, wc);
            }
        }.setEdgeFilter(new EdgeLevelFilter(lg));
    }

    @Test @Override
    public void testPerformance() throws IOException {
        // TODO introduce a prepareGraph method to be overriden otherwise it takes a lot time!
    }

    @Test public void testCalcFastestPath() {
        // TODO how to make a difference between fast and shortest preparation?
    }

    @Test
    public void testRekeyBugOfIntBinHeap() {
        // TODO make createMatrixGraph overwritable
    }

    @Test
    public void testBug1() {
        // TODO make createMatrixGraph overwritable
    }
}
