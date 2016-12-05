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
package com.graphhopper.routing.weighting;

import com.graphhopper.coll.GHIntHashSet;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.DataFlagEncoder;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphExtension;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.Circle;
import com.graphhopper.util.shapes.Shape;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author Peter Karich
 */
public class GenericWeightingTest {
    private final DataFlagEncoder encoder = (DataFlagEncoder) new EncodingManager("generic").getEncoder("generic");

    @Test
    public void testBlockedById() {
        ReaderWay way = new ReaderWay(27l);
        way.setTag("highway", "primary");
        way.setTag("maxspeed", "10");
        EdgeIteratorState edge = createEdge(27, 10, encoder.handleWayTags(way, 1, 0));
        ConfigMap cMap = encoder.readStringMap(new PMap());
        Weighting instance = new GenericWeighting(encoder, cMap);
        assertEquals(3.0, instance.calcWeight(edge, false, EdgeIterator.NO_EDGE), 1e-8);

        GHIntHashSet blockedEdges = new GHIntHashSet(1);
        cMap.put(Parameters.Routing.BLOCKED_EDGES, blockedEdges);
        blockedEdges.add(27);
        instance = new GenericWeighting(encoder, cMap);
        assertEquals(Double.POSITIVE_INFINITY, instance.calcWeight(edge, false, EdgeIterator.NO_EDGE), 1e-8);
    }

    @Test
    public void testBlockedByShape() {
        ReaderWay way = new ReaderWay(27l);
        way.setTag("highway", "primary");
        way.setTag("maxspeed", "10");
        EdgeIteratorState edge = createEdge(27, 10, encoder.handleWayTags(way, 1, 0));
        ConfigMap cMap = encoder.readStringMap(new PMap());
        GenericWeighting instance = new GenericWeighting(encoder, cMap);
        assertEquals(3.0, instance.calcWeight(edge, false, EdgeIterator.NO_EDGE), 1e-8);

        List<Shape> shapes = new ArrayList<>(1);
        shapes.add(new Circle(1, 1, 1));
        cMap.put(Parameters.Routing.BLOCKED_SHAPES, shapes);
        instance = new GenericWeighting(encoder, cMap);
        instance.setGraph(getGraph());
        assertEquals(Double.POSITIVE_INFINITY, instance.calcWeight(edge, false, EdgeIterator.NO_EDGE), 1e-8);

        shapes.clear();
        // Do not match 1,1 of Edge - which is returned by NodeAccess
        shapes.add(new Circle(10, 10, 1));
        cMap.put(Parameters.Routing.BLOCKED_SHAPES, shapes);
        instance = new GenericWeighting(encoder, cMap);
        instance.setGraph(getGraph());
        assertEquals(3.0, instance.calcWeight(edge, false, EdgeIterator.NO_EDGE), 1e-8);
    }


    EdgeIterator createEdge(final int edge, final double distance, final long flags) {
        return new GHUtility.DisabledEdgeIterator() {
            @Override
            public int getEdge() {
                return edge;
            }

            @Override
            public double getDistance() {
                return distance;
            }

            @Override
            public long getFlags() {
                return flags;
            }

            @Override
            public boolean getBool(int key, boolean _default) {
                return _default;
            }

            @Override
            public int getAdjNode() {
                return 1;
            }

        };
    }

    // Fake Graph that returns a Node Acess that returns 1 for for every getLat,getLon Edge
    Graph getGraph(){
        return new Graph() {
            @Override
            public Graph getBaseGraph() {
                return null;
            }

            @Override
            public int getNodes() {
                return 0;
            }

            @Override
            public NodeAccess getNodeAccess() {
                return new NodeAccess() {
                    @Override
                    public int getAdditionalNodeField(int nodeId) {
                        return 0;
                    }

                    @Override
                    public void setAdditionalNodeField(int nodeId, int additionalValue) {

                    }

                    @Override
                    public boolean is3D() {
                        return false;
                    }

                    @Override
                    public int getDimension() {
                        return 0;
                    }

                    @Override
                    public void ensureNode(int nodeId) {

                    }

                    @Override
                    public void setNode(int nodeId, double lat, double lon) {

                    }

                    @Override
                    public void setNode(int nodeId, double lat, double lon, double ele) {

                    }

                    @Override
                    public double getLatitude(int nodeId) {
                        return 1;
                    }

                    @Override
                    public double getLat(int nodeId) {
                        return 1;
                    }

                    @Override
                    public double getLongitude(int nodeId) {
                        return 1;
                    }

                    @Override
                    public double getLon(int nodeId) {
                        return 1;
                    }

                    @Override
                    public double getElevation(int nodeId) {
                        return 0;
                    }

                    @Override
                    public double getEle(int nodeId) {
                        return 0;
                    }
                };
            }

            @Override
            public BBox getBounds() {
                return null;
            }

            @Override
            public EdgeIteratorState edge(int a, int b) {
                return null;
            }

            @Override
            public EdgeIteratorState edge(int a, int b, double distance, boolean bothDirections) {
                return null;
            }

            @Override
            public EdgeIteratorState getEdgeIteratorState(int edgeId, int adjNode) {
                return null;
            }

            @Override
            public AllEdgesIterator getAllEdges() {
                return null;
            }

            @Override
            public EdgeExplorer createEdgeExplorer(EdgeFilter filter) {
                return null;
            }

            @Override
            public EdgeExplorer createEdgeExplorer() {
                return null;
            }

            @Override
            public Graph copyTo(Graph g) {
                return null;
            }

            @Override
            public GraphExtension getExtension() {
                return null;
            }
        };
    }
}
