package com.graphhopper.routing.weighting;

import com.graphhopper.expression.GSAssignment;
import com.graphhopper.expression.GSParser;
import com.graphhopper.routing.profiles.*;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.GHUtility;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ScriptWeightingTest {

    private final DefaultEncodedValueFactory factory = new DefaultEncodedValueFactory();
    private CarFlagEncoder carFlagEncoder;
    private EnumEncodedValue<RoadClass> roadClassEnc;
    private DecimalEncodedValue maxWeightEnc;
    private EnumEncodedValue<RoadEnvironment> roadEnvEnc;

    @Before
    public void setUp() {
        carFlagEncoder = new CarFlagEncoder();
        maxWeightEnc = new UnsignedDecimalEncodedValue(MaxWeight.KEY, 8, 0.1, Double.POSITIVE_INFINITY, true);
        roadClassEnc = new EnumEncodedValue<>(RoadClass.KEY, RoadClass.class);
        roadEnvEnc = new EnumEncodedValue<>(RoadEnvironment.KEY, RoadEnvironment.class);
        new EncodingManager.Builder(4).add(roadClassEnc).add(maxWeightEnc).add(roadEnvEnc).add(carFlagEncoder).build();
    }

    @Test
    public void simpleWeighting() throws IOException {
        List<GSAssignment> assignments = GSParser.parse(new StringReader("base: 'car'\n" +
                "speed:\n" +
                "  road_class == 'motorway' ? 80\n" + // speed in km/h
                "  100")); // default speed

        ScriptWeighting weighting = new ScriptWeighting(carFlagEncoder, factory, assignments);
        IntsRef ints = createAccessibleIntsRef();
        roadClassEnc.setEnum(false, ints, RoadClass.MOTORWAY);
        assertEquals(45, weighting.calcWeight(GHUtility.createMockedEdgeIteratorState(1000, ints),
                false, -1) / 1e3, .1);

        roadClassEnc.setEnum(false, ints, RoadClass.PRIMARY);
        assertEquals(36, weighting.calcWeight(GHUtility.createMockedEdgeIteratorState(1000, ints),
                false, -1) / 1e3, .1);
    }

    @Test
    public void noSpeedDefault() throws IOException {
        List<GSAssignment> assignments = GSParser.parse(new StringReader("base: 'car'\n" +
                "speed:\n" +
                "  road_class == 'motorway' ? 80"));

        ScriptWeighting weighting = new ScriptWeighting(carFlagEncoder, factory, assignments);
        IntsRef ints = createAccessibleIntsRef();
        roadClassEnc.setEnum(false, ints, RoadClass.PRIMARY);
        try {
            weighting.calcWeight(GHUtility.createMockedEdgeIteratorState(1000, ints), false, -1);
            fail();
        } catch (Exception ex) {
            assertEquals("Script does not contain default value for 'speed'", ex.getMessage());
        }
    }

    @Test
    public void maxWeightWithReverseTrue() throws IOException {
        List<GSAssignment> assignments = GSParser.parse(new StringReader("base: 'car'\n" +
                "speed:\n" +
                "  max_weight < 7.5 ? 80\n" + // speed in km/h
                "  100")); // default speed

        ScriptWeighting weighting = new ScriptWeighting(carFlagEncoder, factory, assignments);

        IntsRef ints = createAccessibleIntsRef();
        maxWeightEnc.setDecimal(false, ints, 8);
        assertEquals(36, weighting.calcWeight(GHUtility.createMockedEdgeIteratorState(1000, ints),
                false, -1) / 1e3, .1);

        // max_weight on road is just 6
        maxWeightEnc.setDecimal(false, ints, 6);
        assertEquals(45, weighting.calcWeight(GHUtility.createMockedEdgeIteratorState(1000, ints),
                false, -1) / 1e3, .1);

        // fall back to default speed as reverse weight is infinity
        assertEquals(36, weighting.calcWeight(GHUtility.createMockedEdgeIteratorState(1000, ints),
                true, -1) / 1e3, .1);
    }

    @Test
    public void twoAssignments() throws IOException {
        List<GSAssignment> assignments = GSParser.parse(new StringReader("base: 'car'\n" +
                "speed:\n" +
                "  road_class == 'motorway' ? 80\n" +
                "  road_class == 'primary' ? 70\n" +
                "  road_environment == 'ferry' ? 10\n" +
                "  100\n" +
                "priority:\n" +
                "  road_class == 'service' ? 30 # try hard to avoid it\n" +
                "  1"));

        ScriptWeighting weighting = new ScriptWeighting(carFlagEncoder, factory, assignments);

        IntsRef ints = createAccessibleIntsRef();
        roadClassEnc.setEnum(false, ints, RoadClass.MOTORWAY);
        assertEquals(45, weighting.calcWeight(GHUtility.createMockedEdgeIteratorState(1000, ints),
                false, -1) / 1e3, .1);

        roadClassEnc.setEnum(false, ints, RoadClass.PRIMARY);
        assertEquals(51.43, weighting.calcWeight(GHUtility.createMockedEdgeIteratorState(1000, ints),
                false, -1) / 1e3, .1);

        ints = createAccessibleIntsRef();
        roadEnvEnc.setEnum(false, ints, RoadEnvironment.FERRY);
        assertEquals(360, weighting.calcWeight(GHUtility.createMockedEdgeIteratorState(1000, ints),
                false, -1) / 1e3, .1);

        ints = createAccessibleIntsRef();
        roadClassEnc.setEnum(false, ints, RoadClass.SERVICE);
        assertEquals(1080, weighting.calcWeight(GHUtility.createMockedEdgeIteratorState(1000, ints),
                false, -1) / 1e3, .1);

        assertEquals(Long.MAX_VALUE, Math.round(1000 / 0.0));
    }

    IntsRef createAccessibleIntsRef() {
        IntsRef ints = new IntsRef(1);
        carFlagEncoder.getAccessEnc().setBool(false, ints, true);
        carFlagEncoder.getAccessEnc().setBool(true, ints, true);
        return ints;
    }
}