package com.graphhopper.reader.osm;

import com.graphhopper.GraphConfig;
import com.graphhopper.GraphHopperWriter;
import com.graphhopper.routing.util.EncodingManager;

public class GraphHopperWriterOSM {
    private GraphHopperWriterOSM() {
    }

    public static GraphHopperWriter create(EncodingManager encodingManager, GraphConfig graphConfig) {
        GraphHopperWriter writer = new GraphHopperWriter(encodingManager, graphConfig);
        OSMReader reader = new OSMReader(writer.getGraphHopperStorage());
        return writer.setDataReader(reader);
    }
}
