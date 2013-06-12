package com.graphhopper.reader;

import com.graphhopper.reader.dem.DemArea;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.util.shapes.BBox;

/**
 * Encapsulates the different data sources and classes that handle geometry.
 * Author: Nop
 */
public class GeometryAccess
{
    private OSMReader reader;
    private OSMReaderHelper helper;
    private DemArea dem;

    public GeometryAccess( OSMReader reader, OSMReaderHelper helper ) {
        this.reader = reader;
        this.helper = helper;
    }

    public void initDem( String demLocation, GraphStorage graphStorage ) {

        if( demLocation == null )
            throw new IllegalArgumentException( "Path to DEM data cannot be empty." );

        BBox bounds = graphStorage.bounds();
        dem = new DemArea( demLocation, 4, bounds.minLon, bounds.minLat, bounds.maxLon, bounds.maxLat  );
        dem.load();
    }

    public void getNode( long osmId, int[] node ) {
        helper.getNodeCoordinates( osmId, node );
    }


    public int getElevation( double lat, double lon ) {
        return dem.get( lat, lon );
    }
}
