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

import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.util.EdgeIteratorState;

import static com.graphhopper.util.Parameters.DETAILS.AVERAGE_SPEED;

/**
 * Calculate the average speed segments for a Path
 *
 * @author Robin Boldt
 */
public class AverageSpeedDetails extends AbstractPathDetailsBuilder {

    private final DecimalEncodedValue avSpeedEnc;
    private double curAvgSpeed = -1;

    public AverageSpeedDetails(FlagEncoder encoder) {
        super(AVERAGE_SPEED);
        this.avSpeedEnc = encoder.getDecimalEncodedValue(EncodingManager.getKey(encoder, "average_speed"));
    }

    @Override
    public boolean isEdgeDifferentToLastEdge(EdgeIteratorState edge) {
        double tmpSpeed = edge.get(avSpeedEnc);
        if (Math.abs(tmpSpeed - curAvgSpeed) > 0.0001) {
            curAvgSpeed = tmpSpeed;
            return true;
        }
        return false;
    }

    @Override
    public Object getCurrentValue() {
        return curAvgSpeed;
    }
}
