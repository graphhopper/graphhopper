package com.graphhopper.reader;

/**
 * Interface for 2D and 3D node id translation table
 * Author: Nop
 */
public interface OsmNodeTranslation
{
    int getIndex( long nodeId );

    void putIndex( long nodeId, int index );

    void optimize();

    long getSize();

    Object getMemoryUsage();

    int getElevation( long osmId );

    void copyNode( long nodeId, long id, int towerNode );
}
