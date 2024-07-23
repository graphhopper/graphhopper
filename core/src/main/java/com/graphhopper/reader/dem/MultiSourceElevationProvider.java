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
public class MultiSourceElevationProvider extends TileBasedElevationProvider {

    // Usually a high resolution provider in the SRTM area
    private final TileBasedElevationProvider primary;
    // The fallback provider that provides elevation data globally
    private final TileBasedElevationProvider fallback;

    // While we may have precise elevation data for certain coordinates lying
    // on the edges of a defined supported area, a TileElevationProvider's
    // implementation could result in a situation where it requests elevation
    // data from an adjacent tile instead, in consistency with its naming
    // convention.
    //
    // Removing a small buffer region from the supported area eliminates this
    // literal edge case.
    private final double DEGREES_BUFFER = 0.001;

    public MultiSourceElevationProvider(
            TileBasedElevationProvider primary,
            TileBasedElevationProvider fallback) {
        super(primary.cacheDir.getAbsolutePath());
        this.primary = primary;
        this.fallback = fallback;
    }

    public MultiSourceElevationProvider() {
        this(new CGIARProvider(), new GMTEDProvider());
    }

    public MultiSourceElevationProvider(String cacheDir) {
        this(new USGSProvider(cacheDir), new SRTMProvider(cacheDir));
    }

    @Override
    public double getEle(double lat, double lon) {
        if (lat > 37.5 + DEGREES_BUFFER && lat < 38.25 - DEGREES_BUFFER &&
                lon > -122.75 + DEGREES_BUFFER && lon < -122 - DEGREES_BUFFER)
            return primary.getEle(lat, lon);
        return fallback.getEle(lat, lon);
    }

    /**
     * For the MultiSourceElevationProvider you have to specify the base URL separated by a ';'.
     * The first for cgiar, the second for gmted.
     */
    @Override
    public MultiSourceElevationProvider setBaseURL(String baseURL) {
        String[] urls = baseURL.split(";");
        if (urls.length != 2) {
            throw new IllegalArgumentException("The base url must consist of two urls separated by a ';'. The first for cgiar, the second for gmted");
        }
        primary.setBaseURL(urls[0]);
        fallback.setBaseURL(urls[1]);
        return this;
    }

    @Override
    public MultiSourceElevationProvider setDAType(DAType daType) {
        primary.setDAType(daType);
        fallback.setDAType(daType);
        return this;
    }

    @Override
    public MultiSourceElevationProvider setInterpolate(boolean interpolate) {
        primary.setInterpolate(interpolate);
        fallback.setInterpolate(interpolate);
        return this;
    }

    @Override
    public boolean canInterpolate() {
        return primary.canInterpolate() && fallback.canInterpolate();
    }

    @Override
    public void release() {
        primary.release();
        fallback.release();
    }

    @Override
    public MultiSourceElevationProvider setAutoRemoveTemporaryFiles(boolean autoRemoveTemporary) {
        primary.setAutoRemoveTemporaryFiles(autoRemoveTemporary);
        fallback.setAutoRemoveTemporaryFiles(autoRemoveTemporary);
        return this;
    }

    @Override
    public String toString() {
        return "multi";
    }
}
