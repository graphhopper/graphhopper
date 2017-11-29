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

import com.graphhopper.coll.GHIntObjectHashMap;
import com.graphhopper.storage.DAType;
import com.graphhopper.storage.DataAccess;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.GHDirectory;
import com.graphhopper.util.BitUtil;
import com.graphhopper.util.Downloader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;

/**
 * Common functionality used when working with SRTM hgt data.
 *
 * @author Robin Boldt
 */
public abstract class AbstractSRTMElevationProvider implements ElevationProvider {

    private static final BitUtil BIT_UTIL = BitUtil.BIG;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final int DEFAULT_WIDTH;
    private final int WIDTH_BYTE_INDEX = 0;
    // use a map as an array is not quite useful if we want to hold only parts of the world
    private final GHIntObjectHashMap<HeightTile> cacheData = new GHIntObjectHashMap<HeightTile>();
    private final double precision = 1e7;
    private final double invPrecision = 1 / precision;
    private Directory dir;
    private DAType daType = DAType.MMAP;
    private Downloader downloader;
    private File cacheDir;
    private String baseUrl;
    private boolean calcMean = false;

    public AbstractSRTMElevationProvider(String baseUrl, String cacheDir, String downloaderName, int defaultWidt) {
        this.baseUrl = baseUrl;
        this.cacheDir = new File(cacheDir);
        downloader = new Downloader(downloaderName).setTimeout(10000);
        this.DEFAULT_WIDTH = defaultWidt;
    }

    @Override
    public void setCalcMean(boolean calcMean) {
        this.calcMean = calcMean;
    }

    // use int key instead of string for lower memory usage
    int calcIntKey(double lat, double lon) {
        // we could use LinearKeyAlgo but this is simpler as we only need integer precision:
        return (down(lat) + 90) * 1000 + down(lon) + 180;
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

    int down(double val) {
        int intVal = (int) val;
        if (val >= 0 || intVal - val < invPrecision)
            return intVal;
        return intVal - 1;
    }

    @Override
    public double getEle(double lat, double lon) {
        // Return fast, if there is no data available
        // See https://www2.jpl.nasa.gov/srtm/faq.html
        if (lat >= 60 || lat <= -56)
            return 0;

        lat = (int) (lat * precision) / precision;
        lon = (int) (lon * precision) / precision;
        int intKey = calcIntKey(lat, lon);
        HeightTile demProvider = cacheData.get(intKey);
        if (demProvider != null)
            return demProvider.getHeight(lat, lon);

        if (!cacheDir.exists())
            cacheDir.mkdirs();

        String fileName = getFileName(lat, lon);
        if (fileName == null)
            return 0;

        DataAccess heights = getDirectory().find("dem" + intKey);
        boolean loadExisting = false;
        try {
            loadExisting = heights.loadExisting();
        } catch (Exception ex) {
            logger.warn("cannot load dem" + intKey + ", error:" + ex.getMessage());
        }

        if (!loadExisting)
            updateHeightsFromZipFile(lat, lon, fileName, heights);

        int width = (int) (Math.sqrt(heights.getHeader(WIDTH_BYTE_INDEX)) + 0.5);
        if (width == 0)
            width = DEFAULT_WIDTH;

        demProvider = new HeightTile(down(lat), down(lon), width, width, precision, 1, 1);
        cacheData.put(intKey, demProvider);
        demProvider.setCalcMean(calcMean);
        demProvider.setHeights(heights);
        return demProvider.getHeight(lat, lon);
    }

    void updateHeightsFromZipFile(double lat, double lon, String fileDetails, DataAccess heights) throws RuntimeException {
        try {
            byte[] bytes = getByteArrayFromZipFile(lat, lon, fileDetails);
            heights.create(bytes.length);
            for (int bytePos = 0; bytePos < bytes.length; bytePos += 2) {
                short val = BIT_UTIL.toShort(bytes, bytePos);
                if (val < -1000 || val > 12000)
                    val = Short.MIN_VALUE;

                heights.setShort(bytePos, val);
            }
            heights.setHeader(WIDTH_BYTE_INDEX, bytes.length / 2);
            heights.flush();

        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private byte[] getByteArrayFromZipFile(double lat, double lon, String fileDetails) throws InterruptedException, FileNotFoundException, IOException {
        String zippedURL = baseUrl + getDownloadURL(lat, lon);
        File file = new File(cacheDir, new File(zippedURL).getName());
        InputStream is;
        // get zip file if not already in cacheDir
        if (!file.exists())
            for (int i = 0; i < 3; i++) {
                try {
                    downloader.downloadFile(zippedURL, file.getAbsolutePath());
                    break;
                } catch (SocketTimeoutException ex) {
                    // just try again after a little nap
                    Thread.sleep(2000);
                    continue;
                }
            }

        return readFile(file);
    }

    abstract byte[] readFile(File file) throws IOException;

    @Override
    public void release() {
        cacheData.clear();

        // for memory mapped type we create temporary unpacked files which should be removed
        if (dir != null)
            dir.clear();
    }

    Directory getDirectory() {
        if (dir != null)
            return dir;

        logger.info(this.toString() + " Elevation Provider, from: " + baseUrl + ", to: " + cacheDir + ", as: " + daType);
        return dir = new GHDirectory(cacheDir.getAbsolutePath(), daType);
    }

    abstract String getFileName(double lat, double lon);

    abstract String getDownloadURL(double lat, double lon);

}