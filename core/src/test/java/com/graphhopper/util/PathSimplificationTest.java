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

import com.graphhopper.ResponsePath;
import com.graphhopper.routing.Dijkstra;
import com.graphhopper.routing.InstructionsFromEdges;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.ShortestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.details.PathDetail;
import com.graphhopper.util.details.PathDetailsBuilderFactory;
import com.graphhopper.util.details.PathDetailsFromEdges;
import org.junit.jupiter.api.Test;

import java.util.*;

import static com.graphhopper.search.KVStorage.KeyValue.STREET_NAME;
import static com.graphhopper.search.KVStorage.KeyValue.createKV;
import static com.graphhopper.util.Parameters.Details.AVERAGE_SPEED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Robin Boldt
 */
public class PathSimplificationTest {

    private final TranslationMap trMap = TranslationMapTest.SINGLETON;
    private final Translation usTR = trMap.getWithFallBack(Locale.US);
    private final TraversalMode tMode = TraversalMode.NODE_BASED;

    @Test
    public void testScenario() {
        BooleanEncodedValue accessEnc = new SimpleBooleanEncodedValue("access", true);
        DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, false);
        EncodingManager carManager = EncodingManager.start().add(accessEnc).add(speedEnc)
                .add(Roundabout.create()).add(RoadClass.create()).add(RoadClassLink.create()).add(MaxSpeed.create()).build();
        BaseGraph g = new BaseGraph.Builder(carManager).create();
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

        GHUtility.setSpeed(9, true, true, accessEnc, speedEnc, g.edge(0, 1).setDistance(10000)).setKeyValues(createKV(STREET_NAME, "0-1"));
        GHUtility.setSpeed(9, true, true, accessEnc, speedEnc, g.edge(1, 2).setDistance(11000)).setKeyValues(createKV(STREET_NAME, "1-2"));

        GHUtility.setSpeed(18, true, true, accessEnc, speedEnc, g.edge(0, 3).setDistance(11000));
        GHUtility.setSpeed(18, true, true, accessEnc, speedEnc, g.edge(1, 4).setDistance(10000)).setKeyValues(createKV(STREET_NAME, "1-4"));
        GHUtility.setSpeed(18, true, true, accessEnc, speedEnc, g.edge(2, 5).setDistance(11000)).setKeyValues(createKV(STREET_NAME, "5-2"));

        GHUtility.setSpeed(27, true, true, accessEnc, speedEnc, g.edge(3, 6).setDistance(11000)).setKeyValues(createKV(STREET_NAME, "3-6"));
        GHUtility.setSpeed(27, true, true, accessEnc, speedEnc, g.edge(4, 7).setDistance(10000)).setKeyValues(createKV(STREET_NAME, "4-7"));
        GHUtility.setSpeed(27, true, true, accessEnc, speedEnc, g.edge(5, 8).setDistance(10000)).setKeyValues(createKV(STREET_NAME, "5-8"));

        GHUtility.setSpeed(36, true, true, accessEnc, speedEnc, g.edge(6, 7).setDistance(11000)).setKeyValues(createKV(STREET_NAME, "6-7"));
        EdgeIteratorState tmpEdge = GHUtility.setSpeed(36, true, true, accessEnc, speedEnc, g.edge(7, 8).setDistance(10000));
        PointList list = new PointList();
        list.add(1.0, 1.15);
        list.add(1.0, 1.16);
        tmpEdge.setWayGeometry(list);
        tmpEdge.setKeyValues(createKV(STREET_NAME, "7-8"));

        // missing edge name
        GHUtility.setSpeed(45, true, true, accessEnc, speedEnc, g.edge(9, 10).setDistance(10000));
        tmpEdge = GHUtility.setSpeed(45, true, true, accessEnc, speedEnc, g.edge(8, 9).setDistance(20000));
        list.clear();
        list.add(1.0, 1.3);
        list.add(1.0, 1.3001);
        list.add(1.0, 1.3002);
        list.add(1.0, 1.3003);
        tmpEdge.setKeyValues(createKV(STREET_NAME, "8-9"));
        tmpEdge.setWayGeometry(list);

        // Path is: [0 0-1, 3 1-4, 6 4-7, 9 7-8, 11 8-9, 10 9-10]
        Weighting weighting = new ShortestWeighting(accessEnc, speedEnc);
        Path p = new Dijkstra(g, weighting, tMode).calcPath(0, 10);
        InstructionList wayList = InstructionsFromEdges.calcInstructions(p, g, weighting, carManager, usTR);
        Map<String, List<PathDetail>> details = PathDetailsFromEdges.calcDetails(p, carManager, weighting,
                Arrays.asList(AVERAGE_SPEED), new PathDetailsBuilderFactory(), 0, g);

        PointList points = p.calcPoints();
        PointList waypoints = new PointList(2, g.getNodeAccess().is3D());
        waypoints.add(g.getNodeAccess(), 0);
        waypoints.add(g.getNodeAccess(), 10);
        List<Integer> waypointIndices = Arrays.asList(0, points.size() - 1);

        ResponsePath responsePath = new ResponsePath();
        responsePath.setInstructions(wayList);
        responsePath.addPathDetails(details);
        responsePath.setPoints(points);
        responsePath.setWaypoints(waypoints);
        responsePath.setWaypointIndices(waypointIndices);

        int numberOfPoints = points.size();

        RamerDouglasPeucker ramerDouglasPeucker = new RamerDouglasPeucker();
        // Do not simplify anything
        ramerDouglasPeucker.setMaxDistance(0);

        PathSimplification.simplify(responsePath, ramerDouglasPeucker, true);

        assertEquals(numberOfPoints, responsePath.getPoints().size());

        responsePath = new ResponsePath();
        responsePath.setInstructions(wayList);
        responsePath.addPathDetails(details);
        responsePath.setPoints(p.calcPoints());
        responsePath.setWaypoints(waypoints);
        responsePath.setWaypointIndices(waypointIndices);

        ramerDouglasPeucker.setMaxDistance(100000000);
        PathSimplification.simplify(responsePath, ramerDouglasPeucker, true);

        assertTrue(numberOfPoints > responsePath.getPoints().size());
    }

    @Test
    public void testSinglePartition() {
        // points are chosen such that DP will remove those marked with an x
        // todo: we could go further and replace Ramer-Douglas-Peucker with some abstract thing that makes this easier to test
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
        PathSimplification.simplify(points, partitions, new RamerDouglasPeucker());

        // check points were modified correctly
        assertEquals(5, points.size());
        origPoints.set(1, Double.NaN, Double.NaN, Double.NaN);
        origPoints.set(2, Double.NaN, Double.NaN, Double.NaN);
        origPoints.set(5, Double.NaN, Double.NaN, Double.NaN);
        RamerDouglasPeucker.removeNaN(origPoints);
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
        // http://localhost:8989/maps/?point=48.891273%2C9.325418&point=48.891005%2C9.322865&point=48.889877%2C9.32102&point=48.88975%2C9.31999&profile=car&weighting=fastest&elevation=true&debug=true&details=max_speed&details=street_name&
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
        PathSimplification.simplify(points, partitions, new RamerDouglasPeucker());

        // check points were modified correctly
        assertEquals(12, points.size());
        origPoints.set(1, Double.NaN, Double.NaN, Double.NaN);
        origPoints.set(2, Double.NaN, Double.NaN, Double.NaN);
        origPoints.set(5, Double.NaN, Double.NaN, Double.NaN);
        origPoints.set(13, Double.NaN, Double.NaN, Double.NaN);
        RamerDouglasPeucker.removeNaN(origPoints);
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
