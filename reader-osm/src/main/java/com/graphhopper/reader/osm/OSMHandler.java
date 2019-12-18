package com.graphhopper.reader.osm;

import com.graphhopper.reader.osm.OSMFileHeader;
import com.graphhopper.reader.ReaderElement;
import com.graphhopper.reader.ReaderNode;
import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.reader.ReaderWay;

/**
 * Abstract with some callback methods to called with each OSM object read from an OSM input file.
 *
 * @author Michael Reichert
 */
public abstract class OSMHandler {
    /**
     * Instance of OSMReader using this handler. This reference provides access to the public
     * methods of OSMReader to resolve OSM object IDs to node/edge IDs in the graph and vice versa. 
     */
    protected OSMReader reader;

    /**
     * Register the instance of OSMReader using this handler.
     *
     * You have implement this method if you want to access any public method of OSMReader, e.g.
     * the lookup from OSM node IDs to internal node IDs.
     *
     * @param reader the instance of OSMReader
     */
    public void registerOSMReader(OSMReader reader) {
        this.reader = reader;
    }

    /**
     * Called for each node, way or relation read from the input file
     */
    public void osmObject(ReaderElement element) {
    }

    /**
     * Called for each node read from the input file
     */
    public void node(ReaderNode node) {
    }

    /**
     * Called for each way read from the input file
     */
    public void way(ReaderWay way) {
    }

    /**
     * Called for each relation read from the input file
     */
    public void relation(ReaderRelation relation) {
    }

    /**
     * Called for the file header of the input file
     */
    public void header(OSMFileHeader relation) {
    }

    /**
     * Called for each tower node added to the graph.
     *
     * This callback is only called in the second reading of the OSM input file.
     *
     * @param osmID OSM object ID
     * @param lat latitude
     * @param lon longitude
     * @param ele elevation
     * @param id the ID of the tower node in the routing graph
     */
    public void towerNode(long osmId, double lat, double lon, double ele, int id) {
    }
}