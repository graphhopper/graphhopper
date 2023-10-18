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
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.GHDirectory;
import com.graphhopper.util.Downloader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * Provides basic methods that are usually used in an ElevationProvider using tiles from files.
 *
 * @author Robin Boldt
 */
public abstract class TileBasedElevationProvider implements ElevationProvider {
    final Logger logger = LoggerFactory.getLogger(getClass());
    Downloader downloader;
    final File cacheDir;
    String baseUrl;
    Directory dir;
    DAType daType = DAType.MMAP;
    boolean interpolate = false;
    boolean autoRemoveTemporary = true;
    long sleep = 2000;

    protected TileBasedElevationProvider(String cacheDirString) {
        File cacheDir = new File(cacheDirString);
        if (cacheDir.exists() && !cacheDir.isDirectory())
            throw new IllegalArgumentException("Cache path has to be a directory");
        try {
            this.cacheDir = cacheDir.getCanonicalFile();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Configuration option to use bilinear interpolation to find the elevation at a point from the
     * surrounding elevation points. Has only an effect if called before the first getEle call.
     * Turned off by default.
     */
    public TileBasedElevationProvider setInterpolate(boolean interpolate) {
        this.interpolate = interpolate;
        return this;
    }

    @Override
    public boolean canInterpolate() {
        return this.interpolate;
    }

    void setSleep(long sleep) {
        this.sleep = sleep;
    }

    /**
     * Specifies the service URL where to download the elevation data. An empty string should set it
     * to the default URL. Default is a provider-dependent URL which should work out of the box.
     */
    public TileBasedElevationProvider setBaseURL(String baseUrl) {
        if (baseUrl == null || baseUrl.isEmpty())
            throw new IllegalArgumentException("baseUrl cannot be empty");

        this.baseUrl = baseUrl;
        return this;
    }

    /**
     * Set to true if you have a small area and need high speed access. Default is DAType.MMAP
     */
    public TileBasedElevationProvider setDAType(DAType daType) {
        this.daType = daType;
        return this;
    }

    /**
     * Creating temporary files can take a long time as we need to unpack them as well as to fill
     * our DataAccess object, so this option can be used to disable the default clear mechanism via
     * specifying 'false'.
     */
    public TileBasedElevationProvider setAutoRemoveTemporaryFiles(boolean autoRemoveTemporary) {
        this.autoRemoveTemporary = autoRemoveTemporary;
        return this;
    }

    public TileBasedElevationProvider setDownloader(Downloader downloader) {
        this.downloader = downloader;
        return this;
    }

    protected File getCacheDir() {
        return cacheDir;
    }

    protected Directory getDirectory() {
        if (dir != null)
            return dir;

        logger.info(this.toString() + " Elevation Provider, from: " + baseUrl + ", to: " + cacheDir + ", as: " + daType +
                " using interpolate: " + interpolate);
        return dir = new GHDirectory(cacheDir.getAbsolutePath(), daType);
    }

}
