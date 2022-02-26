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
package com.graphhopper.reader.dem;

import com.graphhopper.reader.ReaderNode;

/**
 * @author Peter Karich
 */
public interface ElevationProvider {
    ElevationProvider NOOP = new ElevationProvider() {
        @Override
        public double getEle(double lat, double lon) {
            return Double.NaN;
        }

        @Override
        public void release() {
        }

        @Override
        public boolean canInterpolate() {
            return false;
        }
    };

    /**
     * @return returns the height in meters or Double.NaN if invalid
     */
    double getEle(double lat, double lon);

    /**
     * @param node Node to read
     * @return returns the height in meters or Double.NaN if invalid
     */
    default double getEle(ReaderNode node) {
        return getEle(node.getLat(), node.getLon());
    }

    /**
     * Returns true if bilinear interpolation is enabled.
     */
    boolean canInterpolate();

    /**
     * Release resources.
     */
    void release();
}
