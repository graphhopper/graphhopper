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
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.RoadEnvironment;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.EdgeIteratorState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/**
 * @author Alexey Valikov
 */
public abstract class EdgeElevationInterpolatorTest {

    protected static final double PRECISION = ElevationInterpolator.EPSILON2;
    protected IntsRef interpolatableFlags;
    protected IntsRef normalFlags;

    protected BaseGraph graph;
    protected EnumEncodedValue<RoadEnvironment> roadEnvEnc;
    protected CarFlagEncoder encoder;
    protected EncodingManager encodingManager;
    protected EdgeElevationInterpolator edgeElevationInterpolator;

    @SuppressWarnings("resource")
    @BeforeEach
    public void setUp() {
        encoder = new CarFlagEncoder();
        encodingManager = EncodingManager.create(encoder);
        graph = new BaseGraph.Builder(encodingManager).set3D(true).create();
        roadEnvEnc = encodingManager.getEnumEncodedValue(RoadEnvironment.KEY, RoadEnvironment.class);
        edgeElevationInterpolator = createEdgeElevationInterpolator();
        interpolatableFlags = createInterpolatableFlags();
        normalFlags = new IntsRef(1);
        roadEnvEnc.setEnum(false, normalFlags, RoadEnvironment.ROAD);
    }

    @AfterEach
    public void tearDown() {
        graph.close();
    }

    protected abstract IntsRef createInterpolatableFlags();

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
