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

import com.graphhopper.storage.DataAccess;
import com.graphhopper.core.util.Downloader;

import javax.net.ssl.SSLException;
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
public abstract class AbstractTiffElevationProvider extends TileBasedElevationProvider {
    private final Map<String, HeightTile> cacheData = new HashMap<>();
    final double precision = 1e7;

    private final int WIDTH;
    private final int HEIGHT;

    // Degrees of latitude covered by this tile
    final int LAT_DEGREE;
    // Degrees of longitude covered by this tile
    final int LON_DEGREE;

    public AbstractTiffElevationProvider(String baseUrl, String cacheDir, String downloaderName, int width, int height, int latDegree, int lonDegree) {
        super(cacheDir);
        this.baseUrl = baseUrl;
        this.downloader = new Downloader(downloaderName).setTimeout(10000);
        this.WIDTH = width;
        this.HEIGHT = height;
        this.LAT_DEGREE = latDegree;
        this.LON_DEGREE = lonDegree;
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

    /**
     * Return true if the coordinates are outside of the supported area
     */
    abstract boolean isOutsideSupportedArea(double lat, double lon);

    /**
     * The smallest lat that is still in the HeightTile
     */
    abstract int getMinLatForTile(double lat);

    /**
     * The smallest lon that is still in the HeightTile
     */
    abstract int getMinLonForTile(double lon);

    /**
     * Specify the name of the file after downloading
     */
    abstract String getFileNameOfLocalFile(double lat, double lon);

    /**
     * Return the local file name without file ending, has to be lower case, because DataAccess only supports lower case names.
     */
    abstract String getFileName(double lat, double lon);

    /**
     * Returns the complete URL to download the file
     */
    abstract String getDownloadURL(double lat, double lon);

    @Override
    public double getEle(double lat, double lon) {
        // Return fast, if there is no data available
        if (isOutsideSupportedArea(lat, lon))
            return 0;

        lat = (int) (lat * precision) / precision;
        lon = (int) (lon * precision) / precision;
        String name = getFileName(lat, lon);
        HeightTile demProvider = cacheData.get(name);
        if (demProvider == null) {
            if (!cacheDir.exists())
                cacheDir.mkdirs();

            int minLat = getMinLatForTile(lat);
            int minLon = getMinLonForTile(lon);
            // less restrictive against boundary checking
            demProvider = new HeightTile(minLat, minLon, WIDTH, HEIGHT, LON_DEGREE * precision, LON_DEGREE, LAT_DEGREE);
            demProvider.setInterpolate(interpolate);

            cacheData.put(name, demProvider);
            DataAccess heights = getDirectory().create(name + ".gh");
            demProvider.setHeights(heights);
            boolean loadExisting = false;
            try {
                loadExisting = heights.loadExisting();
            } catch (Exception ex) {
                logger.warn("cannot load " + name + ", error: " + ex.getMessage());
            }

            if (!loadExisting) {
                File zipFile = new File(cacheDir, new File(getFileNameOfLocalFile(lat, lon)).getName());
                if (!zipFile.exists())
                    try {
                        String zippedURL = getDownloadURL(lat, lon);
                        downloadToFile(zipFile, zippedURL);
                    } catch (SSLException ex) {
                        throw new IllegalStateException("SSL problem with elevation provider " + getClass().getSimpleName(), ex);
                    } catch (IOException ex) {
                        demProvider.setSeaLevel(true);
                        // use small size on disc and in-memory
                        heights.create(10).flush();
                        return 0;
                    }

                // short == 2 bytes
                heights.create(2L * WIDTH * HEIGHT);

                Raster raster = readFile(zipFile, name + ".tif");
                fillDataAccessWithElevationData(raster, heights, WIDTH);

            } // loadExisting
        }

        if (demProvider.isSeaLevel())
            return 0;

        return demProvider.getHeight(lat, lon);
    }

    abstract Raster readFile(File file, String tifName);

    /**
     * Download a file at the provided url and save it as the given downloadFile if the downloadFile does not exist.
     */
    private void downloadToFile(File downloadFile, String url) throws IOException {
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

    private void fillDataAccessWithElevationData(Raster raster, DataAccess heights, int dataAccessWidth) {
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

                    heights.setShort(2 * ((long) y * dataAccessWidth + x), val);
                }
            }
            heights.flush();
        } catch (Exception ex) {
            throw new RuntimeException("Problem at x:" + x + ", y:" + y, ex);
        }
    }
}
