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
import com.graphhopper.util.Helper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.SocketTimeoutException;
import java.util.zip.ZipInputStream;

/**
 * Elevation data from NASA (SRTM).
 * <p>
 * Important information about SRTM: the coordinates of the lower-left corner of tile N40W118 are 40
 * degrees north latitude and 118 degrees west longitude. To be more exact, these coordinates refer
 * to the geometric center of the lower left sample, which in the case of SRTM3 data will be about
 * 90 meters in extent.
 * <p>
 *
 * @author Peter Karich
 */
public class SRTMProvider implements ElevationProvider {
    private static final BitUtil BIT_UTIL = BitUtil.BIG;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final int DEFAULT_WIDTH = 1201;
    private final int WIDTH_BYTE_INDEX = 0;
    // use a map as an array is not quite useful if we want to hold only parts of the world
    private final GHIntObjectHashMap<HeightTile> cacheData = new GHIntObjectHashMap<HeightTile>();
    private final GHIntObjectHashMap<String> areas = new GHIntObjectHashMap<String>();
    private final double precision = 1e7;
    private final double invPrecision = 1 / precision;
    private Directory dir;
    private DAType daType = DAType.MMAP;
    private Downloader downloader = new Downloader("GraphHopper SRTMReader").setTimeout(10000);
    private File cacheDir = new File("/tmp/srtm");
    // possible alternatives see #451
    // http://mirror.ufs.ac.za/datasets/SRTM3/
    //"http://dds.cr.usgs.gov/srtm/version2_1/SRTM3/"
    private String baseUrl = "https://srtm.kurviger.de/SRTM3/";
    private boolean calcMean = false;

    public SRTMProvider() {
        // move to explicit calls?
        init();
    }

    public static void main(String[] args) throws IOException {
        SRTMProvider provider = new SRTMProvider();
        // 1046
        System.out.println(provider.getEle(47.468668, 14.575127));
        // 1113
        System.out.println(provider.getEle(47.467753, 14.573911));

        // 1946
        System.out.println(provider.getEle(46.468835, 12.578777));

        // 845
        System.out.println(provider.getEle(48.469123, 9.576393));

        // 1113 vs new:
        provider.setCalcMean(true);
        System.out.println(provider.getEle(47.467753, 14.573911));
    }

    @Override
    public void setCalcMean(boolean calcMean) {
        this.calcMean = calcMean;
    }

    /**
     * The URLs are a bit ugly and so we need to find out which area name a certain lat,lon
     * coordinate has.
     */
    private SRTMProvider init() {
        try {
            String strs[] = {"Africa", "Australia", "Eurasia", "Islands", "North_America", "South_America"};
            for (String str : strs) {
                InputStream is = getClass().getResourceAsStream(str + "_names.txt");
                for (String line : Helper.readFile(new InputStreamReader(is, Helper.UTF_CS))) {
                    int lat = Integer.parseInt(line.substring(1, 3));
                    if (line.substring(0, 1).charAt(0) == 'S')
                        lat = -lat;

                    int lon = Integer.parseInt(line.substring(4, 7));
                    if (line.substring(3, 4).charAt(0) == 'W')
                        lon = -lon;

                    int intKey = calcIntKey(lat, lon);
                    String key = areas.put(intKey, str);
                    if (key != null)
                        throw new IllegalStateException("do not overwrite existing! key " + intKey + " " + key + " vs. " + str);
                }
            }
            return this;
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot load area names from classpath", ex);
        }
    }

    // use int key instead of string for lower memory usage
    private int calcIntKey(double lat, double lon) {
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

    String getFileString(double lat, double lon) {
        int intKey = calcIntKey(lat, lon);
        String str = areas.get(intKey);
        if (str == null)
            return null;

        int minLat = Math.abs(down(lat));
        int minLon = Math.abs(down(lon));
        str += "/";
        if (lat >= 0)
            str += "N";
        else
            str += "S";

        if (minLat < 10)
            str += "0";
        str += minLat;

        if (lon >= 0)
            str += "E";
        else
            str += "W";

        if (minLon < 10)
            str += "0";
        if (minLon < 100)
            str += "0";
        str += minLon;
        return str;
    }

    @Override
    public double getEle(double lat, double lon) {
        lat = (int) (lat * precision) / precision;
        lon = (int) (lon * precision) / precision;
        int intKey = calcIntKey(lat, lon);
        HeightTile demProvider = cacheData.get(intKey);
        if (demProvider != null)
            return demProvider.getHeight(lat, lon);

        if (!cacheDir.exists())
            cacheDir.mkdirs();

        String fileDetails = getFileString(lat, lon);
        if (fileDetails == null)
            return 0;

        DataAccess heights = getDirectory().find("dem" + intKey);
        boolean loadExisting = false;
        try {
            loadExisting = heights.loadExisting();
        } catch (Exception ex) {
            logger.warn("cannot load dem" + intKey + ", error:" + ex.getMessage());
        }

        if (!loadExisting)
            updateHeightsFromZipFile(fileDetails, heights);

        int width = (int) (Math.sqrt(heights.getHeader(WIDTH_BYTE_INDEX)) + 0.5);
        if (width == 0)
            width = DEFAULT_WIDTH;

        demProvider = new HeightTile(down(lat), down(lon), width, precision, 1);
        cacheData.put(intKey, demProvider);
        demProvider.setCalcMean(calcMean);
        demProvider.setHeights(heights);
        return demProvider.getHeight(lat, lon);
    }

    private void updateHeightsFromZipFile(String fileDetails, DataAccess heights) throws RuntimeException {
        try {
            byte[] bytes = getByteArrayFromZipFile(fileDetails);
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

    private byte[] getByteArrayFromZipFile(String fileDetails) throws InterruptedException, FileNotFoundException, IOException {
        String zippedURL = baseUrl + "/" + fileDetails + ".hgt.zip";
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

        is = new FileInputStream(file);
        ZipInputStream zis = new ZipInputStream(is);
        zis.getNextEntry();
        BufferedInputStream buff = new BufferedInputStream(zis);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        byte[] buffer = new byte[0xFFFF];
        int len;
        while ((len = buff.read(buffer)) > 0) {
            os.write(buffer, 0, len);
        }
        os.flush();
        Helper.close(buff);
        return os.toByteArray();
    }

    @Override
    public void release() {
        cacheData.clear();

        // for memory mapped type we create temporary unpacked files which should be removed
        if (dir != null)
            dir.clear();
    }

    @Override
    public String toString() {
        return "SRTM";
    }

    private Directory getDirectory() {
        if (dir != null)
            return dir;

        logger.info(this.toString() + " Elevation Provider, from: " + baseUrl + ", to: " + cacheDir + ", as: " + daType);
        return dir = new GHDirectory(cacheDir.getAbsolutePath(), daType);
    }
}
