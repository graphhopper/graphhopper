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

import org.apache.xmlgraphics.image.codec.tiff.TIFFDecodeParam;
import org.apache.xmlgraphics.image.codec.tiff.TIFFImageDecoder;
import org.apache.xmlgraphics.image.codec.util.SeekableStream;

import java.awt.image.Raster;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import static com.graphhopper.core.util.Helper.*;

/**
 * Elevation data from Global Multi-resolution Terrain Elevation Data 2010 (GMTED2010).
 * The data provides 7.5 arc seconds resolution (~250 m) global elevation data. The data is available between latitudes
 * of 84°N and 70°S. The data is available as .tiff and the we are using the mean elevation per cell (other options are
 * median, min, max, etc.).
 * <p>
 * More information can be found here: https://topotools.cr.usgs.gov/gmted_viewer/
 * <p>
 * When using the data we have to acknowledge the source: "Data available from the U.S. Geological Survey.",
 * more information can be found here: https://lta.cr.usgs.gov/citation
 * <p>
 * The gdalinfo of one GeoTiff is:
 * Driver: GTiff/GeoTIFF
 * Files: 50N000E_20101117_gmted_mea075.tif
 * Size is 14400, 9600
 * Coordinate System is:
 * GEOGCS["WGS 84",
 * DATUM["WGS_1984",
 * SPHEROID["WGS 84",6378137,298.257223563,
 * AUTHORITY["EPSG","7030"]],
 * AUTHORITY["EPSG","6326"]],
 * PRIMEM["Greenwich",0],
 * UNIT["degree",0.0174532925199433],
 * AUTHORITY["EPSG","4326"]]
 * Origin = (-0.000138888888889,69.999861111111116)
 * Pixel Size = (0.002083333333333,-0.002083333333333)
 * Metadata:
 * AREA_OR_POINT=Area
 * Image Structure Metadata:
 * INTERLEAVE=BAND
 * Corner Coordinates:
 * Upper Left  (  -0.0001389,  69.9998611) (  0d 0' 0.50"W, 69d59'59.50"N)
 * Lower Left  (  -0.0001389,  49.9998611) (  0d 0' 0.50"W, 49d59'59.50"N)
 * Upper Right (  29.9998611,  69.9998611) ( 29d59'59.50"E, 69d59'59.50"N)
 * Lower Right (  29.9998611,  49.9998611) ( 29d59'59.50"E, 49d59'59.50"N)
 * Center      (  14.9998611,  59.9998611) ( 14d59'59.50"E, 59d59'59.50"N)
 * Band 1 Block=14400x1 Type=Int16, ColorInterp=Gray
 * Min=-209.000 Max=2437.000
 * Minimum=-209.000, Maximum=2437.000, Mean=149.447, StdDev=239.767
 * NoData Value=-32768
 * Metadata:
 * STATISTICS_EXCLUDEDVALUES=-32768
 * STATISTICS_MAXIMUM=2437
 * STATISTICS_MEAN=149.44718774595
 * STATISTICS_MINIMUM=-209
 * STATISTICS_STDDEV=239.767158482
 *
 * @author Robin Boldt
 */
public class GMTEDProvider extends AbstractTiffElevationProvider {
    // for alternatives see #346
    private final String FILE_NAME_END = "_20101117_gmted_mea075";

    public GMTEDProvider() {
        this("");
    }

    public GMTEDProvider(String cacheDir) {
        super("https://edcintl.cr.usgs.gov/downloads/sciweb1/shared/topo/downloads/GMTED/Global_tiles_GMTED/075darcsec/mea/",
                cacheDir.isEmpty() ? "/tmp/gmted" : cacheDir,
                "GraphHopper GMTEDReader",
                14400, 9600,
                20, 30);
    }

    public static void main(String[] args) {
        GMTEDProvider provider = new GMTEDProvider();

        System.out.println(provider.getEle(46, -20));

        // 337.0 (339)
        System.out.println(provider.getEle(49.949784, 11.57517));
        // 453.0 (438)
        System.out.println(provider.getEle(49.968668, 11.575127));
        // 447.0 (432)
        System.out.println(provider.getEle(49.968682, 11.574842));

        // 3131 (3169)
        System.out.println(provider.getEle(-22.532854, -65.110474));

        // 123 (124)
        System.out.println(provider.getEle(38.065392, -87.099609));

        // 1615 (1615)
        System.out.println(provider.getEle(40, -105.2277023));
        // (1618)
        System.out.println(provider.getEle(39.99999999, -105.2277023));
        System.out.println(provider.getEle(39.9999999, -105.2277023));
        // 1617 (1618)
        System.out.println(provider.getEle(39.999999, -105.2277023));

        // 1046 (1070)
        System.out.println(provider.getEle(47.468668, 14.575127));
        // 1113 (1115)
        System.out.println(provider.getEle(47.467753, 14.573911));

        // 1946 (1990)
        System.out.println(provider.getEle(46.468835, 12.578777));

        // 845 (841)
        System.out.println(provider.getEle(48.469123, 9.576393));

        // 1113 vs new: (1115)
        provider.setInterpolate(true);
        System.out.println(provider.getEle(47.467753, 14.573911));

        // 0
        System.out.println(provider.getEle(29.840644, -42.890625));
    }

    @Override
    Raster readFile(File file, String tifName) {
        SeekableStream ss = null;
        try {
            InputStream is = new FileInputStream(file);
            ss = SeekableStream.wrapInputStream(is, true);
            TIFFImageDecoder imageDecoder = new TIFFImageDecoder(ss, new TIFFDecodeParam());
            return imageDecoder.decodeAsRaster();
        } catch (Exception e) {
            throw new RuntimeException("Can't decode " + file.getName(), e);
        } finally {
            if (ss != null)
                close(ss);
        }
    }

    int getMinLatForTile(double lat) {
        return (int) (Math.floor((90 + lat) / LAT_DEGREE) * LAT_DEGREE) - 90;
    }

    int getMinLonForTile(double lon) {
        return (int) (Math.floor((180 + lon) / LON_DEGREE) * LON_DEGREE) - 180;
    }

    private String getLonString(int lonInt) {
        lonInt = Math.abs(lonInt);
        String lonString = lonInt < 100 ? "0" : "";
        if (lonInt < 10)
            lonString += "0";
        lonString += lonInt;
        return lonString;
    }

    private String getLatString(int latInt) {
        latInt = Math.abs(latInt);
        String latString = latInt < 10 ? "0" : "";
        latString += latInt;
        return latString;
    }

    @Override
    boolean isOutsideSupportedArea(double lat, double lon) {
        return lat > 84 || lat < -70;
    }

    String getFileName(double lat, double lon) {
        int lonInt = getMinLonForTile(lon);
        int latInt = getMinLatForTile(lat);
        return toLowerCase(getLatString(latInt) + getNorthString(latInt) + getLonString(lonInt) + getEastString(lonInt) + FILE_NAME_END);
    }

    String getDownloadURL(double lat, double lon) {
        int lonInt = getMinLonForTile(lon);
        int latInt = getMinLatForTile(lat);
        String east = getEastString(lonInt);
        String lonString = getLonString(lonInt);
        return baseUrl + "/" + east + lonString + "/" + getLatString(latInt) + getNorthString(latInt) + lonString + east + FILE_NAME_END + ".tif";
    }

    @Override
    String getFileNameOfLocalFile(double lat, double lon) {
        return getFileName(lat, lon) + ".tif";
    }

    private String getNorthString(int lat) {
        if (lat < 0) {
            return "S";
        }
        return "N";
    }

    private String getEastString(int lon) {
        if (lon < 0) {
            return "W";
        }
        return "E";
    }

    @Override
    public String toString() {
        return "gmted";
    }

}
