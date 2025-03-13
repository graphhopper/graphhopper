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

import java.io.*;
import java.util.zip.GZIPInputStream;

import static com.graphhopper.util.Helper.close;
import static com.graphhopper.util.Helper.toLowerCase;

/**
 * Skadi contains elevation data for the entire world with 1 arc second (~30m) accuracy in SRTM format stitched
 * together from many sources (https://github.com/tilezen/joerd/blob/master/docs/data-sources.md).
 *
 * We use the hosted AWS Open Data mirror (https://registry.opendata.aws/terrain-tiles/) by default but you can
 * change to any mirror by updating the base URL.
 *
 * See https://github.com/tilezen/joerd/blob/master/docs/attribution.md for required attribution of any project
 * using this data.
 *
 * Detailed information can be found here: https://github.com/tilezen/joerd
 */
public class SkadiProvider extends AbstractSRTMElevationProvider {
    public SkadiProvider() {
        this("");
    }
    public SkadiProvider(String cacheDir) {
        super(
                "https://elevation-tiles-prod.s3.amazonaws.com/skadi/",
                cacheDir.isEmpty()? "/tmp/srtm": cacheDir,
                "GraphHopper SRTMReader",
                -90,
                90,
                3601
        );
        downloader.requestCompressed(false);
    }

    public static void main(String[] args) throws IOException {
        SkadiProvider provider = new SkadiProvider();
        // 338
        System.out.println(provider.getEle(49.949784, 11.57517));
        // 468
        System.out.println(provider.getEle(49.968668, 11.575127));
        // 467
        System.out.println(provider.getEle(49.968682, 11.574842));
        // 3110
        System.out.println(provider.getEle(-22.532854, -65.110474));
        // 115
        System.out.println(provider.getEle(38.065392, -87.099609));
        // 1612
        System.out.println(provider.getEle(40, -105.2277023));
        System.out.println(provider.getEle(39.99999999, -105.2277023));
        System.out.println(provider.getEle(39.9999999, -105.2277023));
        System.out.println(provider.getEle(39.999999, -105.2277023));
        // 1015
        System.out.println(provider.getEle(47.468668, 14.575127));
        // 1107
        System.out.println(provider.getEle(47.467753, 14.573911));
        // 1930
        System.out.println(provider.getEle(46.468835, 12.578777));
        // 844
        System.out.println(provider.getEle(48.469123, 9.576393));
    }

    @Override
    byte[] readFile(File file) throws IOException {
        InputStream is = new FileInputStream(file);
        GZIPInputStream gzis = new GZIPInputStream(is, 8 * 1024);
        BufferedInputStream buff = new BufferedInputStream(gzis, 16 * 1024);
        ByteArrayOutputStream os = new ByteArrayOutputStream(64 * 1024);
        buff.transferTo(os);
        close(buff);
        return os.toByteArray();
    }

    private String getLatString(double lat) {
        int minLat = (int) Math.floor(lat);
        return (minLat < 0 ? "S" : "N") + getPaddedLatString(minLat);
    }

    private String getLonString(double lon) {
        int minLon = (int) Math.floor(lon);
        return (minLon < 0 ? "W" : "E") + getPaddedLonString(minLon);
    }

    String getFileName(double lat, double lon) {
        String latStr = getLatString(lat);
        String lonStr = getLonString(lon);
        return toLowerCase(latStr + lonStr);
    }

    String getDownloadURL(double lat, double lon) {
        String latStr = getLatString(lat);
        String lonStr = getLonString(lon);

        return latStr + "/" + latStr + lonStr + ".hgt.gz";
    }

    @Override
    public String toString() {
        return "skadi";
    }
}
