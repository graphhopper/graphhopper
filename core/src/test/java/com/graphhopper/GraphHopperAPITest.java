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
package com.graphhopper;

import com.graphhopper.json.geo.JsonFeature;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.change.ChangeGraphHelper;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.BBox;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class GraphHopperAPITest {
    final EncodingManager encodingManager = EncodingManager.create("car");

    void initGraph(GraphHopperStorage graph) {
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 42, 10);
        na.setNode(1, 42.1, 10.1);
        na.setNode(2, 42.1, 10.2);
        na.setNode(3, 42, 10.4);

        graph.edge(0, 1, 10, true);
        graph.edge(2, 3, 10, true);
    }

    @Test
    public void testLoad() {
        GraphHopperStorage graph = new GraphBuilder(encodingManager).create();
        initGraph(graph);
        // do further changes:
        NodeAccess na = graph.getNodeAccess();
        na.setNode(4, 41.9, 10.2);

        graph.edge(1, 2, 10, false);
        graph.edge(0, 4, 40, true);
        graph.edge(4, 3, 40, true);

        GraphHopper instance = new GraphHopper().
                setStoreOnFlush(false).
                setEncodingManager(encodingManager).setCHEnabled(false).
                loadGraph(graph);
        // 3 -> 0
        GHResponse rsp = instance.route(new GHRequest(42, 10.4, 42, 10));
        assertFalse(rsp.hasErrors());
        PathWrapper arsp = rsp.getBest();
        assertEquals(80, arsp.getDistance(), 1e-6);

        PointList points = arsp.getPoints();
        assertEquals(42, points.getLatitude(0), 1e-5);
        assertEquals(10.4, points.getLongitude(0), 1e-5);
        assertEquals(41.9, points.getLatitude(1), 1e-5);
        assertEquals(10.2, points.getLongitude(1), 1e-5);
        assertEquals(3, points.getSize());
        instance.close();
    }

    @Test
    public void testDisconnected179() {
        GraphHopperStorage graph = new GraphBuilder(encodingManager).create();
        initGraph(graph);

        GraphHopper instance = new GraphHopper().
                setStoreOnFlush(false).
                setEncodingManager(encodingManager).setCHEnabled(false).
                loadGraph(graph);
        GHResponse rsp = instance.route(new GHRequest(42, 10, 42, 10.4));
        assertTrue(rsp.hasErrors());

        try {
            rsp.getBest().getPoints();
            assertTrue(false);
        } catch (Exception ex) {
        }

        instance.close();
    }

    @Test
    public void testNoLoad() {
        GraphHopper instance = new GraphHopper().
                setStoreOnFlush(false).
                setEncodingManager(encodingManager).setCHEnabled(false);
        try {
            instance.route(new GHRequest(42, 10.4, 42, 10));
            assertTrue(false);
        } catch (Exception ex) {
            assertTrue(ex.getMessage(), ex.getMessage().startsWith("Do a successful call to load or importOrLoad before routing"));
        }

        instance = new GraphHopper().setEncodingManager(encodingManager);
        try {
            instance.route(new GHRequest(42, 10.4, 42, 10));
            assertTrue(false);
        } catch (Exception ex) {
            assertTrue(ex.getMessage(), ex.getMessage().startsWith("Do a successful call to load or importOrLoad before routing"));
        }
    }

    @Test
    public void testConcurrentGraphChange() throws InterruptedException {
        final GraphHopperStorage graph = new GraphBuilder(encodingManager).create();
        initGraph(graph);
        graph.edge(1, 2, 10, true);

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicInteger checkPointCounter = new AtomicInteger(0);
        final GraphHopper graphHopper = new GraphHopper() {
            @Override
            protected ChangeGraphHelper createChangeGraphHelper(Graph graph, LocationIndex locationIndex) {
                return new ChangeGraphHelper(graph, locationIndex) {
                    @Override
                    public long applyChanges(EncodingManager em, Collection<JsonFeature> features) {
                        // force sleep inside the lock and let the main thread run until the lock barrier
                        latch.countDown();
                        try {
                            Thread.sleep(400);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        checkPointCounter.incrementAndGet();
                        return super.applyChanges(em, features);
                    }
                };
            }
        }.setStoreOnFlush(false).setEncodingManager(encodingManager).setCHEnabled(false).
                loadGraph(graph);

        GHResponse rsp = graphHopper.route(new GHRequest(42, 10.4, 42, 10));
        assertFalse(rsp.toString(), rsp.hasErrors());
        assertEquals(1800, rsp.getBest().getTime());

        final List<JsonFeature> list = new ArrayList<>();
        Map<String, Object> properties = new HashMap<>();
        properties.put("speed", 5);

        list.add(new JsonFeature("1", "bbox",
                new BBox(10.399, 10.4, 42.0, 42.001),
                null, properties));

        ExecutorService executorService = Executors.newFixedThreadPool(1);
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                graphHopper.changeGraph(list);
                checkPointCounter.incrementAndGet();
            }
        });

        latch.await();
        assertEquals(0, checkPointCounter.get());
        rsp = graphHopper.route(new GHRequest(42, 10.4, 42, 10));
        assertFalse(rsp.toString(), rsp.hasErrors());
        assertEquals(8400, rsp.getBest().getTime());

        executorService.shutdown();
        executorService.awaitTermination(3, TimeUnit.SECONDS);

        assertEquals(2, checkPointCounter.get());
    }
}
