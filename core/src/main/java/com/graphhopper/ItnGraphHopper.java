package com.graphhopper;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.graphhopper.reader.DataReader;
import com.graphhopper.reader.osgb.OsItnReader;
import com.graphhopper.routing.util.RoutingAlgorithmSpecialAreaTests;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.util.CmdArgs;

public class ItnGraphHopper extends GraphHopper {
	
	 public static void main( String[] strs ) throws Exception
	    {
	        CmdArgs args = CmdArgs.read(strs);
	        GraphHopper hopper = new ItnGraphHopper().init(args);
	        hopper.importOrLoad();
	        if (args.getBool("graph.testIT", false))
	        {
	            // important: use osmreader.wayPointMaxDistance=0
	            RoutingAlgorithmSpecialAreaTests tests = new RoutingAlgorithmSpecialAreaTests(hopper);
	            tests.start();
	        }
	        hopper.close();
	    }

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Override
	protected DataReader createReader(GraphStorage tmpGraph) {
		return initOSMReader(new OsItnReader(tmpGraph));
	}

	protected OsItnReader initOSMReader(OsItnReader reader) {
		if (osmFile == null)
			throw new IllegalArgumentException("No ITN file specified");
		forDesktop();
		logger.info("start creating graph from " + osmFile);
		File osmTmpFile = new File(osmFile);
		return reader.setOSMFile(osmTmpFile).setElevationProvider(eleProvider)
				.setWorkerThreads(workerThreads)
				.setEncodingManager(encodingManager)
				.setWayPointMaxDistance(wayPointMaxDistance);
	}

}
