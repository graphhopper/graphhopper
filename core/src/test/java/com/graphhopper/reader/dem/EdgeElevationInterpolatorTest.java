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
package com.graphhopper.reader.dem;

import com.graphhopper.coll.GHBitSetImpl;
import com.graphhopper.coll.GHIntHashSet;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.EdgeIteratorState;
import org.junit.jupiter.api.BeforeEach;

/**
 * @author Alexey Valikov
 */
public abstract class EdgeElevationInterpolatorTest {

    protected static final double PRECISION = ElevationInterpolator.EPSILON2;
    protected RoadEnvironment interpolatableRoadEnvironment;
    protected RoadEnvironment normalRoadEnvironment;

    protected BaseGraph graph;
    protected EnumEncodedValue<RoadEnvironment> roadEnvEnc;
    protected BooleanEncodedValue accessEnc;
    protected DecimalEncodedValue speedEnc;
    protected EncodingManager encodingManager;
    protected EdgeElevationInterpolator edgeElevationInterpolator;

    @BeforeEach
    public void setUp() {
        accessEnc = new SimpleBooleanEncodedValue("access", true);
        speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, false);
        encodingManager = EncodingManager.start().add(accessEnc).add(speedEnc).add(RoadEnvironment.create()).build();
        graph = new BaseGraph.Builder(encodingManager).set3D(true).create();
        roadEnvEnc = encodingManager.getEnumEncodedValue(RoadEnvironment.KEY, RoadEnvironment.class);
        edgeElevationInterpolator = createEdgeElevationInterpolator();
        interpolatableRoadEnvironment = getInterpolatableRoadEnvironment();
        normalRoadEnvironment = RoadEnvironment.ROAD;
    }

    protected abstract RoadEnvironment getInterpolatableRoadEnvironment();

    protected EdgeElevationInterpolator createEdgeElevationInterpolator() {
        return new EdgeElevationInterpolator(graph, roadEnvEnc, RoadEnvironment.BRIDGE);
    }

    protected void gatherOuterAndInnerNodeIdsOfStructure(EdgeIteratorState edge,
                                                         final GHIntHashSet outerNodeIds, final GHIntHashSet innerNodeIds) {
        edgeElevationInterpolator.gatherOuterAndInnerNodeIds(
                edgeElevationInterpolator.getGraph().createEdgeExplorer(), edge,
                new GHBitSetImpl(), outerNodeIds, innerNodeIds);
    }
}
