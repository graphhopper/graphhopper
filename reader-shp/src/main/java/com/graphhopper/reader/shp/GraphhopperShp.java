package com.graphhopper.reader.shp;

import com.graphhopper.GraphHopper;
import com.graphhopper.reader.DataReader;
import com.graphhopper.storage.GraphHopperStorage;

public class GraphhopperShp extends GraphHopper {
    @Override
    protected DataReader createReader(GraphHopperStorage ghStorage) {
        return initDataReader(new OSMShapeFileReader(ghStorage));
    }

}
