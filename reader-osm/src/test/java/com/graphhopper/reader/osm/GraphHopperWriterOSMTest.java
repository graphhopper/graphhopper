package com.graphhopper.reader.osm;

import com.graphhopper.*;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.storage.CHProfile;
import com.graphhopper.util.Helper;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class GraphHopperWriterOSMTest {

    private static final String graphCache = "target/graphhopper-new";
    private static final String osmFile = "../core/files/monaco.osm.gz";

    @Before
    public void setUp() {
        Helper.removeDir(new File(graphCache));
    }

    @Test
    public void testQuery() {
        CarFlagEncoder encoder = new CarFlagEncoder();
        GraphConfig graphConfig = GraphConfig.start().graphCacheFolder(graphCache).build();
        GraphHopperWriter ghWriter = GraphHopperWriterOSM.create(EncodingManager.create(encoder), graphConfig).
                readData(ReadDataConfig.start().dataReaderFile(osmFile).build()).
                createLocationIndex();

        // Advantage: here we can do any post processing for the graph without the need to extend GraphHopperWriter
        // e.g. importPublicTransit() -> or would this be too inconvenient?
        ghWriter.getGraphHopperStorage();

        CHProfile chProfile = CHProfile.nodeBased(new FastestWeighting(encoder));
        ghWriter.doAsyncPreparation(chProfile, CHProfileConfig.start().build()).
                waitForAsyncPreparations();

        // one would expect a close of a writer but OSMReader closes automatically and the the other storages cannot be closed
        // ghWriter.close();

        GraphHopperReader reader = new GraphHopperReader(ghWriter);
        GHResponse rsp = reader.route(new GHRequest(43.738775, 7.423754, 43.736872, 7.419548));
        assertFalse(rsp.toString(), rsp.hasErrors());
        assertEquals(1375, rsp.getBest().getDistance(), 1);
        // we can also implement Closable to use try-with-resources, but not sure if this is misleading as GraphHopperReader should be reusable (?)
        reader.close();

        reader = GraphHopperReader.loadExisting(graphCache, graphConfig, Arrays.asList(chProfile));
        rsp = reader.route(new GHRequest(43.738775, 7.423754, 43.736872, 7.419548));
        assertFalse(rsp.toString(), rsp.hasErrors());
        assertEquals(1375, rsp.getBest().getDistance(), 1);
        reader.close();
    }

    @Test
    public void testMinimal() {
        CarFlagEncoder encoder = new CarFlagEncoder();
        GraphHopperWriter ghWriter = GraphHopperWriterOSM.create(EncodingManager.create(encoder), GraphConfig.start().graphCacheFolder(graphCache).build()).
                readData(ReadDataConfig.start().dataReaderFile(osmFile).build()).
                createLocationIndex().
                doAsyncPreparation(CHProfile.nodeBased(new FastestWeighting(encoder)), CHProfileConfig.start().build()).
                waitForAsyncPreparations();
        GraphHopperReader reader = new GraphHopperReader(ghWriter);
        GHResponse rsp = reader.route(new GHRequest(43.738775, 7.423754, 43.736872, 7.419548));
        assertFalse(rsp.toString(), rsp.hasErrors());
        assertEquals(1375, rsp.getBest().getDistance(), 1);
    }

    @Test
    public void oldGHClassMinimal() {
        CarFlagEncoder encoder = new CarFlagEncoder();
        GraphHopper graphHopper = new GraphHopperOSM().setOSMFile(osmFile).setGraphHopperLocation(graphCache).
                setEncodingManager(EncodingManager.create(encoder));
        graphHopper.importOrLoad();
        GHResponse rsp = graphHopper.route(new GHRequest(43.738775, 7.423754, 43.736872, 7.419548));
        assertFalse(rsp.toString(), rsp.hasErrors());
        assertEquals(1375, rsp.getBest().getDistance(), 1);
    }
}