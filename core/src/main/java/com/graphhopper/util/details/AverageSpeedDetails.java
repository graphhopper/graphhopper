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
package com.graphhopper.util.details;

import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.util.EdgeIteratorState;

/**
 * Calculate the average speed segments for a Path
 *
 * @author Robin Boldt
 */
public class AverageSpeedDetails implements PathDetailsCalculator {

    private final FlagEncoder encoder;
    private final PathDetailsBuilder pathDetailsBuilder;
    private double curAvgSpeed = -1;

    public AverageSpeedDetails(FlagEncoder encoder) {
        this.encoder = encoder;
        this.pathDetailsBuilder = new PathDetailsBuilder(this.getName());
    }

    @Override
    public boolean isEdgeDifferentToLastEdge(EdgeIteratorState edge) {
        if (encoder.getSpeed(edge.getFlags()) != curAvgSpeed) {
            this.curAvgSpeed = this.encoder.getSpeed(edge.getFlags());
            return true;
        }
        return false;
    }

    @Override
    public PathDetailsBuilder getPathDetailsBuilder() {
        return this.pathDetailsBuilder;
    }

    @Override
    public Object getCurrentValue() {
        return this.curAvgSpeed;
    }

    @Override
    public String getName() {
        return "average_speed";
    }

}
