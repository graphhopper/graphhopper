package com.graphhopper.routing.util;

import com.graphhopper.json.GHJson;
import com.graphhopper.json.GHJsonFactory;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.profiles.*;
import com.graphhopper.routing.weighting.FastestCarWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.StringReader;
import java.util.Arrays;
import java.util.Map;

import static org.junit.Assert.*;

public class EncodingManagerNewTest {

    final GHJson json = new GHJsonFactory().create();

    private EncodingManager encodingManager;
    private DecimalEncodedValue avSpeedEnc;
    private BooleanEncodedValue accessEnc;

    @Before
    public void setUp() {
        final Map<String, Double> speedMap = TagParserFactory.Car.createSpeedMap();
        ReaderWayFilter filter = new ReaderWayFilter() {
            @Override
            public boolean accept(ReaderWay way) {
                return speedMap.containsKey(way.getTag("highway"));
            }
        };
        avSpeedEnc = new DecimalEncodedValue(TagParserFactory.CAR_AVERAGE_SPEED, 5, 0, 5, true);
        accessEnc = new BooleanEncodedValue(TagParserFactory.CAR_ACCESS, true);
        DecimalEncodedValue maxWeightEnc = new DecimalEncodedValue(TagParserFactory.MAX_WEIGHT, 5, 0, 1, false);
        encodingManager = new EncodingManager.Builder(8).
                // do not add surface property to test exception below
                        addGlobalEncodedValues(false, true).
                        add(TagParserFactory.Car.createAverageSpeed(avSpeedEnc, speedMap)).
                        add(TagParserFactory.Car.createAccess(accessEnc, filter)).
                        add(TagParserFactory.createMaxWeight(maxWeightEnc, filter)).
                        build();
    }

    @Test
    public void importValues() {
        ReaderWay readerWay = new ReaderWay(0);
        readerWay.setTag("maxspeed", "30");
        readerWay.setTag("maxweight", "4");
        readerWay.setTag("highway", "tertiary");
        readerWay.setTag("surface", "mud");

        IntsRef ints = encodingManager.handleWayTags(encodingManager.createIntsRef(), readerWay, new EncodingManager.AcceptWay(), 0);

        DecimalEncodedValue maxSpeed = encodingManager.getEncodedValue(TagParserFactory.CAR_MAX_SPEED, DecimalEncodedValue.class);
        IntEncodedValue weight = encodingManager.getEncodedValue(TagParserFactory.MAX_WEIGHT, IntEncodedValue.class);
        StringEncodedValue highway = encodingManager.getEncodedValue(TagParserFactory.ROAD_CLASS, StringEncodedValue.class);
        assertEquals(30d, maxSpeed.getDecimal(false, ints), .1);
        assertEquals(4, weight.getInt(false, ints));
        assertEquals("tertiary", highway.getString(false, ints));
        // access internal int representation - is this good or bad?
        assertEquals(highway.indexOf("tertiary"), ((IntEncodedValue) highway).getInt(false, ints));

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

        GraphHopperStorage g = new GraphBuilder(encodingManager).create();
        EdgeIteratorState edge = GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 0, 1, true, 10);

        encodingManager.applyWayTags(readerWay, edge);

        IntEncodedValue weight = encodingManager.getEncodedValue(TagParserFactory.MAX_WEIGHT, IntEncodedValue.class);
        assertEquals(0, edge.get(weight));
    }

    // TODO currently we do not throw an exception in TagsParserOSM.parse
    @Ignore
    @Test
    public void testValueBoundaryCheck() {
        ReaderWay readerWay = new ReaderWay(0);
        readerWay.setTag("maxspeed", "180");

        GraphHopperStorage g = new GraphBuilder(encodingManager).create();
        EdgeIteratorState edge = GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 0, 1, true, 10);

        try {
            encodingManager.applyWayTags(readerWay, edge);
            assertTrue(false);
        } catch (Exception ex) {
        }
    }

    @Test
    public void testNotInitializedProperty() {
        GraphHopperStorage g = new GraphBuilder(encodingManager).create();
        EdgeIteratorState edge = GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 0, 1, true, 10);
        StringEncodedValue surface = new StringEncodedValue("surface", Arrays.asList("mud", "something"), "something");
        try {
            edge.get(surface);
            assertTrue(false);
        } catch (AssertionError ex) {
        }
    }

    @Test
    public void testWeighting() {
        Weighting weighting = new FastestCarWeighting(encodingManager, "some_weighting");
        GraphHopperStorage g = new GraphBuilder(encodingManager).create();
        EdgeIteratorState edge = GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 0, 1, true, 10);
        edge.set(avSpeedEnc, 26d);
        edge.set(accessEnc, true);
        assertEquals(10 / ((26 / 5 * 5) * 0.9) * 3600, weighting.calcMillis(edge, false, -1), 1);
    }

    @Test
    public void testDirectionDependentBit() {
        GraphHopperStorage g = new GraphBuilder(encodingManager).create();
        EdgeIteratorState edge = g.edge(0, 1);

        ReaderWay readerWay = new ReaderWay(0);
        readerWay.setTag("maxspeed", "30");
        readerWay.setTag("highway", "tertiary");
        readerWay.setTag("oneway", "yes");
        IntsRef ints = encodingManager.handleWayTags(encodingManager.createIntsRef(), readerWay, new EncodingManager.AcceptWay(), 0);
        edge.setData(ints);

        BooleanEncodedValue access = encodingManager.getEncodedValue(TagParserFactory.CAR_ACCESS, BooleanEncodedValue.class);
        assertTrue(edge.get(access));
        assertFalse(edge.getReverse(access));
        assertFalse(edge.detach(true).get(access));

        // add new edge and apply its associated OSM tags
        EdgeIteratorState edge2 = g.edge(0, 2);
        readerWay = new ReaderWay(2);
        readerWay.setTag("highway", "primary");
        ints = encodingManager.handleWayTags(encodingManager.createIntsRef(), readerWay, new EncodingManager.AcceptWay(), 0);
        edge2.setData(ints);

        // assert that if the properties are cached that it is properly done
        EdgeExplorer explorer = g.createEdgeExplorer();
        EdgeIterator iter = explorer.setBaseNode(0);
        assertTrue(iter.next());
        assertEquals(2, iter.getAdjNode());
        assertTrue(iter.get(access));
        assertTrue(iter.detach(true).get(access));
        assertTrue(iter.getReverse(access));

        assertTrue(iter.next());
        assertEquals(1, iter.getAdjNode());
        assertTrue(iter.get(access));
        assertFalse(iter.detach(true).get(access));
        assertFalse(iter.getReverse(access));

        assertFalse(iter.next());
    }

    @Test
    public void testDirectionDependentDecimal() {
        final DecimalEncodedValue directedEnc = new DecimalEncodedValue("directed_speed", 10, 0, 1, true);

        TagParser directedSpeedParser = new TagParser() {

            @Override
            public String toString() {
                return getName();
            }

            @Override
            public String getName() {
                return directedEnc.getName();
            }

            @Override
            public void parse(IntsRef ints, ReaderWay way) {
                final double speed = AbstractFlagEncoder.parseSpeed(way.getTag("maxspeed"));
                final double speedFW = AbstractFlagEncoder.parseSpeed(way.getTag("maxspeed:forward"));
                directedEnc.setDecimal(false, ints, speedFW > 0 ? speedFW : speed);
                directedEnc.setDecimal(true, ints, speed);
            }

            @Override
            public EncodedValue getEncodedValue() {
                return directedEnc;
            }

            @Override
            public ReaderWayFilter getReadWayFilter() {
                return TagParserFactory.ACCEPT_IF_HIGHWAY;
            }
        };

        final Map<String, Double> speedMap = TagParserFactory.Car.createSpeedMap();
        ReaderWayFilter filter = new ReaderWayFilter() {
            @Override
            public boolean accept(ReaderWay way) {
                return speedMap.containsKey(way.getTag("highway"));
            }
        };

        EncodingManager encodingManager = new EncodingManager.Builder(4).
                add(TagParserFactory.Car.createAccess(new BooleanEncodedValue(TagParserFactory.CAR_ACCESS, true), filter)).
                add(directedSpeedParser).
                build();

        GraphHopperStorage g = new GraphBuilder(encodingManager).create();
        EdgeIteratorState edge = GHUtility.createEdge(g,
                encodingManager.getDecimalEncodedValue("directed_speed"), 60,
                encodingManager.getBooleanEncodedValue(TagParserFactory.CAR_ACCESS), 0, 1, true, 10);

        ReaderWay readerWay = new ReaderWay(0);
        readerWay.setTag("maxspeed", "30");
        readerWay.setTag("maxspeed:forward", "50");
        readerWay.setTag("highway", "tertiary");
        edge.setData(encodingManager.handleWayTags(encodingManager.createIntsRef(), readerWay, new EncodingManager.AcceptWay(), 0));

        assertEquals(30, edge.getReverse(directedEnc), .1);
        assertEquals(50, edge.get(directedEnc), .1);

        EdgeIteratorState reverseEdge = edge.detach(true);
        assertEquals(30, reverseEdge.get(directedEnc), .1);
        assertEquals(50, edge.get(directedEnc), .1);
    }


    @Test
    public void deserializationWithoutFlagEncoders() {
        final Map<String, Double> speedMap = TagParserFactory.Car.createSpeedMap();
        ReaderWayFilter filter = new ReaderWayFilter() {
            @Override
            public boolean accept(ReaderWay way) {
                return speedMap.containsKey(way.getTag("highway"));
            }
        };

        EncodingManager encodingManager = new EncodingManager.Builder(4).
                addGlobalEncodedValues().
                add(TagParserFactory.Car.createAccess(new BooleanEncodedValue(TagParserFactory.CAR_ACCESS, true), filter)).
                build();

        String jsonStr = json.toJson(encodingManager);
        // System.out.println(jsonStr);
        EncodingManager newEM = json.fromJson(new StringReader(jsonStr), EncodingManager.class);
        BooleanEncodedValue expected = encodingManager.getBooleanEncodedValue(TagParserFactory.ROUNDABOUT);
        BooleanEncodedValue newEncodedValue = newEM.getBooleanEncodedValue(TagParserFactory.ROUNDABOUT);
        assertTrue(expected != newEncodedValue);
        assertEquals(expected.toString(), newEncodedValue.toString());
        assertEquals(expected, newEncodedValue);
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