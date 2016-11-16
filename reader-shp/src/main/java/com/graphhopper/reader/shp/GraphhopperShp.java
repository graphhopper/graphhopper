package com.graphhopper.reader.shp;

import java.util.HashSet;

import com.graphhopper.GraphHopper;
import com.graphhopper.reader.DataReader;
import com.graphhopper.reader.shp.OSMShapeFileReader.EdgeAddedListener;
import com.graphhopper.storage.GraphHopperStorage;

public class GraphhopperShp extends GraphHopper {
	private final HashSet<EdgeAddedListener> edgeAddedListeners = new HashSet<>();

    @Override
    protected DataReader createReader(GraphHopperStorage ghStorage) {
    	OSMShapeFileReader reader= new OSMShapeFileReader(ghStorage);
    	for(EdgeAddedListener l:edgeAddedListeners){
    		reader.addListener(l);
    	}
        return initDataReader(reader);
    }
    

	public void addListener(EdgeAddedListener l){
		edgeAddedListeners.add(l);
	}

}
