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
import com.graphhopper.storage.DataAccess;
import com.graphhopper.util.Helper;
import org.apache.xmlgraphics.image.codec.tiff.TIFFDecodeParam;
import org.apache.xmlgraphics.image.codec.tiff.TIFFImageDecoder;
import org.apache.xmlgraphics.image.codec.util.SeekableStream;

import java.awt.image.Raster;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * The MultiSourceElevationProvider mixes different elevation providers to provide the best available elevation data
 * for a certain area.
 *
 * @author Robin Boldt
 */
public class MultiSourceElevationProvider implements ElevationProvider {

    private CGIARProvider cgiarProvider;
    private GMTEDProvider gmtedProvider;

    public MultiSourceElevationProvider(CGIARProvider cgiarProvider, GMTEDProvider gmtedProvider) {
        this.cgiarProvider = cgiarProvider;
        this.gmtedProvider = gmtedProvider;
    }

    public MultiSourceElevationProvider() {
        this(new CGIARProvider(), new GMTEDProvider());
    }

    @Override
    public double getEle(double lat, double lon) {
        // Sometimes the cgiar data north of 59.999 equals 0
        if (lat < 59.999 && lat > -56) {
            return cgiarProvider.getEle(lat, lon);
        }
        return gmtedProvider.getEle(lat, lon);
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
        cgiarProvider.setBaseURL(urls[0]);
        gmtedProvider.setBaseURL(urls[1]);
        return this;
    }

    @Override
    public ElevationProvider setCacheDir(File cacheDir) {
        cgiarProvider.setCacheDir(cacheDir);
        gmtedProvider.setCacheDir(cacheDir);
        return this;
    }

    @Override
    public ElevationProvider setDAType(DAType daType) {
        cgiarProvider.setDAType(daType);
        gmtedProvider.setDAType(daType);
        return this;
    }

    @Override
    public void setCalcMean(boolean calcMean) {
        cgiarProvider.setCalcMean(calcMean);
        gmtedProvider.setCalcMean(calcMean);
    }

    @Override
    public void release() {
        cgiarProvider.release();
        gmtedProvider.release();
    }

    @Override
    public String toString() {
        return "multi";
    }

}