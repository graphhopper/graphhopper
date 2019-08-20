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
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.profiles.EnumEncodedValue;
import com.graphhopper.routing.profiles.RoadEnvironment;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FootFlagEncoder;
import com.graphhopper.routing.util.parsers.OSMRoadEnvironmentParser;
import com.graphhopper.storage.GraphExtension;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Helper;
import org.junit.After;
import org.junit.Before;

/**
 * @author Alexey Valikov
 */
public abstract class EdgeElevationInterpolatorTest {

    protected final static EncodingManager.AcceptWay ACCEPT_WAY = new EncodingManager.AcceptWay().
            put(RoadEnvironment.KEY, EncodingManager.Access.WAY).put("car", EncodingManager.Access.WAY).put("foot", EncodingManager.Access.WAY);
    protected static final double PRECISION = ElevationInterpolator.EPSILON2;
    protected ReaderWay interpolatableWay;
    protected ReaderWay normalWay;

    protected GraphHopperStorage graph;
    protected EnumEncodedValue<RoadEnvironment> roadEnvEnc;
    protected EncodingManager encodingManager;
    protected EdgeElevationInterpolator edgeElevationInterpolator;

    @SuppressWarnings("resource")
    @Before
    public void setUp() {
        graph = new GraphHopperStorage(new RAMDirectory(),
                encodingManager = new EncodingManager.Builder(8).add(new CarFlagEncoder()).add(new FootFlagEncoder()).
                        add(new OSMRoadEnvironmentParser()).build(),
                true, new GraphExtension.NoOpExtension()).create(100);
        roadEnvEnc = encodingManager.getEnumEncodedValue(RoadEnvironment.KEY, RoadEnvironment.class);
        edgeElevationInterpolator = createEdgeElevationInterpolator();

        interpolatableWay = createInterpolatableWay();
        normalWay = new ReaderWay(0);
        normalWay.setTag("highway", "primary");
    }

    @After
    public void tearDown() {
        Helper.close(graph);
    }

    protected abstract ReaderWay createInterpolatableWay();

    protected EdgeElevationInterpolator createEdgeElevationInterpolator() {
        return new EdgeElevationInterpolator(graph, roadEnvEnc, RoadEnvironment.BRIDGE);
    }

    protected void gatherOuterAndInnerNodeIdsOfStructure(EdgeIteratorState edge,
                                                         final GHIntHashSet outerNodeIds, final GHIntHashSet innerNodeIds) {
        edgeElevationInterpolator.gatherOuterAndInnerNodeIds(
                edgeElevationInterpolator.getStorage().createEdgeExplorer(), edge,
                new GHBitSetImpl(), outerNodeIds, innerNodeIds);
    }
}
