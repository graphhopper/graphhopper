package com.graphhopper.reader.osgb.hn;

import org.junit.Before;
import org.junit.Test;

import com.graphhopper.GraphHopper;
import com.graphhopper.routing.util.BikeFlagEncoder;
import com.graphhopper.routing.util.BusFlagEncoder;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.FootFlagEncoder;
import com.graphhopper.storage.GraphStorage;

public class OsHnReaderTest {

    protected EncodingManager encodingManager;

    @Before
    public void createEncodingManager() {
        FlagEncoder carEncoder = new CarFlagEncoder(5, 5, 3);
        FlagEncoder busEncoder = new BusFlagEncoder(5, 5, 3);
        // carEncoder = new RelationCarFlagEncoder(5, 5, 3);
        FlagEncoder bikeEncoder = new BikeFlagEncoder(4, 2, 3);
        FlagEncoder footEncoder = new FootFlagEncoder();
        encodingManager = new EncodingManager(footEncoder, carEncoder, bikeEncoder);
    }
    @Test
    public void testReader() {
        String graphLoc = "./target/output/hn-gh";
        String inputFile = "/data/Development/highways_network";
        GraphHopper graphHopper = new GraphHopper(){
            @Override
            protected void postProcessing()
            {
                System.out.println("DON'T DO postProcessing()");
            }
            @Override
            protected void flush()
            {
                //                fullyLoaded = true;
            }

        }.setInMemory().setOSMFile(inputFile).setGraphHopperLocation(graphLoc).setCHEnable(false).setEncodingManager(encodingManager).setAsHnReader();
        // THIS WILL FAIL FOR NOW UNTIL THE READER GENERATES SOME OSM NODES
        graphHopper.importOrLoad();
        GraphStorage graph = graphHopper.getGraph();

    }
}
