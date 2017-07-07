package com.graphhopper.routing.profiles;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.HintsMap;
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
        Weighting weighting = new FastestCarPropertyWeighting(encodingManager);
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

    class FastestCarPropertyWeighting implements Weighting {

        private static final double SPEED_CONV = 3.6;
        private String profile = "car";
        private final DoubleProperty maxSpeed;
        private final double maxSpeedValue;

        public FastestCarPropertyWeighting(EncodingManager2 em) {
            maxSpeed = em.getProperty("maxspeed", DoubleProperty.class);
            // TODO
            // maxSpeedValue = maxSpeed.getMaximum() / SPEED_CONV;
            maxSpeedValue = 100 / SPEED_CONV;
        }

        @Override
        public double getMinWeight(double distance) {
            return distance / maxSpeedValue;
        }

        @Override
        public double calcWeight(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
            double speed = edgeState.get(maxSpeed) * 0.9;
            if (speed == 0)
                return Double.POSITIVE_INFINITY;

            return edgeState.getDistance() / speed * SPEED_CONV;

//            // add direction penalties at start/stop/via points
//            boolean unfavoredEdge = edge.getBool(EdgeIteratorState.K_UNFAVORED_EDGE, false);
//            if (unfavoredEdge)
//                time += headingPenalty;
        }

        @Override
        public long calcMillis(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
            // TODO access
//            long flags = edgeState.getFlags();
//            if (reverse && !flagEncoder.isBackward(flags)
//                    || !reverse && !flagEncoder.isForward(flags))
//                throw new IllegalStateException("Calculating time should not require to read speed from edge in wrong direction. "
//                        + "Reverse:" + reverse + ", fwd:" + flagEncoder.isForward(flags) + ", bwd:" + flagEncoder.isBackward(flags));

            // TODO reverse
            // double speed = reverse ? flagEncoder.getReverseSpeed(flags) : flagEncoder.getSpeed(flags);
            double speed = edgeState.get(maxSpeed) * 0.9;
            if (Double.isInfinite(speed) || Double.isNaN(speed) || speed < 0)
                throw new IllegalStateException("Invalid speed stored in edge! " + speed);
            if (speed == 0)
                throw new IllegalStateException("Speed cannot be 0 for unblocked edge, use access properties to mark edge blocked! Should only occur for shortest path calculation. See #242.");

            return (long) (edgeState.getDistance() * 3600 / speed);
        }

        @Override
        public boolean matches(HintsMap reqMap) {
            return getName().equals(reqMap.getWeighting())
                    && profile.equals(reqMap.getVehicle());
        }

        @Override
        public FlagEncoder getFlagEncoder() {
            throw new IllegalArgumentException("Cannot access flag encoder for new encoding mechanism");
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 71 * hash + toString().hashCode();
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            final Weighting other = (Weighting) obj;
            return toString().equals(other.toString());
        }

        @Override
        public String getName() {
            return profile;
        }

        @Override
        public String toString() {
            return getName() + "|" + profile;
        }
    }
}