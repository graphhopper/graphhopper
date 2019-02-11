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

/**
 * The MultiSourceElevationProvider mixes different elevation providers to provide the best available elevation data
 * for a certain area.
 *
 * @author Robin Boldt
 */
public class MultiSourceElevationProvider implements ElevationProvider {

    // Usually a high resolution provider in the SRTM area
    private ElevationProvider srtmProvider;
    // The fallback provider that provides elevation data globally
    private ElevationProvider globalProvider;

    public MultiSourceElevationProvider(ElevationProvider srtmProvider, ElevationProvider globalProvider) {
        this.srtmProvider = srtmProvider;
        this.globalProvider = globalProvider;
    }

    public MultiSourceElevationProvider() {
        this(new CGIARProvider(), new GMTEDProvider());
    }

    public MultiSourceElevationProvider(String cacheDir) {
        this(new CGIARProvider(cacheDir), new GMTEDProvider(cacheDir));
    }

    @Override
    public double getEle(double lat, double lon) {
        // Sometimes the cgiar data north of 59.999 equals 0
        if (lat < 59.999 && lat > -56) {
            return srtmProvider.getEle(lat, lon);
        }
        return globalProvider.getEle(lat, lon);
    }

    /**
     * For the MultiSourceElevationProvider you have to specify the base URL separated by a ';'.
     * The first for cgiar, the second for gmted.
     */
    @Override
    public ElevationProvider setBaseURL(String baseURL) {
        String[] urls = baseURL.split(";");
        if (urls.length != 2) {
            throw new IllegalArgumentException("The base url must consist of two urls separated by a ';'. The first for cgiar, the second for gmted");
        }
        srtmProvider.setBaseURL(urls[0]);
        globalProvider.setBaseURL(urls[1]);
        return this;
    }

    @Override
    public ElevationProvider setDAType(DAType daType) {
        srtmProvider.setDAType(daType);
        globalProvider.setDAType(daType);
        return this;
    }

    @Override
    public void setCalcMean(boolean calcMean) {
        srtmProvider.setCalcMean(calcMean);
        globalProvider.setCalcMean(calcMean);
    }

    @Override
    public void release() {
        srtmProvider.release();
        globalProvider.release();
    }

    @Override
    public void setAutoRemoveTemporaryFiles(boolean autoRemoveTemporary) {
        srtmProvider.setAutoRemoveTemporaryFiles(autoRemoveTemporary);
        globalProvider.setAutoRemoveTemporaryFiles(autoRemoveTemporary);
    }

    @Override
    public String toString() {
        return "multi";
    }

}