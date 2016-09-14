package com.graphhopper.reader.gtfs;

import com.conveyal.gtfs.GTFSFeed;
import com.graphhopper.GraphHopper;
import com.graphhopper.reader.DataReader;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.Helper;

import java.io.File;

public class GraphHopperGtfs extends GraphHopper {

	@Override
	protected DataReader createReader(GraphHopperStorage ghStorage) {
		return initDataReader(new GtfsReader(ghStorage));
	}

	public GraphHopperGtfs setGtfsFile(String gtfs) {
		super.setDataReaderFile(gtfs);
		return this;
	}

}
