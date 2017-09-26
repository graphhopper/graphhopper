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
package com.graphhopper.util;

import com.graphhopper.json.GHJson;
import com.graphhopper.json.GHJsonFactory;
import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.profiles.TagParserFactory;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.storage.CHGraph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.IntsRef;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Peter Karich
 */
public class CHEdgeIteratorTest {
    private GHJson json = new GHJsonFactory().create();
    @Test
    public void testUpdateFlags() {
        CarFlagEncoder carFlagEncoder = new CarFlagEncoder();
        EncodingManager encodingManager = new EncodingManager.Builder().addGlobalEncodedValues().addAll(carFlagEncoder).build();
        FastestWeighting weighting = new FastestWeighting(carFlagEncoder);
        BooleanEncodedValue accessEnc = encodingManager.getBooleanEncodedValue(TagParserFactory.CAR_ACCESS);
        DecimalEncodedValue averageSpeedEnc = encodingManager.getDecimalEncodedValue(TagParserFactory.CAR_AVERAGE_SPEED);
        EdgeFilter carOutFilter = new DefaultEdgeFilter(accessEnc, true, false);
        GraphHopperStorage ghStorage = new GraphBuilder(encodingManager, json).setCHGraph(weighting).create();
        CHGraph g = ghStorage.getGraph(CHGraph.class, weighting);
        GHUtility.createEdge(g, averageSpeedEnc, 60, accessEnc, 0, 1, true, 12d).set(averageSpeedEnc, 10d);
        GHUtility.createEdge(g, averageSpeedEnc, 60, accessEnc, 0, 2, true, 13d).set(averageSpeedEnc, 20d);
        ghStorage.freeze();

        assertEquals(2, GHUtility.count(g.getAllEdges()));
        assertEquals(1, GHUtility.count(g.createEdgeExplorer(carOutFilter).setBaseNode(1)));
        EdgeIteratorState iter = GHUtility.getEdge(g, 0, 1);
        assertEquals(1, iter.getAdjNode());
        IntsRef ints = encodingManager.createIntsRef();
        accessEnc.setBool(false, ints, true);
        accessEnc.setBool(true, ints, true);
        averageSpeedEnc.setDecimal(false, ints, 10d);
        assertEquals(ints, iter.getData());

        // update setProperties
        averageSpeedEnc.setDecimal(false, ints, 20d);
        accessEnc.setBool(true, ints, false);
        iter.setData(ints);
        assertEquals(12, iter.getDistance(), 1e-4);

        // update distance
        iter.setDistance(10);
        assertEquals(10, iter.getDistance(), 1e-4);
        assertEquals(0, GHUtility.count(g.createEdgeExplorer(carOutFilter).setBaseNode(1)));
        iter = GHUtility.getEdge(g, 0, 1);

        accessEnc.setBool(false, ints, true);
        accessEnc.setBool(true, ints, false);
        averageSpeedEnc.setDecimal(false, ints, 20d);
        assertEquals(ints, iter.getData());
        assertEquals(10, iter.getDistance(), 1e-4);
        assertEquals(1, GHUtility.getNeighbors(g.createEdgeExplorer().setBaseNode(1)).size());
        assertEquals(0, GHUtility.getNeighbors(g.createEdgeExplorer(carOutFilter).setBaseNode(1)).size());
    }
}
