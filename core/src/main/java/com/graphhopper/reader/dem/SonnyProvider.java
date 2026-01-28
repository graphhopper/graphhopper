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

import com.graphhopper.util.Downloader;

import static com.graphhopper.util.Helper.close;

/**
 * Sonny's LiDAR Digital Terrain Models contains elevation data for Europe with 1 arc second (~30m) accuracy.
 * The description is available at <a href="https://sonny.4lima.de/">https://sonny.4lima.de/</a>. Unfortunately the data is provided on a
 * <a href="https://drive.google.com/drive/folders/0BxphPoRgwhnoWkRoTFhMbTM3RDA?resourcekey=0-wRe5bWl96pwvQ9tAfI9cQg">Google Drive</a>
 * Therefore, the data is not available via a direct URL and you have to download it manually. After downloading,
 * the data has to be unzipped and placed in the cache directory. The cache directory is expected to contain DTM
 * data files with the naming convention like "N49E011.hgt" for the area around 49°N and 11°E.
 * <p>
 * Please note that the data cannot be used for public hosting or redistribution due to the terms of use of the data. See
 * @see <a href="https://github.com/graphhopper/graphhopper/issues/2823">https://github.com/graphhopper/graphhopper/issues/2823</a>
 * <p>
 *
 * @author ratrun
 */
public class SonnyProvider extends AbstractSRTMElevationProvider {

	private static final String SONNY_DOWNLOAD_URL = "https://drive.google.com/drive/folders/0BxphPoRgwhnoWkRoTFhMbTM3RDA?resourcekey=0-wRe5bWl96pwvQ9tAfI9cQg";

    public SonnyProvider() {
        this("");
    }

    public SonnyProvider(String cacheDir) {
        super(SONNY_DOWNLOAD_URL + "/", // This base URL cannot be used, as the data is not available via a direct URL
                cacheDir.isEmpty() ? "/tmp/sonny" : cacheDir,
                -56,
                90,
                3601
        );
		this.downloader = new Downloader() {
			@Override
			public void downloadFile(String url, File toFile) {
				throw new RuntimeException("Sonny elevation data cannot be downloaded automatically. " +
						"Please download the data manually from " + SONNY_DOWNLOAD_URL +
						", unzip it and place the .hgt files in the cache directory: " + getCacheDir());
			}
		};
    }

    public static void main(String[] args) throws IOException {
        SonnyProvider provider = new SonnyProvider();
        // 338
        System.out.println(provider.getEle(49.949784, 11.57517));
        // 462
        System.out.println(provider.getEle(49.968668, 11.575127));
        // 462
        System.out.println(provider.getEle(49.968682, 11.574842));
        // 982
        System.out.println(provider.getEle(47.468668, 14.575127));
        // 1094
        System.out.println(provider.getEle(47.467753, 14.573911));
        // 1925
        System.out.println(provider.getEle(46.468835, 12.578777));
        // 834
        System.out.println(provider.getEle(48.469123, 9.576393));
        // Out of area
        try {
            System.out.println(provider.getEle(37.5969196, 23.0706507));
        } catch (Exception e) {
            System.out.println("Error: Out of area! " + e.getMessage());
        }

    }

    @Override
    byte[] readFile(File file) throws IOException {
        InputStream is = new FileInputStream(file);
        BufferedInputStream buff = new BufferedInputStream(is);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        byte[] buffer = new byte[0xFFFF];
        int len;
        while ((len = buff.read(buffer)) > 0) {
            os.write(buffer, 0, len);
        }
        os.flush();
        close(buff);
        return os.toByteArray();
    }

    @Override
    String getFileName(double lat, double lon) {
        String str = "";

        int minLat = Math.abs(down(lat));
        int minLon = Math.abs(down(lon));

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
        return getFileName(lat, lon) + ".hgt";
    }

    @Override
    public String toString() {
        return "sonny";
    }

}
