package com.graphhopper.reader.gtfs;

import com.conveyal.gtfs.GTFSFeed;
import com.graphhopper.GraphHopper;
import com.graphhopper.reader.DataReader;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.Helper;

import java.io.File;

public class GraphHopperGtfs extends GraphHopper {

	GtfsStorage gtfsStorage = new GtfsStorage();

	@Override
	protected DataReader createReader(GraphHopperStorage ghStorage) {
		return initDataReader(new GtfsReader(ghStorage, gtfsStorage));
	}

	public GraphHopperGtfs setGtfsFile(String gtfs) {
		super.setDataReaderFile(gtfs);
		return this;
	}

	@Override
	public Weighting createWeighting(HintsMap weightingMap, FlagEncoder encoder) {
		return new PtTravelTimeWeighting(encoder, gtfsStorage);
	}
}
