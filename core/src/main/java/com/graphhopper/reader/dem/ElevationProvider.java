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

import com.graphhopper.storage.DAType;

import java.io.File;

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
        public ElevationProvider setCacheDir(File cacheDir) {
            return this;
        }

        @Override
        public ElevationProvider setBaseURL(String baseURL) {
            return this;
        }

        @Override
        public ElevationProvider setDAType(DAType daType) {
            return this;
        }

        @Override
        public void release() {
        }

        @Override
        public void setCalcMean(boolean eleCalcMean) {
        }
    };

    /**
     * @return returns the height in meters or Double.NaN if invalid
     */
    double getEle(double lat, double lon);

    /**
     * Specifies the service URL where to download the elevation data. An empty string should set it
     * to the default URL. Default is a provider-dependent URL which should work out of the box.
     */
    ElevationProvider setBaseURL(String baseURL);

    /**
     * Specifies the directory where to temporarily store the elevation data after fetched from base
     * URL. Default is a custom provider-dependent subdirectory in '/tmp'
     */
    ElevationProvider setCacheDir(File cacheDir);

    /**
     * Set to true if you have a small area and need high speed access. Default is DAType.MMAP
     */
    ElevationProvider setDAType(DAType daType);

    /**
     * Configuration option to include surrounding elevation points when fetching the elevation. Has
     * only an effect if called before the first getEle call. Turned off by default.
     */
    void setCalcMean(boolean calcMean);

    /**
     * Release resources.
     */
    void release();
}
