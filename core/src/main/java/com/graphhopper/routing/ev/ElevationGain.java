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

package com.graphhopper.routing.ev;

import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.PointList;

public class ElevationGain {
    public static final String KEY = "elevation_gain";

    public static GeometryEncodedValue create() {
        return new ElevationGainEncodedValue();
    }

    private static class ElevationGainEncodedValue extends UnsignedDecimalEncodedValue implements GeometryEncodedValue {
        public ElevationGainEncodedValue() {
            // todonow: how many bits etc.
            super(KEY, 32, 1, true);
        }

        @Override
        public double calculateDecimal(boolean reverse, IntsRef ref, PointList geometry) {
            // todonow: handle !3D
            // todonow: we probably need to handle several edge cases, like very short edges etc. see also Bike2WeightFlagEncoder
            double gain = 0;
            double prev = geometry.getEle(0);
            for (int i = 1; i < geometry.size(); i++) {
                double ele = geometry.getEle(i);
                double delta = ele - prev;
                if (!reverse && delta > 0)
                    gain += delta;
                if (reverse && delta < 0)
                    gain -= delta;
                prev = ele;
            }
            return gain;
        }
    }
}
