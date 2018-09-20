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
 * Provides basic methods that are usually used in an ElevationProvider.
 *
 * @author Robin Boldt
 */
public abstract class AbstractElevationProvider implements ElevationProvider {
    final Logger logger = LoggerFactory.getLogger(getClass());
    Downloader downloader;
    final File cacheDir;
    String baseUrl;
    Directory dir;
    DAType daType = DAType.MMAP;
    boolean calcMean = false;
    boolean autoRemoveTemporary = true;
    long sleep = 2000;

    protected AbstractElevationProvider(String cacheDirString) {
        File cacheDir = new File(cacheDirString);
        if (cacheDir.exists() && !cacheDir.isDirectory())
            throw new IllegalArgumentException("Cache path has to be a directory");
        try {
            this.cacheDir = cacheDir.getCanonicalFile();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void setCalcMean(boolean eleCalcMean) {
        calcMean = eleCalcMean;
    }

    void setSleep(long sleep) {
        this.sleep = sleep;
    }

    @Override
    public void setAutoRemoveTemporaryFiles(boolean autoRemoveTemporary) {
        this.autoRemoveTemporary = autoRemoveTemporary;
    }

    public void setDownloader(Downloader downloader) {
        this.downloader = downloader;
    }

    protected File getCacheDir() {
        return cacheDir;
    }

    @Override
    public ElevationProvider setBaseURL(String baseUrl) {
        if (baseUrl == null || baseUrl.isEmpty())
            throw new IllegalArgumentException("baseUrl cannot be empty");

        this.baseUrl = baseUrl;
        return this;
    }

    @Override
    public ElevationProvider setDAType(DAType daType) {
        this.daType = daType;
        return this;
    }


    protected Directory getDirectory() {
        if (dir != null)
            return dir;

        logger.info(this.toString() + " Elevation Provider, from: " + baseUrl + ", to: " + cacheDir + ", as: " + daType +
                " using calcmean: " + calcMean);
        return dir = new GHDirectory(cacheDir.getAbsolutePath(), daType);
    }

    /**
     * Return the local file name without file ending, has to be lower case, because DataAccess only supports lower case names.
     */
    abstract String getFileName(double lat, double lon);

    /**
     * Returns the complete URL to download the file
     */
    abstract String getDownloadURL(double lat, double lon);

}
