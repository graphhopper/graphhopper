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

import com.graphhopper.GHRequest;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.util.Collections.emptyList;
import static org.junit.Assert.assertEquals;

/**
 * @author Peter Karich
 */
public class GHRequestTest {
    @Test
    public void testGetHint() {
        GHRequest instance = new GHRequest(10, 12, 12, 10);
        instance.getHints().putObject("something", 1);
        assertEquals(1, instance.getHints().getInt("something", 2));
        // #173 - will throw an error: Integer cannot be cast to Double
        assertEquals(1, instance.getHints().getDouble("something", 2d), 1e1);
    }

    @Test
    public void testCorrectInit() {
        double lat0 = 51, lon0 = 1, lat1 = 52, lon1 = 2, lat2 = 53, lon2 = 3;

        ArrayList<GHPoint> points = new ArrayList<>(3);
        points.add(new GHPoint(lat0, lon0));
        points.add(new GHPoint(lat1, lon1));
        points.add(new GHPoint(lat2, lon2));
        List<Double> favoredHeadings = Arrays.asList(3.14, 4.15, Double.NaN);
        GHRequest instance;

        instance = new GHRequest(points, favoredHeadings);
        compareFavoredHeadings(instance, favoredHeadings);
        comparePoints(instance, points);

        instance = new GHRequest(points.get(0), points.get(1), favoredHeadings.get(0), favoredHeadings.get(1));
        compareFavoredHeadings(instance, favoredHeadings.subList(0, 2));
        comparePoints(instance, points.subList(0, 2));

        instance = new GHRequest(lat0, lon0, lat1, lon1, favoredHeadings.get(0), favoredHeadings.get(1));
        compareFavoredHeadings(instance, favoredHeadings.subList(0, 2));
        comparePoints(instance, points.subList(0, 2));

        // check init without favoredHeadings
        instance = new GHRequest(points);
        comparePoints(instance, points);
        compareFavoredHeadings(instance, emptyList());

        instance = new GHRequest(points.get(0), points.get(1));
        comparePoints(instance, points.subList(0, 2));
        compareFavoredHeadings(instance, emptyList());

        instance = new GHRequest(lat0, lon0, lat1, lon1);
        comparePoints(instance, points.subList(0, 2));
        compareFavoredHeadings(instance, emptyList());
    }

    private void comparePoints(GHRequest request, List<GHPoint> points) {
        assertEquals("Points do not match", points, request.getPoints());
    }

    private void compareFavoredHeadings(GHRequest request, List<Double> expected) {
        assertEquals(expected.size(), request.getHeadings().size());
        for (int i = 0; i < expected.size(); i++) {
            double favoredHeading = request.getHeadings().get(i);
            assertEquals(i + " favored Heading does not match" + expected.get(i) + " vs ." + favoredHeading,
                    expected.get(i), favoredHeading, 0.01);
        }

    }
}
