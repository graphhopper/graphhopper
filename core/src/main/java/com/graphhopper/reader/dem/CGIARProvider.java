/*
 *  Licensed to Peter Karich under one or more contributor license
 *  agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  Peter Karich licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the
 *  License at
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
import com.graphhopper.util.Helper;
import java.awt.image.Raster;
import java.io.*;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.xmlgraphics.image.codec.tiff.TIFFDecodeParam;
import org.apache.xmlgraphics.image.codec.tiff.TIFFImageDecoder;
import org.apache.xmlgraphics.image.codec.util.SeekableStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * @author NopMap
 * @author Peter Karich
 */
public class CGIARProvider implements ElevationProvider
{
    private static final int WIDTH = 6000;
    private Downloader downloader = new Downloader("GraphHopper CGIARReader").setTimeout(10000);
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Map<String, HeightTile> cacheData = new HashMap<String, HeightTile>();
    private File cacheDir = new File("/tmp/cgiar");
    // String baseUrl = "http://srtm.csi.cgiar.org/SRT-ZIP/SRTM_V41/SRTM_Data_GeoTiff";
    private String baseUrl = "http://droppr.org/srtm/v4.1/6_5x5_TIFs";
    private Directory dir;
    private DAType daType = DAType.MMAP;
    final double precision = 1e7;
    private final double invPrecision = 1 / precision;
    private final int degree = 5;

    public void setDownloader( Downloader downloader )
    {
        this.downloader = downloader;
    }

    @Override
    public ElevationProvider setCacheDir( File cacheDir )
    {
        if (cacheDir.exists() && !cacheDir.isDirectory())
            throw new IllegalArgumentException("Cache path has to be a directory");

        this.cacheDir = cacheDir;
        return this;
    }

    @Override
    public ElevationProvider setBaseURL( String baseUrl )
    {
        if (baseUrl == null || baseUrl.isEmpty())
            throw new IllegalArgumentException("baseUrl cannot be empty");

        this.baseUrl = baseUrl;
        return this;
    }

    @Override
    public ElevationProvider setDAType( DAType daType )
    {
        this.daType = daType;
        return this;
    }

    @Override
    public double getEle( double lat, double lon )
    {
        // no data we can avoid the trouble
        if (lat > 60 || lat < -60)
            return 0;

        lat = (int) (lat * precision) / precision;
        lon = (int) (lon * precision) / precision;
        String name = getFileName(lat, lon);
        HeightTile demProvider = cacheData.get(name);
        if (demProvider == null)
        {
            if (!cacheDir.exists())
                cacheDir.mkdirs();

            int minLat = down(lat);
            int minLon = down(lon);
            // less restrictive against boundary checking
            demProvider = new HeightTile(minLat, minLon, WIDTH, degree * precision, degree);
            cacheData.put(name, demProvider);
            DataAccess heights = getDirectory().find(name + ".gh");
            demProvider.setHeights(heights);
            boolean loadExisting = false;
            try
            {
                loadExisting = heights.loadExisting();
            } catch (Exception ex)
            {
                logger.warn("cannot load " + name + ", error:" + ex.getMessage());
            }

            if (!loadExisting)
            {
                // short == 2 bytes
                heights.create(2 * WIDTH * WIDTH);

                String tifName = name + ".tif";
                String zippedURL = baseUrl + "/" + name + ".zip";
                File file = new File(cacheDir, new File(zippedURL).getName());

                // get zip file if not already in cacheDir - unzip later and in-memory only!
                if (!file.exists())
                {
                    try
                    {
                        for (int i = 0; i < 3; i++)
                        {
                            try
                            {
                                downloader.downloadFile(zippedURL, file.getAbsolutePath());
                                break;
                            } catch (SocketTimeoutException ex)
                            {
                                // just try again after a little nap
                                Thread.sleep(2000);
                                continue;
                            } catch (IOException ex)
                            {
                                demProvider.setSeaLevel(true);
                                heights.flush();
                                return 0;
                            }
                        }
                    } catch (Exception ex)
                    {
                        throw new RuntimeException(ex);
                    }
                }

                // logger.info("start decoding");
                // decode tiff data
                Raster raster;
                SeekableStream ss = null;
                try
                {
                    InputStream is = new FileInputStream(file);
                    ZipInputStream zis = new ZipInputStream(is);
                    // find tif file in zip
                    ZipEntry entry = zis.getNextEntry();
                    while (entry != null && !entry.getName().equals(tifName))
                    {
                        entry = zis.getNextEntry();
                    }

                    ss = SeekableStream.wrapInputStream(zis, true);
                    TIFFImageDecoder imageDecoder = new TIFFImageDecoder(ss, new TIFFDecodeParam());
                    raster = imageDecoder.decodeAsRaster();
                } catch (Exception e)
                {
                    throw new RuntimeException("Can't decode " + tifName, e);
                } finally
                {
                    if (ss != null)
                        Helper.close(ss);
                }

                // logger.info("start converting to our format");
                // store in our own format, TODO use faster setBytes method?
                final int height = raster.getHeight();
                final int width = raster.getWidth();
                int x = 0, y = 0;
                try
                {
                    for (y = 0; y < height; y++)
                    {
                        for (x = 0; x < width; x++)
                        {
                            short val = (short) raster.getPixel(x, y, (int[]) null)[0];
                            if (val < -1000 || val > 10000)
                                val = Short.MIN_VALUE;

                            heights.setShort(2 * (y * WIDTH + x), val);
                        }
                    }
                    heights.flush();
                    // logger.info("end converting to our format");
                    // demProvider.toImage(name + ".png");

                    // TODO remove tifName and zip?
                } catch (Exception ex)
                {
                    throw new RuntimeException("Problem at x:" + x + ", y:" + y, ex);
                }
            } // loadExisting
        }

        if (demProvider.isSeaLevel())
            return 0;

        short val = demProvider.getHeight(lat, lon);
        if (val == Short.MIN_VALUE)
            return Double.NaN;
        return val;
    }

    int down( double val )
    {
        // 'rounding' to closest 5
        int intVal = (int) (val / degree) * degree;
        if (!(val >= 0 || intVal - val < invPrecision))
            intVal = intVal - degree;

        return intVal;
    }

    protected String getFileName( double lat, double lon )
    {
        lon = 1 + (180 + lon) / degree;
        int lonInt = (int) lon;
        lat = 1 + (60 - lat) / degree;
        int latInt = (int) lat;

        if (Math.abs(latInt - lat) < invPrecision / degree)
            latInt--;

        return String.format("srtm_%02d_%02d", lonInt, latInt);
    }

    @Override
    public void release()
    {
        cacheData.clear();

        // for memory mapped type we create temporary unpacked files which should be removed
        if (dir != null)
            dir.clear();
    }

    @Override
    public String toString()
    {
        return "CGIAR";
    }

    private Directory getDirectory()
    {
        if (dir != null)
            return dir;

        logger.info(this.toString() + " Elevation Provider, from: " + baseUrl + ", to: " + cacheDir + ", as: " + daType);
        return dir = new GHDirectory(cacheDir.getAbsolutePath(), daType);
    }

    public static void main( String[] args )
    {
        CGIARProvider provider = new CGIARProvider();
        // 340.0
        System.out.println(provider.getEle(49.949784, 11.57517));
        // 457.0
        System.out.println(provider.getEle(49.968668, 11.575127));

        // 3130
        System.out.println(provider.getEle(-22.532854, -65.110474));

        // 130                
        System.out.println(provider.getEle(38.065392, -87.099609));

        // 1125
        System.out.println(provider.getEle(40, -105.2277023));
        System.out.println(provider.getEle(39.99999999, -105.2277023));
        System.out.println(provider.getEle(39.9999999, -105.2277023));
        System.out.println(provider.getEle(39.999999, -105.2277023));
    }
}
