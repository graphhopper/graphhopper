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

public class EncodingManagerTest {

    private EncodingManager createEncodingManager() {
        TagParser highway = TagParserFactory.createHighway();
        TagParser maxSpeed = TagParserFactory.Car.createMaxSpeed();
        TagParser averageSpeed = TagParserFactory.Car.createAverageSpeed();
        TagParser weight = TagParserFactory.Truck.createWeight();

        // do not add surface property to test exception below
        TagsParserOSM parser = new TagsParserOSM();
        return new EncodingManager(parser, 4).
                add(averageSpeed, new DecimalEncodedValue(averageSpeed.getName(), 5, 0, 5)).
                add(maxSpeed, new DecimalEncodedValue(maxSpeed.getName(), 5, 120, 5)).
                add(weight, new IntEncodedValue("weight", 5, 5)).
                add(highway, new StringEncodedValue(highway.getName(), Arrays.asList("primary", "secondary", "tertiary"), "tertiary")).
                init();
    }

    @Test
    public void importValues() {
        ReaderWay readerWay = new ReaderWay(0);
        readerWay.setTag("maxspeed", "30");
        readerWay.setTag("weight", "4");
        readerWay.setTag("highway", "tertiary");
        readerWay.setTag("surface", "mud");
        // interesting: we could move distance into a separate EncodedValue!!

        EncodingManager encodingManager = createEncodingManager();
        GraphHopperStorage g = new GraphBuilder(encodingManager).create();
        EdgeIteratorState edge = g.edge(0, 1, 10, true);

        encodingManager.applyWayTags(readerWay, edge);

        DecimalEncodedValue maxSpeed = encodingManager.getEncodedValue("maxspeed", DecimalEncodedValue.class);
        IntEncodedValue weight = encodingManager.getEncodedValue("weight", IntEncodedValue.class);
        StringEncodedValue highway = encodingManager.getEncodedValue("highway", StringEncodedValue.class);
        assertEquals(30d, edge.get(maxSpeed), 1d);
        assertEquals(4, edge.get(weight));
        assertEquals("tertiary", edge.get(highway));
        // access internal int representation - is this good or bad?
        assertEquals(2, edge.get((IntEncodedValue) highway));

        try {
            encodingManager.getEncodedValue("not_existing", IntEncodedValue.class);
            assertTrue(false);
        } catch (Exception ex) {
        }
    }

    @Test
    public void testDefaultValue() {
        ReaderWay readerWay = new ReaderWay(0);
        readerWay.setTag("highway", "tertiary");

        EncodingManager encodingManager = createEncodingManager();
        GraphHopperStorage g = new GraphBuilder(encodingManager).create();
        EdgeIteratorState edge = g.edge(0, 1, 10, true);

        encodingManager.applyWayTags(readerWay, edge);

        IntEncodedValue weight = encodingManager.getEncodedValue("weight", IntEncodedValue.class);
        assertEquals(5, edge.get(weight));
        DecimalEncodedValue speed = encodingManager.getEncodedValue("maxspeed", DecimalEncodedValue.class);
        assertEquals(120, edge.get(speed), .1);
    }

    @Test
    public void testValueBoundaryCheck() {
        ReaderWay readerWay = new ReaderWay(0);
        readerWay.setTag("maxspeed", "180");

        EncodingManager encodingManager = createEncodingManager();
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
        EncodingManager encodingManager = createEncodingManager();
        GraphHopperStorage g = new GraphBuilder(encodingManager).create();
        EdgeIteratorState edge = g.edge(0, 1, 10, true);
        StringEncodedValue surface = new StringEncodedValue("surface", Arrays.asList("mud", "something"), "something");
        try {
            edge.get(surface);
            assertTrue(false);
        } catch (AssertionError ex) {
        }
    }

    @Test
    public void testWeighting() {
        EncodingManager encodingManager = createEncodingManager();
        Weighting weighting = new FastestCarWeighting(encodingManager, "some_weighting");
        GraphHopperStorage g = new GraphBuilder(encodingManager).create();
        EdgeIteratorState edge = g.edge(0, 1, 10, true);

        DecimalEncodedValue maxSpeed = encodingManager.getEncodedValue("averagespeed", DecimalEncodedValue.class);
        edge.set(maxSpeed, 26d);

        assertEquals(10 / ((26 / 5 * 5) * 0.9) * 3600, weighting.calcMillis(edge, false, -1), 1);
    }

    @Test
    public void testMoreThan4Bytes() {
        // TODO
        // return new EncodingManager(parser, 8).init(Arrays.asList(maxSpeed, weight, highway));
    }

    @Test
    public void testSplittingAtVirtualEdges() {
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