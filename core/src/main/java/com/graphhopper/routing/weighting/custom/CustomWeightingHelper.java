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
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.Polygon;

import java.util.Map;

/**
 * This class is for internal usage only. It is subclassed by Janino, then special expressions are injected into init,
 * getSpeed and getPriority. At the end an instance is created and used in CustomWeighting.
 */
public class CustomWeightingHelper {
    protected DecimalEncodedValue avg_speed_enc;
    protected DecimalEncodedValue priority_enc;
    protected EncodedValueLookup lookup;
    protected CustomModel customModel;

    protected CustomWeightingHelper() {
    }

    public void init(CustomModel customModel, EncodedValueLookup lookup, DecimalEncodedValue avgSpeedEnc, DecimalEncodedValue priorityEnc, Map<String, JsonFeature> areas) {
        this.lookup = lookup;
        this.customModel = customModel;
        this.avg_speed_enc = avgSpeedEnc;
        this.priority_enc = priorityEnc;
    }

    public double getPriority(EdgeIteratorState edge, boolean reverse) {
        return 1;
    }

    public double getSpeed(EdgeIteratorState edge, boolean reverse) {
        return getRawSpeed(edge, reverse);
    }

    protected final double getRawSpeed(EdgeIteratorState edge, boolean reverse) {
        double speed = reverse ? edge.getReverse(avg_speed_enc) : edge.get(avg_speed_enc);
        if (Double.isInfinite(speed) || Double.isNaN(speed) || speed < 0)
            throw new IllegalStateException("Invalid estimated speed " + speed);
        return speed;
    }

    protected final double getRawPriority(EdgeIteratorState edge, boolean reverse) {
        if (priority_enc == null) return 1;
        double priority = reverse ? edge.getReverse(priority_enc) : edge.get(priority_enc);
        if (Double.isInfinite(priority) || Double.isNaN(priority) || priority < 0)
            throw new IllegalStateException("Invalid priority " + priority);
        return priority;
    }

    public final double calcMaxSpeed() {
        MinMax minMaxSpeed = new MinMax(1, avg_speed_enc.getMaxOrMaxStorableDecimal());
        FindMinMax.findMinMax(minMaxSpeed, customModel.getSpeed(), lookup);
        if (minMaxSpeed.min < 0)
            throw new IllegalArgumentException("speed has to be >=0 but can be negative (" + minMaxSpeed.min + ")");
        if (minMaxSpeed.max <= 0)
            throw new IllegalArgumentException("maximum speed has to be >0 but was " + minMaxSpeed.max);

        return minMaxSpeed.max;
    }

    public final double calcMaxPriority() {
        // initial value of minimum has to be >0 so that multiple_by with a negative value leads to a negative value and not 0
        MinMax minMaxPriority = new MinMax(1, 1);
        FindMinMax.findMinMax(minMaxPriority, customModel.getPriority(), lookup);
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
}
