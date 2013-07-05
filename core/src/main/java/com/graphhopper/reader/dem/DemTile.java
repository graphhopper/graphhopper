package com.graphhopper.reader.dem;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;

/**
 * Base class for a DEM tile
 * Author: Nop
 */
public abstract class DemTile
{
    protected File tile;

    protected double lat;
    protected double lon;
    protected int size;


    public DemTile( String baseDir, double lon, double lat, int size )
    {
        this.lat = lat;
        this.lon = lon;
        this.size = size;

        tile = new File( baseDir, getFileName());
    }

    public boolean isPresent()
    {
        return tile.exists();
    }

    public int getSize()
    {
        return size;
    }

    File getFile()
    {
        return tile;
    }

    protected abstract String getFileName();

    public abstract boolean load();

    public abstract boolean isEmpty();

    public abstract int get( int x, int y );

}
