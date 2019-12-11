package com.graphhopper.reader.osm;

import com.graphhopper.reader.ReaderElement;
import com.graphhopper.reader.ReaderNode;
import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.reader.ReaderWay;

/**
 * Interface with some callback methods to called with each OSM object read from an OSM input file.
 *
 * @author Michael Reichert
 */
public interface OSMHandler {
    /**
     * Called for each node, way or relation read from the input file
     */
    public void osm_object(ReaderElement element);
    
    /**
     * Called for each node read from the input file
     */
    public void node(ReaderNode node);

    /**
     * Called for each way read from the input file
     */
    public void way(ReaderWay way);

    /**
     * Called for each relation read from the input file
     */
    public void relation(ReaderRelation relation);

    /**
     * Called for the file header of the input file
     */
    public void header(OSMFileHeader relation);
}