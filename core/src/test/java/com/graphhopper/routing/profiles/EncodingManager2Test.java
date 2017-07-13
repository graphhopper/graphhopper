package com.graphhopper.routing.profiles;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.weighting.FastestCarWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.EdgeIteratorState;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EncodingManager2Test {

    private EncodingManager2 createEncodingManager() {
        Property maxSpeed = new DoubleProperty("maxspeed", 5, 0, 2);
        Property weight = new IntProperty("weight", 5, 5);
        StringProperty highway = new StringProperty("highway", Arrays.asList("primary", "secondary", "tertiary"), "tertiary");
        // TODO MappedDoubleProperty (0->0,1->5,2->20,...)
        // TODO BitProperty, IntPairProperty, DoublePairProperty


        // do not add surface property to test exception below
        PropertyParserOSM parser = new PropertyParserOSM();
        return new EncodingManager2(parser, 4).
                init(Arrays.asList(maxSpeed, weight, highway));
    }

    @Test
    public void importValues() {
        ReaderWay readerWay = new ReaderWay(0);
        readerWay.setTag("maxspeed", "30");
        readerWay.setTag("weight", "4");
        readerWay.setTag("highway", "tertiary");
        readerWay.setTag("surface", "mud");
        // interesting: we could move distance into a separate Property!!

        EncodingManager2 encodingManager = createEncodingManager();
        GraphHopperStorage g = new GraphBuilder(encodingManager).create();
        EdgeIteratorState edge = g.edge(0, 1, 10, true);

        encodingManager.applyWayTags(readerWay, edge);

        // TODO avoid cast somehow
        DoubleProperty maxSpeed = encodingManager.getProperty("maxspeed", DoubleProperty.class);
        IntProperty weight = encodingManager.getProperty("weight", IntProperty.class);
        StringProperty highway = encodingManager.getProperty("highway", StringProperty.class);
        assertEquals(30d, edge.get(maxSpeed), 1d);
        assertEquals(4, edge.get(weight));
        assertEquals("tertiary", edge.get(highway));
        // access internal int representation - is this good or bad?
        assertEquals(2, edge.get((IntProperty) highway));

        try {
            encodingManager.getProperty("not_existing", IntProperty.class);
            assertTrue(false);
        } catch (Exception ex) {
        }
    }

    @Test
    public void testDefaultValue() {
        ReaderWay readerWay = new ReaderWay(0);
        readerWay.setTag("highway", "tertiary");
        // interesting: we could move distance into a separate Property!!

        EncodingManager2 encodingManager = createEncodingManager();
        GraphHopperStorage g = new GraphBuilder(encodingManager).create();
        EdgeIteratorState edge = g.edge(0, 1, 10, true);

        encodingManager.applyWayTags(readerWay, edge);

        IntProperty weight = encodingManager.getProperty("weight", IntProperty.class);
        assertEquals(5, edge.get(weight));
    }

    @Test
    public void testMaxValueCheck() {
        ReaderWay readerWay = new ReaderWay(0);
        readerWay.setTag("maxspeed", "80");

        EncodingManager2 encodingManager = createEncodingManager();
        GraphHopperStorage g = new GraphBuilder(encodingManager).create();
        EdgeIteratorState edge = g.edge(0, 1, 10, true);

        try {
            encodingManager.applyWayTags(readerWay, edge);
            assertTrue(false);
        } catch (Exception ex) {
        }
    }

    @Test
    public void testNotInitializedProperty() {
        EncodingManager2 encodingManager = createEncodingManager();
        GraphHopperStorage g = new GraphBuilder(encodingManager).create();
        EdgeIteratorState edge = g.edge(0, 1, 10, true);
        StringProperty surface = new StringProperty("surface", Arrays.asList("mud", "something"), "something");
        try {
            edge.get(surface);
            assertTrue(false);
        } catch (AssertionError ex) {
        }
    }

    @Test
    public void testWeighting() {
        EncodingManager2 encodingManager = createEncodingManager();
        Weighting weighting = new FastestCarWeighting(encodingManager, "some_weighting");
        GraphHopperStorage g = new GraphBuilder(encodingManager).create();
        EdgeIteratorState edge = g.edge(0, 1, 10, true);

        DoubleProperty maxSpeed = encodingManager.getProperty("maxspeed", DoubleProperty.class);
        edge.set(maxSpeed, 26d);

        assertEquals(10 / (26 * 0.9) * 3600, weighting.calcMillis(edge, false, -1), 1);
    }

    @Test
    public void testMoreThan4Bytes() {
        // TODO
        // return new EncodingManager2(parser, 8).init(Arrays.asList(maxSpeed, weight, highway));
    }

    @Test
    public void testPropertySplittingAtVirtualEdges() {
        // TODO
    }

    @Test
    public void testReversePropertyPair() {
        // TODO we should add special pair support to make the case "two-weights per edges" easier, e.g. forward and backward maxspeed
    }

    @Test
    public void testNonOSMDataSet() {
        // TODO use completely different 'tagging' and still feed the same properties
        // or introduce a converter which allows us to intercept input and convert to OSM-alike tagging
    }
}