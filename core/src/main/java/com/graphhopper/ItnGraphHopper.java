package com.graphhopper;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.graphhopper.reader.DataReader;
import com.graphhopper.reader.osgb.OsItnReader;
import com.graphhopper.storage.GraphStorage;

public class ItnGraphHopper extends GraphHopper {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Override
	protected DataReader createReader(GraphStorage tmpGraph) {
		return initOSMReader(new OsItnReader(tmpGraph));
	}

	protected OsItnReader initOSMReader(OsItnReader reader) {
		if (osmFile == null)
			throw new IllegalArgumentException("No OSM file specified");

		logger.info("start creating graph from " + osmFile);
		File osmTmpFile = new File(osmFile);
		return reader.setOSMFile(osmTmpFile).setElevationProvider(eleProvider)
				.setWorkerThreads(workerThreads)
				.setEncodingManager(encodingManager)
				.setWayPointMaxDistance(wayPointMaxDistance);
	}

}
