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
package com.graphhopper.routing.weighting.custom;

import com.graphhopper.json.MinMax;
import com.graphhopper.json.Statement;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.Polygon;

import java.util.List;
import java.util.Map;

/**
 * This class is for internal usage only. It is subclassed by Janino, then special expressions are
 * injected into init, getSpeed and getPriority. At the end an instance is created and used in CustomWeighting.
 */
public class CustomWeightingHelper {
    static double GLOBAL_MAX_SPEED = 999;
    static double GLOBAL_PRIORITY = 1;

    protected EncodedValueLookup lookup;
    protected CustomModel customModel;

    protected CustomWeightingHelper() {
    }

    public void init(CustomModel customModel, EncodedValueLookup lookup, Map<String, JsonFeature> areas) {
        this.lookup = lookup;
        this.customModel = customModel;
    }

    public double getPriority(EdgeIteratorState edge, boolean reverse) {
        return 1;
    }

    public double getSpeed(EdgeIteratorState edge, boolean reverse) {
        return 1;
    }

    public double getTurnPenalty(BaseGraph graph, EdgeIntAccess edgeIntAccess, int inEdge, int viaNode, int outEdge) {
        return 0;
    }

    public final double calcMaxSpeed() {
        MinMax minMaxSpeed = new MinMax(0, GLOBAL_MAX_SPEED);
        FindMinMax.findMinMax(minMaxSpeed, customModel.getSpeed(), lookup);
        if (minMaxSpeed.min < 0)
            throw new IllegalArgumentException("speed has to be >=0 but can be negative (" + minMaxSpeed.min + ")");
        if (minMaxSpeed.max <= 0)
            throw new IllegalArgumentException("maximum speed has to be >0 but was " + minMaxSpeed.max);
        if (minMaxSpeed.max == GLOBAL_MAX_SPEED)
            throw new IllegalArgumentException("The first statement for 'speed' must be unconditionally to set the speed. But it was " + customModel.getSpeed().get(0));

        return minMaxSpeed.max;
    }

    public final double calcMaxPriority() {
        MinMax minMaxPriority = new MinMax(0, GLOBAL_PRIORITY);
        List<Statement> statements = customModel.getPriority();
        if (!statements.isEmpty() && "true".equals(statements.get(0).condition())) {
            String value = statements.get(0).value();
            if (lookup.hasEncodedValue(value))
                minMaxPriority.max = lookup.getDecimalEncodedValue(value).getMaxOrMaxStorableDecimal();
        }
        FindMinMax.findMinMax(minMaxPriority, statements, lookup);
        if (minMaxPriority.min < 0)
            throw new IllegalArgumentException("priority has to be >=0 but can be negative (" + minMaxPriority.min + ")");
        if (minMaxPriority.max < 0)
            throw new IllegalArgumentException("maximum priority has to be >=0 but was " + minMaxPriority.max);
        return minMaxPriority.max;
    }

    public static boolean in(Polygon p, EdgeIteratorState edge) {
        BBox edgeBBox = GHUtility.createBBox(edge);
        BBox polyBBOX = p.getBounds();
        if (!polyBBOX.intersects(edgeBBox))
            return false;
        if (p.isRectangle() && polyBBOX.contains(edgeBBox))
            return true;
        return p.intersects(edge.fetchWayGeometry(FetchMode.ALL).makeImmutable()); // TODO PERF: cache bbox and edge wayGeometry for multiple area
    }

    public static double calcChangeAngle(EdgeIntAccess edgeIntAccess, DecimalEncodedValue orientationEnc,
                                         int inEdge, boolean inEdgeReverse, int outEdge, boolean outEdgeReverse) {

        double prevAzimuth = orientationEnc.getDecimal(inEdgeReverse, inEdge, edgeIntAccess);
        double azimuth = orientationEnc.getDecimal(outEdgeReverse, outEdge, edgeIntAccess);

        // bring parallel to prevOrientation
        azimuth = (azimuth + 180) % 360.0;

        double changeAngle = azimuth - prevAzimuth;

        // keep in [-180, 180]
        return (changeAngle + 540.0) % 360.0 - 180.0;
    }
}
