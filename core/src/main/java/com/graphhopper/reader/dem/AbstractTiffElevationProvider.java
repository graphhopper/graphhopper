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
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.GHDirectory;
import com.graphhopper.util.Downloader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.Raster;
import java.io.File;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;

/**
 * Provides basic methods that are usually used in an ElevationProvider that reads tiff files.
 *
 * @author Robin Boldt
 */
public abstract class AbstractTiffElevationProvider implements ElevationProvider {
    final Logger logger = LoggerFactory.getLogger(getClass());
    final Map<String, HeightTile> cacheData = new HashMap<String, HeightTile>();
    protected Downloader downloader;
    File cacheDir;
    String baseUrl;
    private Directory dir;
    private DAType daType = DAType.MMAP;
    boolean calcMean = false;
    boolean autoRemoveTemporary = true;
    long sleep = 2000;

    public AbstractTiffElevationProvider(String baseUrl, String cacheDir, String downloaderName) {
        this.baseUrl = baseUrl;
        this.cacheDir = new File(cacheDir);
        downloader = new Downloader(downloaderName).setTimeout(10000);
    }

    @Override
    public void setCalcMean(boolean eleCalcMean) {
        calcMean = eleCalcMean;
    }

    void setSleep(long sleep) {
        this.sleep = sleep;
    }

    /**
     * Creating temporary files can take a long time as we need to unpack tiff as well as to fill
     * our DataAccess object, so this option can be used to disable the default clear mechanism via
     * specifying 'false'.
     */
    public void setAutoRemoveTemporaryFiles(boolean autoRemoveTemporary) {
        this.autoRemoveTemporary = autoRemoveTemporary;
    }

    public void setDownloader(Downloader downloader) {
        this.downloader = downloader;
    }

    @Override
    public ElevationProvider setCacheDir(File cacheDir) {
        if (cacheDir.exists() && !cacheDir.isDirectory())
            throw new IllegalArgumentException("Cache path has to be a directory");
        try {
            this.cacheDir = cacheDir.getCanonicalFile();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        return this;
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


    @Override
    public void release() {
        cacheData.clear();

        // for memory mapped type we create temporary unpacked files which should be removed
        if (autoRemoveTemporary && dir != null)
            dir.clear();
    }

    /**
     * Download a file at the provided url and save it as the given downloadFile if the downloadFile does not exist.
     */
    protected void downloadFile(File downloadFile, String url) throws IOException {
        if (!downloadFile.exists()) {
            int max = 3;
            for (int trial = 0; trial < max; trial++) {
                try {
                    downloader.downloadFile(url, downloadFile.getAbsolutePath());
                    return;
                } catch (SocketTimeoutException ex) {
                    if (trial >= max - 1)
                        throw new RuntimeException(ex);
                    try {
                        Thread.sleep(sleep);
                    } catch (InterruptedException ignored) {
                    }
                }
            }
        }
    }

    protected void fillDataAccessWithElevationData(Raster raster, DataAccess heights, int dataAccessWidth) {
        final int height = raster.getHeight();
        final int width = raster.getWidth();
        int x = 0;
        int y = 0;
        try {
            for (y = 0; y < height; y++) {
                for (x = 0; x < width; x++) {
                    short val = (short) raster.getPixel(x, y, (int[]) null)[0];
                    if (val < -1000 || val > 12000)
                        val = Short.MIN_VALUE;

                    heights.setShort(2 * (y * dataAccessWidth + x), val);
                }
            }
            heights.flush();

            // TODO remove tifName and zip?
        } catch (Exception ex) {
            throw new RuntimeException("Problem at x:" + x + ", y:" + y, ex);
        }
    }

    protected Directory getDirectory() {
        if (dir != null)
            return dir;

        logger.info(this.toString() + " Elevation Provider, from: " + baseUrl + ", to: " + cacheDir + ", as: " + daType);
        return dir = new GHDirectory(cacheDir.getAbsolutePath(), daType);
    }
}