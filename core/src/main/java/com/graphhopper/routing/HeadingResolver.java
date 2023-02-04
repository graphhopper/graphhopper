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

package com.graphhopper.routing;

import com.graphhopper.core.util.PointList;
import com.carrotsearch.hppc.IntArrayList;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.AngleCalc;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.FetchMode;

public class HeadingResolver {
    private final EdgeExplorer edgeExplorer;
    private double toleranceRad = Math.toRadians(100);

    public HeadingResolver(Graph graph) {
        this.edgeExplorer = graph.createEdgeExplorer();
    }

    /**
     * Returns a list of edge IDs of edges adjacent to the given base node that do *not* have the same or a similar
     * heading as the given heading. If for example the tolerance is 45 degrees this method returns all edges for which
     * the absolute difference to the given heading is greater than 45 degrees. The heading of an edge is defined as
     * the direction of the first segment of an edge (adjacent and facing away from the base node).
     *
     * @param heading north based azimuth, between 0 and 360 degrees
     * @see #setTolerance
     */
    public IntArrayList getEdgesWithDifferentHeading(int baseNode, double heading) {
        double xAxisAngle = AngleCalc.ANGLE_CALC.convertAzimuth2xaxisAngle(heading);
        IntArrayList edges = new IntArrayList(1);
        EdgeIterator iter = edgeExplorer.setBaseNode(baseNode);
        while (iter.next()) {
            PointList points = iter.fetchWayGeometry(FetchMode.ALL);
            double orientation = AngleCalc.ANGLE_CALC.calcOrientation(
                    points.getLat(0), points.getLon(0),
                    points.getLat(1), points.getLon(1)
            );

            orientation = AngleCalc.ANGLE_CALC.alignOrientation(xAxisAngle, orientation);
            double diff = Math.abs(orientation - xAxisAngle);

            if (diff > toleranceRad)
                edges.add(iter.getEdge());
        }
        return edges;
    }

    /**
     * Sets the tolerance for {@link #getEdgesWithDifferentHeading} in degrees.
     */
    public HeadingResolver setTolerance(double tolerance) {
        this.toleranceRad = Math.toRadians(tolerance);
        return this;
    }
}
