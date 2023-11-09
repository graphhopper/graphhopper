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

import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.FetchMode;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.JsonFeature;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.Polygon;

import java.util.Map;

/**
 * This class is for internal usage only. It is subclassed by Janino, then special expressions are
 * injected into init, getSpeed and getPriority. At the end an instance is created and used in CustomWeighting.
 */
public abstract class CustomWeightingHelper {
    protected CustomWeightingHelper() {
    }

    public void init(EncodedValueLookup lookup, Map<String, JsonFeature> areas) {
    }

    public double getPriority(EdgeIteratorState edge, boolean reverse) {
        return 1;
    }

    public double getSpeed(EdgeIteratorState edge, boolean reverse) {
        return 1;
    }

    protected abstract double getMaxPriority();

    protected abstract double getMaxSpeed();

    public static boolean in(Polygon p, EdgeIteratorState edge) {
        BBox edgeBBox = GHUtility.createBBox(edge);
        BBox polyBBOX = p.getBounds();
        if (!polyBBOX.intersects(edgeBBox))
            return false;
        if (p.isRectangle() && polyBBOX.contains(edgeBBox))
            return true;
        return p.intersects(edge.fetchWayGeometry(FetchMode.ALL).makeImmutable()); // TODO PERF: cache bbox and edge wayGeometry for multiple area
    }
}
