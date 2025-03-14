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
import com.graphhopper.util.Helper;

import java.io.*;
import java.util.zip.ZipEntry;
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
public class SRTMProvider extends AbstractSRTMElevationProvider {
    private final GHIntObjectHashMap<String> areas = new GHIntObjectHashMap<>();

    public SRTMProvider() {
        this("");
    }

    public SRTMProvider(String cacheDir) {
        super(
                "https://srtm.kurviger.de/SRTM3/",
                cacheDir.isEmpty()? "/tmp/srtm": cacheDir,
                "GraphHopper SRTMReader",
                -56,
                60,
                1201
        );
        // move to explicit calls?
        init();
    }

    public static void main(String[] args) throws IOException {
        SRTMProvider provider = new SRTMProvider();
        // 337
        System.out.println(provider.getEle(49.949784, 11.57517));
        // 466
        System.out.println(provider.getEle(49.968668, 11.575127));
        // 466
        System.out.println(provider.getEle(49.968682, 11.574842));
        // 3100
        System.out.println(provider.getEle(-22.532854, -65.110474));
        // 122
        System.out.println(provider.getEle(38.065392, -87.099609));
        // 1617
        System.out.println(provider.getEle(40, -105.2277023));
        System.out.println(provider.getEle(39.99999999, -105.2277023));
        System.out.println(provider.getEle(39.9999999, -105.2277023));
        System.out.println(provider.getEle(39.999999, -105.2277023));
        // 1046
        System.out.println(provider.getEle(47.468668, 14.575127));
        // 1113
        System.out.println(provider.getEle(47.467753, 14.573911));
        // 1946
        System.out.println(provider.getEle(46.468835, 12.578777));
        // 845
        System.out.println(provider.getEle(48.469123, 9.576393));
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

    @Override
    public String toString() {
        return "srtm";
    }

    @Override
    byte[] readFile(File file) throws IOException {
        InputStream is = new FileInputStream(file);
        ZipInputStream zis = new ZipInputStream(is);
        ZipEntry entry = zis.getNextEntry();
        if (entry == null) {
            throw new RuntimeException("No entry found in zip file " + file);
        }
        ByteArrayOutputStream os = new ByteArrayOutputStream((int) entry.getSize());
        try (BufferedInputStream buff = new BufferedInputStream(zis)) {
            buff.transferTo(os);
        }
        return os.toByteArray();
    }

    @Override
    String getFileName(double lat, double lon) {
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
    String getDownloadURL(double lat, double lon) {
        return getFileName(lat, lon) + ".hgt.zip";
    }
}
