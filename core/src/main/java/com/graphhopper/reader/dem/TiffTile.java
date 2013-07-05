package com.graphhopper.reader.dem;

import com.sun.media.jai.codec.SeekableStream;
import com.sun.media.jai.codec.TIFFDecodeParam;
import com.sun.media.jai.codecimpl.TIFFImageDecoder;

import java.awt.image.Raster;
import java.io.*;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.nio.channels.FileChannel;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Tile for CIAT data.
 * Author: Nop
 */
public class TiffTile extends DemTile
{
    private short[] elevations;
    private boolean seaLevel;

    public TiffTile( String baseDir, double lon, double lat ) {
        super( baseDir, lon, lat, 6000 );

        elevations = null;
        seaLevel = false;
    }

    public boolean load() {
        // already present
        if( elevations != null || seaLevel ) {
            return false;
        }

        return loadData();
    }

    @Override
    protected String getFileName() {
        int lonVal = (int) (lon / 5) + 37;
        int latVal = 12 - (int) (lat / 5);
        return String.format( "srtm_%02d_%02d.dem", lonVal, latVal );
    }

    public int get( int x, int y ) {
        if( seaLevel )
            return 0;

        return elevations[y * size + x];
    }

    private boolean loadData() {
        // sea level substitute file for missing data
        if( tile.length() == 4 ) {
            seaLevel = true;
            return false;
        }

        ByteBuffer bb = ByteBuffer.allocateDirect( size*size * 2 );
        ShortBuffer sb = bb.asShortBuffer();
        try {
            FileInputStream fis = new FileInputStream( getFile() );
            FileChannel ch = fis.getChannel();
            ch.read( bb );
            fis.close();
        }
        catch( Exception e ) {
            throw new RuntimeException( "Can't load DEM data file " + tile.getPath() );
        }

        elevations = new short[size*size];
        sb.get( elevations );
        return true;
    }


    public boolean download() {
        byte[] tifData = new byte[72100000];
        int dataCount = 0;

        // look for local file
        boolean localData = false;
        File localDem = new File( tile.getPath().replace( ".dem", ".tif" ) );
        if( localDem.exists() && localDem.length() > 72000000 ) {
            localData = true;
            System.out.println("Converting local DEM file " + localDem.getPath() );
            try {
                FileInputStream in = new FileInputStream( localDem );
                int n;
                while( (n = in.read( tifData, dataCount, tifData.length - dataCount )) > -1 ) {
                    dataCount += n;
                }
                in.close();
            }
            catch( Exception e ) {
                throw new RuntimeException( "loading local DEM TIF file " + localDem.getPath() );
            }
        }
        else {
            //        String baseUrl = "http://srtm.csi.cgiar.org/SRT-ZIP/SRTM_V41/SRTM_Data_GeoTiff/";
            String baseUrl = "http://droppr.org/srtm/v4.1/6_5x5_TIFs/";
            // look for suitable file
            String urlString = baseUrl + tile.getName().replace( ".dem", ".zip" );
            System.out.println( "Downloading DEM data " + urlString );
            try {
                // download and unzip into memory
                URL url = new URL( urlString );
                InputStream ips = new BufferedInputStream( url.openStream(), 50000 );

                ZipInputStream in = new ZipInputStream( ips );
                // position on first entry
                ZipEntry entry = in.getNextEntry();
                final String name = tile.getName().replace( ".dem", ".tif" );
                while( entry != null && !entry.getName().equals( name ) )
                    entry = in.getNextEntry();

                if( entry == null )
                    throw new EOFException( "File not found in ZIP: " + name );

                int n;
                int cnt = 2000000;
                while( (n = in.read( tifData, dataCount, tifData.length - dataCount )) > -1 ) {
                    dataCount += n;
                    cnt -= n;
                    if( cnt < 0 ) {
                        System.out.print( "." );
                        cnt = 2000000;
                    }
                }
                in.closeEntry();
                ips.close();

                System.out.println( " download done. Converting." );
            }
            catch( FileNotFoundException e ) {
                // missing file usually means sea area
                try {
                    FileOutputStream out = new FileOutputStream( getFile() );
                    out.write( "Meer".getBytes() );
                    out.close();
                    seaLevel = true;
                    return true;
                }
                catch( IOException ex ) {
                    throw new RuntimeException( "Can't write sea level file " + getFile(), ex );
                }
            }
            catch( Exception e ) {
                throw new RuntimeException( "Can't download " + urlString, e );
            }
        }

        // decode tiff data
        Raster raster;
        try {
            SeekableStream ss = SeekableStream.wrapInputStream( new ByteArrayInputStream( tifData ), true );
            TIFFImageDecoder imageDecoder = new TIFFImageDecoder( ss, new TIFFDecodeParam() );
            raster = imageDecoder.decodeAsRaster();
            ss.close();
        }
        catch( Exception e ) {
            throw new RuntimeException( "Can't decode TIF " + tile.getName(), e );
        }

        // store in our own format
        elevations = new short[size * size];
        final float[] rasterSample = new float[1];

        final int height = raster.getHeight();
        final int width = raster.getWidth();
        for( int x = 0; x < width; x++ ) {
            for( int y = 0; y < height; y++ ) {
                float[] sample = raster.getPixel( x, 5999 - y, rasterSample );
                float ele = sample[0];
                if( ele < -1000 )
                    ele = 0;

                elevations[y * size + x] = (short) ele;
            }
        }
        ByteBuffer bb = ByteBuffer.allocateDirect( elevations.length * 2 );
        ShortBuffer sb = bb.asShortBuffer();
        sb.put( elevations );
        try {
            FileOutputStream ops = new FileOutputStream( tile );
            FileChannel oc = ops.getChannel();
            oc.write( bb );
            ops.close();
        }
        catch( Exception e ) {
            throw new RuntimeException( "Can't write elevation data " + tile.getPath(), e );
        }

        // delete local DEM data after conversion
        if(localData)
            localDem.delete();

        return true;
    }

    @Override
    public boolean isEmpty() {
        return elevations == null && !seaLevel;
    }

}
