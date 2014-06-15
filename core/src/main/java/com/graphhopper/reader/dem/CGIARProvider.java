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
import gnu.trove.map.hash.TIntObjectHashMap;
import java.awt.image.Raster;
import java.io.*;
import java.net.SocketTimeoutException;
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
    private final TIntObjectHashMap<HeightTile> cacheData = new TIntObjectHashMap<HeightTile>();
    private File cacheDir = new File("/tmp/cgiar");
    // String baseUrl = "http://srtm.csi.cgiar.org/SRT-ZIP/SRTM_V41/SRTM_Data_GeoTiff";
    private String baseUrl = "http://droppr.org/srtm/v4.1/6_5x5_TIFs";
    private Directory dir;
    private DAType daType = DAType.MMAP;
    private final double precision = 1e7;
    private final double invPrecision = 1 / precision;

    public void setDownloader( Downloader downloader )
    {
        this.downloader = downloader;
    }

    @Override
    public ElevationProvider setCacheDir( File cacheDir )
    {
        if (cacheDir.exists() && !cacheDir.isDirectory())
            throw new IllegalStateException("Cache path has to be a directory");

        this.cacheDir = cacheDir;
        return this;
    }

    @Override
    public ElevationProvider setBaseURL( String baseUrl )
    {
        if (baseUrl.isEmpty())
            return this;

        this.baseUrl = baseUrl;
        return this;
    }

    @Override
    public ElevationProvider setInMemory( boolean inMem )
    {
        if (inMem)
            daType = DAType.RAM;
        else
            daType = DAType.MMAP;
        return this;
    }

    // use int key instead of string for lower memory usage
    private int calcIntKey( double lat, double lon )
    {
        // we could use LinearKeyAlgo but this is simpler as we only need integer precision:
        return (down(lat, 5) + 90) * 1000 + down(lon, 5) + 180;
    }

    int down( double val, int scale )
    {
        int intVal = (int) val;
        if (val >= 0 || intVal - val < invPrecision)
            return intVal / scale;
        return (intVal - 1) / scale;
    }

    @Override
    public double getEle( double lat, double lon )
    {
        lat = (int) (lat * precision) / precision;
        lon = (int) (lon * precision) / precision;
        int intKey = calcIntKey(lat, lon);
        HeightTile demProvider = cacheData.get(intKey);
        if (demProvider == null)
        {
            if (!cacheDir.exists())
                cacheDir.mkdirs();

            int minLat = down(lat, 1);
            int minLon = down(lon, 1);
            demProvider = new HeightTile(minLat, minLon, WIDTH, precision);
            cacheData.put(intKey, demProvider);
            DataAccess heights = getDirectory().find("dem" + intKey);
            // short == 2 bytes
            heights.create(2 * WIDTH * WIDTH);
            demProvider.setHeights(heights);

            String name = getFileName(lat, lon);
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
                        } catch (FileNotFoundException ex)
                        {
                            // TODO is at seaLevel
                            continue;
                        }
                    }
                } catch (Exception ex)
                {
                    throw new RuntimeException(ex);
                }
            }

            logger.info("start decoding");

            // decode tiff data
            Raster raster;
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

                SeekableStream ss = SeekableStream.wrapInputStream(zis, true);
                TIFFImageDecoder imageDecoder = new TIFFImageDecoder(ss, new TIFFDecodeParam());
                raster = imageDecoder.decodeAsRaster();
                ss.close();
            } catch (Exception e)
            {
                throw new RuntimeException("Can't decode " + tifName, e);
            }

            logger.info("start converting to our format");
            // store in our own format, TODO use faster setBytes method?
            final int height = raster.getHeight();
            final int width = raster.getWidth();
            final float[] rasterSample = new float[1];
            int x = 0, y = 0;
            try
            {
                for (; x < width; x++)
                {
                    for (y = 0; y < height; y++)
                    {
                        float[] sample = raster.getPixel(x, y, rasterSample);
                        short val = (short) sample[0];

                        if (val < -1000 || val > 10000)
                            val = Short.MIN_VALUE;

                        heights.setShort(2 * (y * WIDTH + x), val);
                    }
                }
                logger.info("end converting to our format");
                // demProvider.toImage(name + ".png");
            } catch (Exception ex)
            {
                throw new RuntimeException("Problem at index " + x + ", " + y, ex);
            }
        }

        short val = demProvider.getHeight(lat, lon);
        if (val == Short.MIN_VALUE)
            return Double.NaN;
        return val;
    }

    protected String getFileName( double lat, double lon )
    {
        int lonVal = (int) (lon / 5) + 37;
        int latVal = 12 - (int) (lat / 5);
        return String.format("srtm_%02d_%02d", lonVal, latVal);
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
    }
}
