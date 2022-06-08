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
import com.graphhopper.storage.DataAccess;
import com.graphhopper.util.BitUtil;
import com.graphhopper.util.Downloader;

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
public abstract class AbstractSRTMElevationProvider extends TileBasedElevationProvider {
    private final int DEFAULT_WIDTH;
    private final int MIN_LAT;
    private final int MAX_LAT;
    private final int WIDTH_BYTE_INDEX = 0;
    private final int DEGREE = 1;
    // use a map as an array is not quite useful if we want to hold only parts of the world
    private final GHIntObjectHashMap<HeightTile> cacheData = new GHIntObjectHashMap<>();
    private final double precision = 1e7;
    private final double invPrecision = 1 / precision;

    public AbstractSRTMElevationProvider(String baseUrl, String cacheDir, String downloaderName, int minLat, int maxLat, int defaultWidth) {
        super(cacheDir);
        this.baseUrl = baseUrl;
        downloader = new Downloader(downloaderName).setTimeout(10000);
        this.DEFAULT_WIDTH = defaultWidth;
        this.MIN_LAT = minLat;
        this.MAX_LAT = maxLat;
    }

    // use int key instead of string for lower memory usage
    int calcIntKey(double lat, double lon) {
        // we could use LinearKeyAlgo but this is simpler as we only need integer precision:
        return (down(lat) + 90) * 1000 + down(lon) + 180;
    }

    @Override
    public void release() {
        cacheData.clear();
        if (dir != null) {
            // for memory mapped type we remove temporary files
            if (autoRemoveTemporary)
                dir.clear();
            else
                dir.close();
        }
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
        if (lat >= MAX_LAT || lat <= MIN_LAT)
            return 0;

        lat = (int) (lat * precision) / precision;
        lon = (int) (lon * precision) / precision;
        int intKey = calcIntKey(lat, lon);
        HeightTile demProvider = cacheData.get(intKey);
        if (demProvider == null) {
            if (!cacheDir.exists())
                cacheDir.mkdirs();

            int minLat = down(lat);
            int minLon = down(lon);

            String fileName = getFileName(lat, lon);
            if (fileName == null)
                return 0;

            DataAccess heights = getDirectory().create("dem" + intKey);
            boolean loadExisting = false;
            try {
                loadExisting = heights.loadExisting();
            } catch (Exception ex) {
                logger.warn("cannot load dem" + intKey + ", error:" + ex.getMessage());
            }

            if (!loadExisting) {
                try {
                    updateHeightsFromFile(lat, lon, heights);
                } catch (FileNotFoundException ex) {
                    demProvider = new HeightTile(minLat, minLon, DEFAULT_WIDTH, DEFAULT_WIDTH, precision, DEGREE, DEGREE);
                    cacheData.put(intKey, demProvider);
                    demProvider.setHeights(heights);
                    demProvider.setSeaLevel(true);
                    // use small size on disc and in-memory
                    heights.create(10)
                            .flush();
                    return 0;
                }
            }

            int width = (int) (Math.sqrt(heights.getHeader(WIDTH_BYTE_INDEX)) + 0.5);
            if (width == 0)
                width = DEFAULT_WIDTH;

            demProvider = new HeightTile(minLat, minLon, width, width, precision, DEGREE, DEGREE);
            cacheData.put(intKey, demProvider);
            demProvider.setInterpolate(interpolate);
            demProvider.setHeights(heights);
        }

        if (demProvider.isSeaLevel())
            return 0;

        return demProvider.getHeight(lat, lon);
    }

    private void updateHeightsFromFile(double lat, double lon, DataAccess heights) throws FileNotFoundException {
        try {
            byte[] bytes = getByteArrayFromFile(lat, lon);
            heights.create(bytes.length);
            for (int bytePos = 0; bytePos < bytes.length; bytePos += 2) {
                // we need big endianess to read the SRTM files
                short val = BitUtil.BIG.toShort(bytes, bytePos);
                if (val < -1000 || val > 12000)
                    val = Short.MIN_VALUE;

                heights.setShort(bytePos, val);
            }
            heights.setHeader(WIDTH_BYTE_INDEX, bytes.length / 2);
            heights.flush();

        } catch (FileNotFoundException ex) {
            logger.warn("File not found for the coordinates for " + lat + "," + lon);
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException("There was an issue looking up the coordinates for " + lat + "," + lon, ex);
        }
    }

    private byte[] getByteArrayFromFile(double lat, double lon) throws InterruptedException, IOException {
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
                } catch (FileNotFoundException ex) {
                    if (zippedURL.contains(".hgt.zip")) {
                        zippedURL = zippedURL.replace(".hgt.zip", "hgt.zip");
                    } else {
                        throw ex;
                    }
                }
            }

        return readFile(file);
    }

    protected String getPaddedLonString(int lonInt) {
        lonInt = Math.abs(lonInt);
        String lonString = lonInt < 100 ? "0" : "";
        if (lonInt < 10)
            lonString += "0";
        lonString += lonInt;
        return lonString;
    }

    protected String getPaddedLatString(int latInt) {
        latInt = Math.abs(latInt);
        String latString = latInt < 10 ? "0" : "";
        latString += latInt;
        return latString;
    }

    abstract byte[] readFile(File file) throws IOException;

    /**
     * Return the local file name without file ending, has to be lower case, because DataAccess only supports lower case names.
     */
    abstract String getFileName(double lat, double lon);

    /**
     * Returns the complete URL to download the file
     */
    abstract String getDownloadURL(double lat, double lon);

}
