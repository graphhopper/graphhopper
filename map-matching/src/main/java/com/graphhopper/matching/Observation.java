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
package com.graphhopper.matching;

import com.graphhopper.core.util.shapes.GHPoint;

import java.util.Objects;

public class Observation {
    private GHPoint point;
    private double accumulatedLinearDistanceToPrevious;

    public Observation(GHPoint p) {
        this.point = p;
    }

    public GHPoint getPoint() {
        return point;
    }

    public double getAccumulatedLinearDistanceToPrevious() {
        return accumulatedLinearDistanceToPrevious;
    }

    public void setAccumulatedLinearDistanceToPrevious(double accumulatedLinearDistanceToPrevious) {
        this.accumulatedLinearDistanceToPrevious = accumulatedLinearDistanceToPrevious;
    }

    @Override
    public String toString() {
        return "Observation{" +
                "point=" + point +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Observation that = (Observation) o;
        return Objects.equals(point, that.point);
    }

    @Override
    public int hashCode() {
        return Objects.hash(point);
    }
}
