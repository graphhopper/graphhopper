package com.graphhopper.reader.dem;

import java.io.File;

/**
 * Describes an area of DEM data which is organized in Tiles.
 * Author: Nop
 */
public class DemArea
{
    double fromLon;
    double fromLat;
    double toLon;
    double toLat;

    double lonBase;
    double latBase;
    int xBase;
    int yBase;
    int xSize;
    int ySize;

    DemTile[][] tiles;

    int resolution;
    int tileCount;
    int tileRes;

    private String demLocation;

    public DemArea(String demLocation, int cacheSize, double fromLon, double fromLat, double toLon, double toLat)
    {
        this.fromLon = fromLon;
        this.fromLat = fromLat;
        this.toLon = toLon;
        this.toLat = toLat;

        this.demLocation = demLocation;
        File demDir = new File( demLocation );
        if( !demDir.exists() )
            demDir.mkdirs();

        resolution = 1200;
        tileCount = 5;

        tileRes = resolution * tileCount;

        lonBase = (int) Math.floor(fromLon / tileCount) * tileCount;
        latBase = (int) Math.floor(fromLat / tileCount) * tileCount;

        xBase = (int) (Math.abs(fromLon - lonBase) * resolution);
        yBase = (int) (Math.abs(fromLat - latBase) * resolution);
        xSize = (int) (Math.abs(toLon - fromLon) * resolution) + 1;
        ySize = (int) (Math.abs(toLat - fromLat) * resolution) + 1;
    }

    public boolean load()
    {
        int txMax = (xBase + xSize) / tileRes;
        int tyMax = (yBase + ySize) / tileRes;
        tiles = new DemTile[txMax + 1][tyMax + 1];
        for (int tx = 0; tx <= txMax; tx++)
        {
            for (int ty = 0; ty <= tyMax; ty++)
            {
                final TiffTile tile = new TiffTile( demLocation, lonBase + tx * tileCount, latBase + ty * tileCount );
                if( !tile.isPresent() )
                    tile.download();
                tiles[tx][ty] = tile;
            }
        }
        return true;
    }

    public int get( double lat, double lon )
    {
        final double lonRel = lon - lonBase;
        final double latRel = lat - latBase;
        int xIndex = (int) (lonRel / tileCount);
        int yIndex = (int) (latRel / tileCount);

        DemTile tile = tiles[xIndex][yIndex];
        if( tile.isEmpty() ) {
            // todo: add caching here if we find a reasonable usecase
            boolean needsCache = tile.load();
        }

        int xPos = (int) ((lonRel % tileCount) * resolution);
        int yPos = (int) ((latRel % tileCount) * resolution);
        return (int) tile.get( xPos, yPos );
    }
}
