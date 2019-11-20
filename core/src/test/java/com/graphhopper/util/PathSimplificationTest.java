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

import com.graphhopper.PathWrapper;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.Dijkstra;
import com.graphhopper.routing.InstructionsFromEdges;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.profiles.Roundabout;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.ShortestWeighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.Parameters.Details;
import com.graphhopper.util.details.PathDetail;
import com.graphhopper.util.details.PathDetailsBuilderFactory;
import com.graphhopper.util.details.PathDetailsFromEdges;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Robin Boldt
 */
public class PathSimplificationTest {

    private final TranslationMap trMap = TranslationMapTest.SINGLETON;
    private final Translation usTR = trMap.getWithFallBack(Locale.US);
    private final TraversalMode tMode = TraversalMode.NODE_BASED;
    private EncodingManager carManager;
    private FlagEncoder carEncoder;

    @Before
    public void setUp() {
        carEncoder = new CarFlagEncoder();
        carManager = EncodingManager.create(carEncoder);
    }

    @Test
    public void testScenario() {
        Graph g = new GraphBuilder(carManager).create();
        // 0-1-2
        // | | |
        // 3-4-5  9-10
        // | | |  |
        // 6-7-8--*
        NodeAccess na = g.getNodeAccess();
        na.setNode(0, 1.2, 1.0);
        na.setNode(1, 1.2, 1.1);
        na.setNode(2, 1.2, 1.2);
        na.setNode(3, 1.1, 1.0);
        na.setNode(4, 1.1, 1.1);
        na.setNode(5, 1.1, 1.2);
        na.setNode(9, 1.1, 1.3);
        na.setNode(10, 1.1, 1.4);

        na.setNode(6, 1.0, 1.0);
        na.setNode(7, 1.0, 1.1);
        na.setNode(8, 1.0, 1.2);

        ReaderWay w = new ReaderWay(1);
        w.setTag("highway", "tertiary");
        w.setTag("maxspeed", "10");

        long relFlags = 0;
        EdgeIteratorState tmpEdge;
        tmpEdge = g.edge(0, 1, 10000, true).setName("0-1");
        EncodingManager.AcceptWay map = new EncodingManager.AcceptWay();
        assertTrue(carManager.acceptWay(w, map));
        tmpEdge.setFlags(carManager.handleWayTags(w, map, relFlags));
        tmpEdge = g.edge(1, 2, 11000, true).setName("1-2");
        tmpEdge.setFlags(carManager.handleWayTags(w, map, relFlags));

        w.setTag("maxspeed", "20");
        tmpEdge = g.edge(0, 3, 11000, true);
        tmpEdge.setFlags(carManager.handleWayTags(w, map, relFlags));
        tmpEdge = g.edge(1, 4, 10000, true).setName("1-4");
        tmpEdge.setFlags(carManager.handleWayTags(w, map, relFlags));
        tmpEdge = g.edge(2, 5, 11000, true).setName("5-2");
        tmpEdge.setFlags(carManager.handleWayTags(w, map, relFlags));

        w.setTag("maxspeed", "30");
        tmpEdge = g.edge(3, 6, 11000, true).setName("3-6");
        tmpEdge.setFlags(carManager.handleWayTags(w, map, relFlags));
        tmpEdge = g.edge(4, 7, 10000, true).setName("4-7");
        tmpEdge.setFlags(carManager.handleWayTags(w, map, relFlags));
        tmpEdge = g.edge(5, 8, 10000, true).setName("5-8");
        tmpEdge.setFlags(carManager.handleWayTags(w, map, relFlags));

        w.setTag("maxspeed", "40");
        tmpEdge = g.edge(6, 7, 11000, true).setName("6-7");
        tmpEdge.setFlags(carManager.handleWayTags(w, map, relFlags));
        tmpEdge = g.edge(7, 8, 10000, true);
        PointList list = new PointList();
        list.add(1.0, 1.15);
        list.add(1.0, 1.16);
        tmpEdge.setWayGeometry(list);
        tmpEdge.setName("7-8");
        tmpEdge.setFlags(carManager.handleWayTags(w, map, relFlags));

        w.setTag("maxspeed", "50");
        // missing edge name
        tmpEdge = g.edge(9, 10, 10000, true);
        tmpEdge.setFlags(carManager.handleWayTags(w, map, relFlags));
        tmpEdge = g.edge(8, 9, 20000, true);
        list.clear();
        list.add(1.0, 1.3);
        list.add(1.0, 1.3001);
        list.add(1.0, 1.3002);
        list.add(1.0, 1.3003);
        tmpEdge.setName("8-9");
        tmpEdge.setWayGeometry(list);
        tmpEdge.setFlags(carManager.handleWayTags(w, map, relFlags));

        // Path is: [0 0-1, 3 1-4, 6 4-7, 9 7-8, 11 8-9, 10 9-10]
        ShortestWeighting weighting = new ShortestWeighting(carEncoder);
        Path p = new Dijkstra(g, weighting, tMode).calcPath(0, 10);
        InstructionList wayList = InstructionsFromEdges.calcInstructions(p, g, weighting, carManager.getBooleanEncodedValue(Roundabout.KEY), usTR);
        Map<String, List<PathDetail>> details = PathDetailsFromEdges.calcDetails(p, weighting, Arrays.asList(Details.AVERAGE_SPEED), new PathDetailsBuilderFactory(), 0);

        PathWrapper pathWrapper = new PathWrapper();
        pathWrapper.setInstructions(wayList);
        pathWrapper.addPathDetails(details);
        pathWrapper.setPoints(p.calcPoints());

        int numberOfPoints = p.calcPoints().size();

        DouglasPeucker douglasPeucker = new DouglasPeucker();
        // Do not simplify anything
        douglasPeucker.setMaxDistance(0);

        PathSimplification.simplify(pathWrapper, douglasPeucker, true);

        assertEquals(numberOfPoints, pathWrapper.getPoints().size());

        pathWrapper = new PathWrapper();
        pathWrapper.setInstructions(wayList);
        pathWrapper.addPathDetails(details);
        pathWrapper.setPoints(p.calcPoints());

        douglasPeucker.setMaxDistance(100000000);
        PathSimplification.simplify(pathWrapper, douglasPeucker, true);

        assertTrue(numberOfPoints > pathWrapper.getPoints().size());
    }

    @Test
    public void testSinglePartition() {
        // points are chosen such that DP will remove those marked with an x
        // todo: we could go further and replace DouglasPeucker with some abstract thing that makes this easier to test
        PointList points = new PointList();
        points.add(48.89107, 9.33161); // 0   -> 0
        points.add(48.89104, 9.33102); // 1 x
        points.add(48.89100, 9.33024); // 2 x
        points.add(48.89099, 9.33002); // 3   -> 1
        points.add(48.89092, 9.32853); // 4   -> 2
        points.add(48.89101, 9.32854); // 5 x
        points.add(48.89242, 9.32865); // 6   -> 3
        points.add(48.89343, 9.32878); // 7   -> 4
        PointList origPoints = points.clone(false);
        TestPartition partition = TestPartition.start()
                .add(0, 3)
                .add(3, 3) // via
                .add(3, 3) // via (just added this to make the test harder)
                .add(3, 4)
                .add(4, 4) // via
                .add(4, 7)
                .add(7, 7); // end
        List<PathSimplification.Partition> partitions = new ArrayList<>();
        partitions.add(partition);
        PathSimplification.simplify(points, partitions, new DouglasPeucker());

        // check points were modified correctly
        assertEquals(5, points.size());
        origPoints.set(1, Double.NaN, Double.NaN, Double.NaN);
        origPoints.set(2, Double.NaN, Double.NaN, Double.NaN);
        origPoints.set(5, Double.NaN, Double.NaN, Double.NaN);
        DouglasPeucker.removeNaN(origPoints);
        assertEquals(origPoints, points);

        // check partition was modified correctly
        TestPartition expected = TestPartition.start()
                .add(0, 1)
                .add(1, 1)
                .add(1, 1)
                .add(1, 2)
                .add(2, 2)
                .add(2, 4)
                .add(4, 4);
        assertEquals(expected.intervals, partition.intervals);
    }

    @Test
    public void testMultiplePartitions() {
        // points are chosen such that DP will remove those marked with an x
        // got this data from running a request like this:
        // http://localhost:8989/maps/?point=48.891273%2C9.325418&point=48.891005%2C9.322865&point=48.889877%2C9.32102&point=48.88975%2C9.31999&vehicle=car&weighting=fastest&elevation=true&debug=true&details=max_speed&details=street_name&
        PointList points = new PointList(20, true);
        points.add(48.89089, 9.32538, 270.0); // 0    -> 0
        points.add(48.89090, 9.32527, 269.0); // 1 x
        points.add(48.89091, 9.32439, 267.0); // 2 x
        points.add(48.89091, 9.32403, 267.0); // 3    -> 1
        points.add(48.89090, 9.32324, 267.0); // 4    -> 2
        points.add(48.89088, 9.32296, 267.0); // 5 x
        points.add(48.89088, 9.32288, 266.0); // 6    -> 3
        points.add(48.89081, 9.32208, 265.0); // 7    -> 4
        points.add(48.89056, 9.32217, 265.0); // 8    -> 5
        points.add(48.89047, 9.32218, 265.0); // 9    -> 6
        points.add(48.89037, 9.32215, 265.0); // 10   -> 7
        points.add(48.89026, 9.32157, 265.0); // 11   -> 8
        points.add(48.89023, 9.32101, 264.0); // 12   -> 9
        points.add(48.89027, 9.32038, 261.0); // 13 x
        points.add(48.89030, 9.32006, 261.0); // 14   -> 10
        points.add(48.88989, 9.31965, 261.0); // 15   -> 11

        PointList origPoints = points.clone(false);
        // from instructions
        TestPartition partition1 = TestPartition.start()
                .add(0, 6)
                .add(6, 6) // via
                .add(6, 7)
                .add(7, 10)
                .add(10, 12)
                .add(12, 12) // via
                .add(12, 14)
                .add(14, 15)
                .add(15, 15); // end

        // from max_speed detail
        TestPartition partition2 = TestPartition.start()
                .add(0, 3)
                .add(3, 7)
                .add(7, 15);

        // from street_name detail
        TestPartition partition3 = TestPartition.start()
                .add(0, 7)
                .add(7, 14)
                .add(14, 15);

        List<PathSimplification.Partition> partitions = new ArrayList<>();
        partitions.add(partition1);
        partitions.add(partition2);
        partitions.add(partition3);
        PathSimplification.simplify(points, partitions, new DouglasPeucker());

        // check points were modified correctly
        assertEquals(12, points.size());
        origPoints.set(1, Double.NaN, Double.NaN, Double.NaN);
        origPoints.set(2, Double.NaN, Double.NaN, Double.NaN);
        origPoints.set(5, Double.NaN, Double.NaN, Double.NaN);
        origPoints.set(13, Double.NaN, Double.NaN, Double.NaN);
        DouglasPeucker.removeNaN(origPoints);
        assertEquals(origPoints, points);

        // check partitions were modified correctly
        TestPartition expected1 = TestPartition.start()
                .add(0, 3)
                .add(3, 3) // via
                .add(3, 4)
                .add(4, 7)
                .add(7, 9)
                .add(9, 9) // via
                .add(9, 10)
                .add(10, 11)
                .add(11, 11); // end

        TestPartition expected2 = TestPartition.start()
                .add(0, 1)
                .add(1, 4)
                .add(4, 11);

        TestPartition expected3 = TestPartition.start()
                .add(0, 4)
                .add(4, 10)
                .add(10, 11);

        assertEquals(expected1.intervals, partition1.intervals);
        assertEquals(expected2.intervals, partition2.intervals);
        assertEquals(expected3.intervals, partition3.intervals);
    }

    static class TestPartition implements PathSimplification.Partition {
        static Interval interval(int start, int end) {
            return new Interval(start, end);
        }

        private List<Interval> intervals = new ArrayList<>();

        private static TestPartition start() {
            return new TestPartition();
        }

        private TestPartition add(int start, int end) {
            intervals.add(interval(start, end));
            return this;
        }

        @Override
        public int size() {
            return intervals.size();
        }

        @Override
        public int getIntervalLength(int index) {
            return intervals.get(index).length();
        }

        @Override
        public void setInterval(int index, int start, int end) {
            intervals.get(index).set(start, end);
        }

        static class Interval {
            int start;
            int end;

            Interval(int start, int end) {
                this.start = start;
                this.end = end;
            }

            private int length() {
                return end - start;
            }

            private void set(int start, int end) {
                this.start = start;
                this.end = end;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                Interval that = (Interval) o;
                return start == that.start &&
                        end == that.end;
            }

            @Override
            public int hashCode() {
                return Objects.hash(start, end);
            }

            @Override
            public String toString() {
                return "[" + start + ", " + end + "]";
            }
        }
    }
}
