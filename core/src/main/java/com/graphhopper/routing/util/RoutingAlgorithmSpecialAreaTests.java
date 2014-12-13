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
import com.graphhopper.routing.AlgorithmOptions;
import com.graphhopper.routing.RoutingAlgorithm;
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.routing.util.TestAlgoCollector.AlgoHelperEntry;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.LevelGraph;
import com.graphhopper.util.StopWatch;
import com.graphhopper.routing.util.TestAlgoCollector.OneRun;
import com.graphhopper.storage.*;
import com.graphhopper.storage.index.LocationIndexTreeSC;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Graph unterfrankenGraph;
    private final LocationIndex idx;

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
            throw new IllegalStateException("run testAlgos only with a none-LevelGraph. Use prepare.chWeighting=no "
                    + "Or use prepare.chWeighting=shortest and avoid the preparation");
        }

        TestAlgoCollector testCollector = new TestAlgoCollector("testAlgos");
        final EncodingManager encodingManager = new EncodingManager("CAR", 4);
        CarFlagEncoder carEncoder = (CarFlagEncoder) encodingManager.getEncoder("CAR");
        boolean ch = true;
        Collection<AlgoHelperEntry> prepares = createAlgos(unterfrankenGraph, idx,
                carEncoder, ch, TraversalMode.NODE_BASED, new ShortestWeighting(), encodingManager);
        EdgeFilter ef = new DefaultEdgeFilter(carEncoder);

        for (AlgoHelperEntry entry : prepares)
        {
            int failed = testCollector.errors.size();

            OneRun or = new OneRun(50.0314, 10.5105, 50.0303, 10.5070, 571, 22);
            testCollector.assertDistance(entry, or.getList(idx, ef), or);
            or = new OneRun(49.51451, 9.967346, 50.2920, 10.4650, 107909, 1929);
            testCollector.assertDistance(entry, or.getList(idx, ef), or);
            or = new OneRun(50.0780, 9.1570, 49.5860, 9.9750, 95562, 1556);
            testCollector.assertDistance(entry, or.getList(idx, ef), or);
            or = new OneRun(50.2800, 9.7190, 49.8960, 10.3890, 81016, 1724);
            testCollector.assertDistance(entry, or.getList(idx, ef), or);
            or = new OneRun(49.8020, 9.2470, 50.4940, 10.1970, 134767, 2295);
            testCollector.assertDistance(entry, or.getList(idx, ef), or);
            or = new OneRun(49.72449, 9.23482, 50.4140, 10.2750, 140809, 2680);
            testCollector.assertDistance(entry, or.getList(idx, ef), or);
            or = new OneRun(50.1100, 10.7530, 49.6500, 10.3410, 77381, 1863);
            testCollector.assertDistance(entry, or.getList(idx, ef), or);

            System.out.println("unterfranken " + entry + ", " + (testCollector.errors.size() - failed) + " failed");
        }

        testCollector.printSummary();
    }

    public static Collection<AlgoHelperEntry> createAlgos( Graph g,
            LocationIndex idx, final FlagEncoder encoder, boolean withCh,
            final TraversalMode tMode, final Weighting weighting, final EncodingManager manager )
    {
        List<AlgoHelperEntry> prepare = new ArrayList<AlgoHelperEntry>();
        prepare.add(new AlgoHelperEntry(g, new AlgorithmOptions(AlgorithmOptions.ASTAR, encoder, weighting, tMode), idx));
        // later: include dijkstraOneToMany        
        prepare.add(new AlgoHelperEntry(g, new AlgorithmOptions(AlgorithmOptions.DIJKSTRA, encoder, weighting, tMode), idx));

        final AlgorithmOptions astarbiOpts = new AlgorithmOptions(AlgorithmOptions.ASTAR_BI, encoder, weighting, tMode);
        astarbiOpts.getHints().put(AlgorithmOptions.ASTAR_BI + ".approximation", "BeelineSimplification");
        final AlgorithmOptions dijkstrabiOpts = new AlgorithmOptions(AlgorithmOptions.DIJKSTRA_BI, encoder, weighting, tMode);
        prepare.add(new AlgoHelperEntry(g, astarbiOpts, idx));
        prepare.add(new AlgoHelperEntry(g, dijkstrabiOpts, idx));

        if (withCh)
        {
            final LevelGraph graphCH = (LevelGraph) ((GraphStorage) g).copyTo(new GraphBuilder(manager).
                    set3D(g.getNodeAccess().is3D()).levelGraphCreate());
            final PrepareContractionHierarchies prepareCH = new PrepareContractionHierarchies(graphCH, encoder, weighting, tMode);
            prepareCH.doWork();
            LocationIndex idxCH = new LocationIndexTreeSC(graphCH, new RAMDirectory()).prepareIndex();
            prepare.add(new AlgoHelperEntry(graphCH, dijkstrabiOpts, idxCH)
            {
                @Override
                public RoutingAlgorithm createAlgo( Graph qGraph )
                {
                    return prepareCH.createAlgo(qGraph, dijkstrabiOpts);
                }
            });

            prepare.add(new AlgoHelperEntry(graphCH, astarbiOpts, idxCH)
            {
                @Override
                public RoutingAlgorithm createAlgo( Graph qGraph )
                {
                    return prepareCH.createAlgo(qGraph, astarbiOpts);
                }
            });
        }
        return prepare;
    }

    void testIndex()
    {
        TestAlgoCollector testCollector = new TestAlgoCollector("testIndex");
        testCollector.queryIndex(unterfrankenGraph, idx, 50.080539, 10.125854, 63.35);
        testCollector.queryIndex(unterfrankenGraph, idx, 50.081146, 10.124496, 0.0);
        testCollector.queryIndex(unterfrankenGraph, idx, 49.68243, 9.933271, 436.29);
        testCollector.queryIndex(unterfrankenGraph, idx, 50.066495, 10.191836, 14.63);

        testCollector.printSummary();
    }
}
