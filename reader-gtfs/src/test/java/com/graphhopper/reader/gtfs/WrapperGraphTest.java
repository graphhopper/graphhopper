/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
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

package com.graphhopper.reader.gtfs;

import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FootFlagEncoder;
import com.graphhopper.storage.GraphExtension;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.RAMDirectory;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class WrapperGraphTest {

    private final PtFlagEncoder pt;
    private final FootFlagEncoder foot;
    private final EncodingManager encodingManager;

    public WrapperGraphTest() {
        pt = new PtFlagEncoder();
        foot = new FootFlagEncoder();
        encodingManager = EncodingManager.create(Arrays.asList(pt, foot), 8);
    }

    @Test
    public void testEternalOffByOneError() {
        GraphHopperStorage graph = new GraphHopperStorage(new RAMDirectory("wurst"), encodingManager, false, new GraphExtension.NoOpExtension());
        assertEquals(0, graph.getNodes());
        assertEquals(0, graph.getAllEdges().length());
        WrapperGraph wrapperGraph = new WrapperGraph(graph, Collections.emptyList());
        assertEquals(0, wrapperGraph.getNodes());
        assertEquals(0, wrapperGraph.getAllEdges().length());
    }

}
