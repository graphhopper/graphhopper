package com.graphhopper.routing.profiles;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.util.AbstractFlagEncoder;
import com.graphhopper.routing.weighting.FastestCarWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.EdgeIteratorState;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

public class EncodingManagerTest {

    private EncodingManager createEncodingManager() {
        // do not add surface property to test exception below
        TagsParserOSM parser = new TagsParserOSM();
        return new EncodingManager(parser, 4).
                add(TagParserFactory.Car.createAverageSpeed(new DecimalEncodedValue("averagespeed", 5, 0, 5, false))).
                add(TagParserFactory.Car.createMaxSpeed(new DecimalEncodedValue("maxspeed", 5, 120, 5, false))).
                add(TagParserFactory.Truck.createWeight(new DecimalEncodedValue("weight", 5, 5, 1, false))).
                add(TagParserFactory.createHighway(new StringEncodedValue("highway",
                        Arrays.asList("primary", "secondary", "tertiary"), "tertiary"))).
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
    public void testDirectionDependentBit() {
        final BitEncodedValue access = new BitEncodedValue("access", true);
        TagParser directionParser = TagParserFactory.Car.createAccess(access);
        TagsParserOSM parser = new TagsParserOSM();
        EncodingManager encodingManager = new EncodingManager(parser, 4).
                add(directionParser).
                init();

        GraphHopperStorage g = new GraphBuilder(encodingManager).create();
        EdgeIteratorState edge = g.edge(0, 1, 10, true);

        ReaderWay readerWay = new ReaderWay(0);
        readerWay.setTag("maxspeed", "30");
        readerWay.setTag("highway", "tertiary");
        readerWay.setTag("oneway", "yes");
        encodingManager.applyWayTags(readerWay, edge);

        assertTrue(edge.get(access));

        EdgeIteratorState reverseEdge = edge.detach(true);
        assertFalse(reverseEdge.get(access));
    }

    @Test
    public void testDirectionDependentDecimal() {
        final DecimalEncodedValue directed = new DecimalEncodedValue("directedspeed", 10, 0, 1, true);

        TagParser directedSpeedParser = new TagParser() {

            @Override
            public String getName() {
                return "directedspeed";
            }

            @Override
            public void parse(EdgeSetter setter, ReaderWay way, EdgeIteratorState edgeState) {
                final double speed = AbstractFlagEncoder.parseSpeed(way.getTag("maxspeed"));
                final double speedFW = AbstractFlagEncoder.parseSpeed(way.getTag("maxspeed:forward"));
                setter.set(edgeState, directed, speedFW > 0 ? speedFW : speed);
                // TODO NOW make this more efficient
                setter.set(edgeState.detach(true), directed, speed);
            }

            @Override
            public EncodedValue getEncodedValue() {
                return directed;
            }
        };

        TagsParserOSM parser = new TagsParserOSM();
        EncodingManager encodingManager = new EncodingManager(parser, 4).
                add(directedSpeedParser).
                init();

        GraphHopperStorage g = new GraphBuilder(encodingManager).create();
        EdgeIteratorState edge = g.edge(0, 1, 10, true);

        ReaderWay readerWay = new ReaderWay(0);
        readerWay.setTag("maxspeed", "30");
        readerWay.setTag("maxspeed:forward", "50");
        readerWay.setTag("highway", "tertiary");
        encodingManager.applyWayTags(readerWay, edge);

        assertEquals(50, edge.get(directed), .1);

        EdgeIteratorState reverseEdge = edge.detach(true);
        assertEquals(30, reverseEdge.get(directed), .1);

        assertEquals(50, edge.get(directed), .1);
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
    public void testNonOSMDataSet() {
        // TODO use completely different 'tagging' and still feed the same properties
        // or introduce a converter which allows us to intercept input and convert to OSM-alike tagging
    }
}