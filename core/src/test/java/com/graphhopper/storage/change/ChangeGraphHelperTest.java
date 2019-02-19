package com.graphhopper.storage.change;

import com.graphhopper.jackson.Jackson;
import com.graphhopper.json.geo.JsonFeatureCollection;
import com.graphhopper.routing.AbstractRoutingAlgorithmTester;
import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.Helper;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class ChangeGraphHelperTest {
    private EncodingManager encodingManager;
    private GraphHopperStorage graph;

    @Before
    public void setUp() {
        encodingManager = EncodingManager.create("car");
        graph = new GraphBuilder(encodingManager).create();
    }

    @Test
    public void testApplyChanges() throws IOException {
        // 0-1-2
        // | |
        // 3-4
        graph.edge(0, 1, 1, true);
        graph.edge(1, 2, 1, true);
        graph.edge(3, 4, 1, true);
        graph.edge(0, 3, 1, true);
        graph.edge(1, 4, 1, true);
        AbstractRoutingAlgorithmTester.updateDistancesFor(graph, 0, 0.01, 0.00);
        AbstractRoutingAlgorithmTester.updateDistancesFor(graph, 1, 0.01, 0.01);
        AbstractRoutingAlgorithmTester.updateDistancesFor(graph, 2, 0.01, 0.02);
        AbstractRoutingAlgorithmTester.updateDistancesFor(graph, 3, 0.00, 0.00);
        AbstractRoutingAlgorithmTester.updateDistancesFor(graph, 4, 0.00, 0.01);
        LocationIndex locationIndex = new LocationIndexTree(graph, new RAMDirectory()).prepareIndex();

        FlagEncoder encoder = encodingManager.getEncoder("car");
        DecimalEncodedValue avSpeedEnc = encoder.getAverageSpeedEnc();
        BooleanEncodedValue accessEnc = encoder.getAccessEnc();
        double defaultSpeed = GHUtility.getEdge(graph, 0, 1).get(avSpeedEnc);
        AllEdgesIterator iter = graph.getAllEdges();
        while (iter.next()) {
            assertEquals(defaultSpeed, iter.get(avSpeedEnc), .1);
            assertTrue(iter.get(accessEnc));
        }

        Reader reader = new InputStreamReader(getClass().getResourceAsStream("overlaydata1.json"), Helper.UTF_CS);
        ChangeGraphHelper instance = new ChangeGraphHelper(graph, locationIndex);
        JsonFeatureCollection collection = Jackson.newObjectMapper().readValue(reader, JsonFeatureCollection.class);
        long updates = instance.applyChanges(encodingManager, collection.getFeatures());
        assertEquals(2, updates);

        // assert changed speed and access
        double newSpeed = GHUtility.getEdge(graph, 0, 1).get(avSpeedEnc);
        assertEquals(10, newSpeed, .1);
        assertTrue(newSpeed < defaultSpeed);
        assertFalse(GHUtility.getEdge(graph, 3, 4).get(accessEnc));
    }
}
