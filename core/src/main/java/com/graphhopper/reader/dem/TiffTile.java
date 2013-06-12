package com.graphhopper.reader.dem;

import com.sun.media.jai.codec.SeekableStream;
import com.sun.media.jai.codec.TIFFDecodeParam;
import com.sun.media.jai.codecimpl.TIFFImageDecoder;

import java.awt.image.Raster;
import java.io.*;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Tile for CIAT data.
 * Author: Nop
 */
public class TiffTile extends DemTile
{
    private Raster raster;
    private boolean seaLevel;

    public TiffTile(String baseDir, double lon, double lat)
    {
        super(baseDir, lon, lat, 6001);

        raster = null;
        seaLevel = false;
    }

    public boolean load()
    {
        // already present
        if (raster != null || seaLevel )
        {
            return false;
        }

        return loadData(tile);
    }

    @Override
    protected String getFileName()
    {
        int lonVal = (int) (lon / 5) + 37;
        int latVal = 12 - (int) (lat / 5);
        return String.format("srtm_%02d_%02d.tif", lonVal, latVal);
    }

    private static final float[] rasterSample = new float[1];

    public float get(int x, int y)
    {
        if( seaLevel )
            return 0;

        float[] sample = raster.getPixel(x, 5999-y, rasterSample);
        float ele = sample[0];
        if( ele < -1000 )
            ele = 0;
        return ele;
    }

    private boolean loadData(File tile)
    {
        // sea level substitute file for missing data
        if( tile.length() == 4 )
        {
            seaLevel = true;
            return false;
        }

        try
        {
            SeekableStream ss = SeekableStream.wrapInputStream( new FileInputStream( tile ), true);
            TIFFImageDecoder imageDecoder = new TIFFImageDecoder(ss, new TIFFDecodeParam());
            //RenderedImage ri = xtffImageDecoder.decodeAsRenderedImage();
            raster = imageDecoder.decodeAsRaster();
            //System.out.printf("Image: %d %d \n", raster.getWidth(), raster.getHeight());
            ss.close();
            return true;
        }
        catch (Exception e)
        {
            throw new RuntimeException( "loading " + tile.getPath(), e);
        }
    }


    public boolean download()
    {
        boolean ok = true;
//        String baseUrl = "http://srtm.csi.cgiar.org/SRT-ZIP/SRTM_V41/SRTM_Data_GeoTiff/";
        String baseUrl = "http://droppr.org/srtm/v4.1/6_5x5_TIFs/";
        // look for suitable file
        String urlString = baseUrl + getFileName().replace( ".tif", ".zip" );
        System.out.println("Downloading DEM data " + urlString);
        try
        {
            URL url = new URL(urlString);
            InputStream ips = new BufferedInputStream(url.openStream(), 100000);

            ZipInputStream in = new ZipInputStream(ips);
            // position on first entry
            ZipEntry entry = in.getNextEntry();
            while( entry != null && !entry.getName().equals( tile.getName() ))
                entry = in.getNextEntry();

            if( entry != null )
            {
                FileOutputStream out = new FileOutputStream( getFile() );
                int cnt = 2000;
                int n;
                byte[] buf = new byte[10000];
                while ((n = in.read(buf, 0, 10000)) > -1) {
                    out.write(buf, 0, n);
                    if( cnt-- == 0 ) {
                        System.out.print(".");
                        cnt = 2000;
                    }
                }
                out.close();
                in.closeEntry();
            }
            ips.close();
            System.out.println(" done");
        }
        catch ( FileNotFoundException e )
        {
            // missing file usually means sea area
            try
            {
                FileOutputStream out = new FileOutputStream( getFile() );
                out.write( "Meer".getBytes() );
                out.close();
                seaLevel = true;
            }
            catch (IOException ex)
            {
                throw new RuntimeException( "Writing sea level file " + getFile(), ex);
            }
        }
        catch (Exception e)
        {
            throw new RuntimeException( "downloading " + urlString, e);
        }

        return ok;
    }

    @Override
    public boolean isEmpty()
    {
        return raster == null && !seaLevel;
    }

}
