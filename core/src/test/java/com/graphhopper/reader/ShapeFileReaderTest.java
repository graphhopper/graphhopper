package com.graphhopper.reader;

import com.graphhopper.GraphHopper;
import com.graphhopper.routing.util.*;
import com.graphhopper.storage.GraphExtension;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.storage.TurnCostExtension;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.Helper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

import static org.junit.Assert.assertEquals;

/**
 * Tests the ShapeFileReader with the normal helper initialized.
 * <p>
 * @author Vikas Veshishth
 */
public class ShapeFileReaderTest {

    private final String shapeFileDir = "test-osm-shapefile";

    private final String dir = "./target/tmp/test-db";
    private CarFlagEncoder carEncoder;

    @Before
    public void setUp()
    {
        new File(dir).mkdirs();
    }

    @After
    public void tearDown()
    {
        Helper.removeDir(new File(dir));
    }

    GraphHopperStorage newGraph( String directory, EncodingManager encodingManager, boolean is3D, boolean turnRestrictionsImport )
    {
        return new GraphHopperStorage(new RAMDirectory(directory, false), encodingManager, is3D,
                turnRestrictionsImport ? new TurnCostExtension() : new GraphExtension.NoOpExtension());
    }

    class GraphHopperTest extends GraphHopper
    {
        public GraphHopperTest( String shapeFileDir )
        {
            setStoreOnFlush(false);
            setShapeFileDir(shapeFileDir);
            setGraphHopperLocation(dir);
            setEncodingManager(new EncodingManager("CAR"));

            carEncoder = new CarFlagEncoder();

            setEncodingManager(new EncodingManager(carEncoder));
            setUseShapeFiles(true);
            setCHEnable(false);
        }

        @Override
        protected DataReader createReader( GraphHopperStorage tmpGraph )
        {
            return initShapeFileReader(new OSMShapeFileReader(tmpGraph));
        }

        @Override
        protected DataReader importData() throws IOException
        {
            GraphHopperStorage tmpGraph = newGraph(dir, getEncodingManager(), hasElevation(),
                    getEncodingManager().needsTurnCostsSupport());
            setGraphHopperStorage(tmpGraph);

            DataReader shapeFileReader = createReader(tmpGraph);
            ((ShapeFileReader) shapeFileReader).setShapeFileDir(getShapeFileDir());
            shapeFileReader.readGraph();
            return shapeFileReader;
        }
    }

    @Test
    public void testMain() throws URISyntaxException{
        String shapeFile = new File(getClass().getResource(shapeFileDir)
                .toURI()).getAbsolutePath();
        GraphHopper hopper = new GraphHopperTest(shapeFile).importOrLoad();
        GraphHopperStorage graph = hopper.getGraphHopperStorage();

        assertEquals(2917, graph.getNodes());
    }


}
