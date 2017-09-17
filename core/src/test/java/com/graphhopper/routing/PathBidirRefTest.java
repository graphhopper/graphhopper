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
package com.graphhopper.routing;

import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.profiles.TagParserFactory;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.SPTEntry;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.Helper;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Peter Karich
 */
public class PathBidirRefTest {
    private final EncodingManager encodingManager = new EncodingManager.Builder().addGlobalEncodedValues().addAllFlagEncoders("car").build();
    private FlagEncoder carEncoder = encodingManager.getEncoder("car");
    private BooleanEncodedValue accessEnc = encodingManager.getBooleanEncodedValue(TagParserFactory.CAR_ACCESS);
    private DecimalEncodedValue avSpeedEnc = encodingManager.getDecimalEncodedValue(TagParserFactory.CAR_AVERAGE_SPEED);
    private EdgeFilter carOutEdges = new DefaultEdgeFilter(accessEnc, true, false);

    Graph createGraph() {
        return new GraphBuilder(encodingManager).create();
    }

    @Test
    public void testExtract() {
        Graph g = createGraph();
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 1, 2, true, 10);
        PathBidirRef pw = new PathBidirRef(g, new FastestWeighting(carEncoder));
        EdgeExplorer explorer = g.createEdgeExplorer(carOutEdges);
        EdgeIterator iter = explorer.setBaseNode(1);
        iter.next();
        pw.sptEntry = new SPTEntry(iter.getEdge(), 2, 0);
        pw.sptEntry.parent = new SPTEntry(EdgeIterator.NO_EDGE, 1, 10);
        pw.edgeTo = new SPTEntry(EdgeIterator.NO_EDGE, 2, 0);
        Path p = pw.extract();
        assertEquals(Helper.createTList(1, 2), p.calcNodes());
        assertEquals(10, p.getDistance(), 1e-4);
    }

    @Test
    public void testExtract2() {
        Graph g = createGraph();
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 1, 2, false, 10);
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 2, 3, false, 20);
        EdgeExplorer explorer = g.createEdgeExplorer(carOutEdges);
        EdgeIterator iter = explorer.setBaseNode(1);
        iter.next();
        PathBidirRef pw = new PathBidirRef(g, new FastestWeighting(carEncoder));
        pw.sptEntry = new SPTEntry(iter.getEdge(), 2, 10);
        pw.sptEntry.parent = new SPTEntry(EdgeIterator.NO_EDGE, 1, 0);

        explorer = g.createEdgeExplorer(new DefaultEdgeFilter(accessEnc, false, true));
        iter = explorer.setBaseNode(3);
        iter.next();
        pw.edgeTo = new SPTEntry(iter.getEdge(), 2, 20);
        pw.edgeTo.parent = new SPTEntry(EdgeIterator.NO_EDGE, 3, 0);
        Path p = pw.extract();
        assertEquals(Helper.createTList(1, 2, 3), p.calcNodes());
        assertEquals(30, p.getDistance(), 1e-4);
    }
}
