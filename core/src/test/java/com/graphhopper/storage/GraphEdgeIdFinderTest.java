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
package com.graphhopper.storage;

import com.graphhopper.coll.GHIntHashSet;
import com.graphhopper.json.GHJson;
import com.graphhopper.json.GHJsonFactory;
import com.graphhopper.routing.AbstractRoutingAlgorithmTester;
import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.profiles.TagParserFactory;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.shapes.Circle;
import com.graphhopper.util.shapes.Shape;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author Peter Karich
 */
public class GraphEdgeIdFinderTest {

    private GHJson json = new GHJsonFactory().create();

    @Test
    public void testParseStringHints() {
        FlagEncoder encoder = new CarFlagEncoder();
        EncodingManager em = new EncodingManager.Builder().addGlobalEncodedValues().addAll(encoder).build();
        BooleanEncodedValue accessEnc = em.getBooleanEncodedValue(TagParserFactory.CAR_ACCESS);
        DecimalEncodedValue avSpeedEnc = em.getDecimalEncodedValue(TagParserFactory.CAR_AVERAGE_SPEED);
        GraphHopperStorage graph = new GraphBuilder(em, json).create();
        // 0-1-2
        // | |
        // 3-4
        GHUtility.createEdge(graph, avSpeedEnc, 60, accessEnc, 0, 1, true, 1);
        GHUtility.createEdge(graph, avSpeedEnc, 60, accessEnc, 1, 2, true, 1);
        GHUtility.createEdge(graph, avSpeedEnc, 60, accessEnc, 3, 4, true, 1);
        GHUtility.createEdge(graph, avSpeedEnc, 60, accessEnc, 0, 3, true, 1);
        GHUtility.createEdge(graph, avSpeedEnc, 60, accessEnc, 1, 4, true, 1);
        AbstractRoutingAlgorithmTester.updateDistancesFor(graph, 0, 0.01, 0.00);
        AbstractRoutingAlgorithmTester.updateDistancesFor(graph, 1, 0.01, 0.01);
        AbstractRoutingAlgorithmTester.updateDistancesFor(graph, 2, 0.01, 0.02);
        AbstractRoutingAlgorithmTester.updateDistancesFor(graph, 3, 0.00, 0.00);
        AbstractRoutingAlgorithmTester.updateDistancesFor(graph, 4, 0.00, 0.01);

        LocationIndex locationIndex = new LocationIndexTree(graph, new RAMDirectory())
                .prepareIndex();

        GraphEdgeIdFinder graphFinder = new GraphEdgeIdFinder(graph, locationIndex);
        GraphEdgeIdFinder.BlockArea blockArea = graphFinder.parseBlockArea("0.01,0.005,1", new DefaultEdgeFilter(accessEnc));

        GHIntHashSet blockedEdges = new GHIntHashSet();
        blockedEdges.add(0);
        assertEquals(blockedEdges, blockArea.blockedEdges);
        List<Shape> blockedShapes = new ArrayList<>();
        assertEquals(blockedShapes, blockArea.blockedShapes);

        // big area converts into shapes
        graphFinder = new GraphEdgeIdFinder(graph, locationIndex);
        blockArea = graphFinder.parseBlockArea("0,0,1000", new DefaultEdgeFilter(accessEnc));
        blockedEdges.clear();
        assertEquals(blockedEdges, blockArea.blockedEdges);
        blockedShapes.add(new Circle(0, 0, 1000));
        assertEquals(blockedShapes, blockArea.blockedShapes);
    }
}
