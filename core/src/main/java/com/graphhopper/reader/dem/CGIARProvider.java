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

import com.graphhopper.util.Helper;
import org.apache.xmlgraphics.image.codec.tiff.TIFFDecodeParam;
import org.apache.xmlgraphics.image.codec.tiff.TIFFImageDecoder;
import org.apache.xmlgraphics.image.codec.util.SeekableStream;

import java.awt.image.Raster;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Elevation data from CGIAR project http://srtm.csi.cgiar.org/ 'PROCESSED SRTM DATA VERSION 4.1'.
 * Every file covers a region of 5x5 degree. License granted for all people using GraphHopper:
 * http://graphhopper.com/public/license/CGIAR.txt
 * <p>
 * Every zip contains readme.txt with the necessary information e.g.:
 * <ol>
 * <li>
 * All GeoTiffs with 6000 x 6000 pixels.
 * </li>
 * </ol>
 * <p>
 *
 * @author NopMap
 * @author Peter Karich
 */
public class CGIARProvider extends AbstractTiffElevationProvider {

    public CGIARProvider() {
        this("");
    }

    public CGIARProvider(String cacheDir) {
        // Alternative URLs for the CGIAR data can be found in #346
        super("https://srtm.csi.cgiar.org/wp-content/uploads/files/srtm_5x5/TIFF/",
                cacheDir.isEmpty() ? "/tmp/cgiar" : cacheDir,
                "GraphHopper CGIARReader",
                6000, 6000,
                5, 5);
    }

    public static void main(String[] args) {
        CGIARProvider provider = new CGIARProvider();

        System.out.println(provider.getEle(46, -20));

        // 337.0
        System.out.println(provider.getEle(49.949784, 11.57517));
        // 466.0
        System.out.println(provider.getEle(49.968668, 11.575127));
        // 455.0
        System.out.println(provider.getEle(49.968682, 11.574842));

        // 3134
        System.out.println(provider.getEle(-22.532854, -65.110474));

        // 120
        System.out.println(provider.getEle(38.065392, -87.099609));

        // 1615
        System.out.println(provider.getEle(40, -105.2277023));
        System.out.println(provider.getEle(39.99999999, -105.2277023));
        System.out.println(provider.getEle(39.9999999, -105.2277023));
        // 1616
        System.out.println(provider.getEle(39.999999, -105.2277023));

        // 0
        System.out.println(provider.getEle(29.840644, -42.890625));

        // 841
        System.out.println(provider.getEle(48.469123, 9.576393));
    }

    @Override
    Raster readFile(File file, String tifName) {
        SeekableStream ss = null;
        try {
            InputStream is = new FileInputStream(file);
            ZipInputStream zis = new ZipInputStream(is);
            // find tif file in zip
            ZipEntry entry = zis.getNextEntry();
            while (entry != null && !entry.getName().equals(tifName)) {
                entry = zis.getNextEntry();
            }

            ss = SeekableStream.wrapInputStream(zis, true);
            TIFFImageDecoder imageDecoder = new TIFFImageDecoder(ss, new TIFFDecodeParam());
            return imageDecoder.decodeAsRaster();
        } catch (Exception e) {
            throw new RuntimeException("Can't decode " + tifName, e);
        } finally {
            if (ss != null)
                Helper.close(ss);
        }
    }

    int down(double val) {
        // floor to nearest multiple of LAT_DEGREE
        return (int) (Math.floor(val / LAT_DEGREE)) * LAT_DEGREE;
    }

    @Override
    boolean isOutsideSupportedArea(double lat, double lon) {
        return lat >= 60 || lat <= -56;
    }

    protected String getFileName(double lat, double lon) {
        int minLat = down(lat);
        int minLon = down(lon);
        int lonInt = 1 + (minLon + 180) / LAT_DEGREE;
        int latInt = (60 - minLat) / LAT_DEGREE;

        // replace String.format as it seems to be slow
        // String.format("srtm_%02d_%02d", lonInt, latInt);
        String str = "srtm_";
        str += lonInt < 10 ? "0" : "";
        str += lonInt;
        str += latInt < 10 ? "_0" : "_";
        str += latInt;

        return str;
    }

    @Override
    int getMinLatForTile(double lat) {
        return down(lat);
    }

    @Override
    int getMinLonForTile(double lon) {
        return down(lon);
    }

    @Override
    String getDownloadURL(double lat, double lon) {
        return baseUrl + "/" + getFileName(lat, lon) + ".zip";
    }

    @Override
    String getFileNameOfLocalFile(double lat, double lon) {
        return getDownloadURL(lat, lon);
    }

    @Override
    public String toString() {
        return "cgiar";
    }
}
