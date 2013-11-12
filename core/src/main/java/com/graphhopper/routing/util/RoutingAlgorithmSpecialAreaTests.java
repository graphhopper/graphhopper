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
package com.graphhopper.routing.util;

import com.graphhopper.GraphHopper;
import com.graphhopper.coll.MapEntry;
import com.graphhopper.routing.AStarBidirection;
import com.graphhopper.routing.RoutingAlgorithm;
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.index.Location2IDIndex;
import com.graphhopper.storage.LevelGraph;
import com.graphhopper.util.StopWatch;
import static com.graphhopper.routing.util.NoOpAlgorithmPreparation.*;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.storage.index.Location2NodesNtreeLG;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration tests for one bigger area - at the moment Unterfranken (Germany). Execute via
 * ./graphhopper.sh test unterfranken.osm
 * <p/>
 * @author Peter Karich
 */
public class RoutingAlgorithmSpecialAreaTests
{
    private Logger logger = LoggerFactory.getLogger(getClass());
    private final Graph unterfrankenGraph;
    private final Location2IDIndex idx;

    public RoutingAlgorithmSpecialAreaTests( GraphHopper graphhopper )
    {
        this.unterfrankenGraph = graphhopper.getGraph();
        StopWatch sw = new StopWatch().start();
        idx = graphhopper.getLocationIndex();
        logger.info(idx.getClass().getSimpleName() + " index. Size:"
                + (float) idx.getCapacity() / (1 << 20) + " MB, took:" + sw.stop().getSeconds());
    }

    public void start()
    {
        testIndex();
        testAlgos();
    }

    void testAlgos()
    {
        if (unterfrankenGraph instanceof LevelGraph)
        {
            throw new IllegalStateException("run testAlgos only with a none-LevelGraph. Use prepare.chShortcuts=false "
                    + "Or use prepare.chShortcuts=shortest and avoid the preparation");
        }

        TestAlgoCollector testCollector = new TestAlgoCollector("testAlgos");
        final EncodingManager encodingManager = new EncodingManager("CAR");
        CarFlagEncoder carEncoder = (CarFlagEncoder) encodingManager.getEncoder("CAR");
        boolean ch = true;
        Collection<Entry<AlgorithmPreparation, Location2IDIndex>> prepares = createAlgos(unterfrankenGraph, idx,
                carEncoder, ch, new ShortestCalc(), encodingManager);
        EdgeFilter ef = new DefaultEdgeFilter(carEncoder);

        for (Entry<AlgorithmPreparation, Location2IDIndex> entry : prepares)
        {
            AlgorithmPreparation prepare = entry.getKey();
            Location2IDIndex currIdx = entry.getValue();
            int failed = testCollector.errors.size();

            testCollector.assertDistance(prepare.createAlgo(),
                    currIdx.findClosest(50.0314, 10.5105, ef), currIdx.findClosest(50.0303, 10.5070, ef), 570, 22);
            testCollector.assertDistance(prepare.createAlgo(),
                    currIdx.findClosest(49.51451, 9.967346, ef), currIdx.findClosest(50.2920, 10.4650, ef), 107544, 1673);
            testCollector.assertDistance(prepare.createAlgo(),
                    currIdx.findClosest(50.0780, 9.1570, ef), currIdx.findClosest(49.5860, 9.9750, ef), 92770, 1285);
            testCollector.assertDistance(prepare.createAlgo(),
                    currIdx.findClosest(50.2800, 9.7190, ef), currIdx.findClosest(49.8960, 10.3890, ef), 77446, 1302);
            testCollector.assertDistance(prepare.createAlgo(),
                    currIdx.findClosest(49.8020, 9.2470, ef), currIdx.findClosest(50.4940, 10.1970, ef), 125567, 2232);
            testCollector.assertDistance(prepare.createAlgo(),
                    currIdx.findClosest(49.72449, 9.23482, ef), currIdx.findClosest(50.4140, 10.2750, ef), 137330, 2350);
            testCollector.assertDistance(prepare.createAlgo(),
                    currIdx.findClosest(50.1100, 10.7530, ef), currIdx.findClosest(49.6500, 10.3410, ef), 74049, 1369);

            System.out.println("unterfranken " + prepare.createAlgo() + ": " + (testCollector.errors.size() - failed) + " failed");
        }

        testCollector.printSummary();
    }

    private static class ME extends MapEntry<AlgorithmPreparation, Location2IDIndex>
    {
        public ME( AlgorithmPreparation ap, Location2IDIndex idx )
        {
            super(ap, idx);
        }
    }

    public static Collection<Entry<AlgorithmPreparation, Location2IDIndex>> createAlgos( Graph g,
            Location2IDIndex idx, FlagEncoder encoder, boolean withCh, WeightCalculation weightCalc, EncodingManager manager )
    {
        // List<Entry<AlgorithmPreparation, Location2IDIndex>> prepare = new ArrayList<Entry<AlgorithmPreparation, Location2IDIndex>>();
        List<Entry<AlgorithmPreparation, Location2IDIndex>> prepare = new ArrayList<Entry<AlgorithmPreparation, Location2IDIndex>>(
                Arrays.<Entry<AlgorithmPreparation, Location2IDIndex>>asList(
                        new ME(createAlgoPrepare(g, "astar", encoder, weightCalc), idx),
                        // new MapEntry<AlgorithmPreparation, Location2IDIndex>(createAlgoPrepare(g, "dijkstraOneToMany", encoder, weightCalc),
                        new ME(createAlgoPrepare(g, "astarbi", encoder, weightCalc), idx),
                        new ME(createAlgoPrepare(g, "dijkstraNative", encoder, weightCalc), idx),
                        new ME(createAlgoPrepare(g, "dijkstrabi", encoder, weightCalc), idx),
                        new ME(createAlgoPrepare(g, "dijkstra", encoder, weightCalc), idx)));
        if (withCh)
        {
            LevelGraph graphCH = (LevelGraph) g.copyTo(new GraphBuilder(manager).levelGraphCreate());
            PrepareContractionHierarchies prepareCH = new PrepareContractionHierarchies(encoder, weightCalc).
                    setGraph(graphCH);
            prepareCH.doWork();
            Location2IDIndex idxCH = new Location2NodesNtreeLG(graphCH, new RAMDirectory()).prepareIndex();
            prepare.add(new ME(prepareCH, idxCH));

            // still one failing test regardless of the approx factor
//            PrepareContractionHierarchies prepareCHAStar = new PrepareContractionHierarchies(encoder, weightCalc) {
//
//                @Override
//                public RoutingAlgorithm createAlgo()
//                {
//                    return createAStar().setApproximation(true).setApproximationFactor(0.9);
//                }
//            }.setGraph(graphCH);            
//            prepare.add(new ME(prepareCHAStar, idxCH));
        }
        return prepare;
    }

    void testIndex()
    {
        TestAlgoCollector testCollector = new TestAlgoCollector("testIndex");
        testCollector.queryIndex(unterfrankenGraph, idx, 50.081241, 10.124366, 11.09);
        testCollector.queryIndex(unterfrankenGraph, idx, 50.081146, 10.124496, 0.0);
        testCollector.queryIndex(unterfrankenGraph, idx, 49.682000, 9.943000, 228.32);
        testCollector.queryIndex(unterfrankenGraph, idx, 50.066495, 10.191836, 14.63);

        testCollector.printSummary();
    }
}
