package com.graphhopper.routing.weighting;

import com.graphhopper.expression.GSAssignment;
import com.graphhopper.expression.GSParser;
import com.graphhopper.routing.profiles.*;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.GHUtility;
import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ScriptWeightingTest {

    @Test
    public void simpleWeighting() throws IOException {
        CarFlagEncoder carFlagEncoder = new CarFlagEncoder();
        EnumEncodedValue<RoadClass> roadClassEnc = new EnumEncodedValue<>(RoadClass.KEY, RoadClass.class);
        EncodingManager em = new EncodingManager.Builder(4).add(roadClassEnc).add(carFlagEncoder).build();
        List<GSAssignment> assignments = new GSParser().parse(new StringReader("base: 'car'\n" +
                "speed:\n" +
                "  road_class == 'motorway' ? 80\n" + // speed in km/h
                "  100")); // default speed

        ScriptWeighting weighting = new ScriptWeighting(carFlagEncoder, assignments);

        IntsRef ints = new IntsRef(1);
        roadClassEnc.setEnum(false, ints, RoadClass.MOTORWAY);
        assertEquals(45, weighting.calcWeight(GHUtility.createMockedEdgeIteratorState(1000, ints),
                false, -1), .1);

        roadClassEnc.setEnum(false, ints, RoadClass.PRIMARY);
        assertEquals(36, weighting.calcWeight(GHUtility.createMockedEdgeIteratorState(1000, ints),
                false, -1), .1);
    }

    @Test
    public void noSpeedDefault() throws IOException {
        CarFlagEncoder carFlagEncoder = new CarFlagEncoder();
        EnumEncodedValue<RoadClass> roadClassEnc = new EnumEncodedValue<>(RoadClass.KEY, RoadClass.class);
        EncodingManager em = new EncodingManager.Builder(4).add(roadClassEnc).add(carFlagEncoder).build();
        List<GSAssignment> assignments = new GSParser().parse(new StringReader("base: 'car'\n" +
                "speed:\n" +
                "  road_class == 'motorway' ? 80"));

        ScriptWeighting weighting = new ScriptWeighting(carFlagEncoder, assignments);
        IntsRef ints = new IntsRef(1);
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
        CarFlagEncoder carFlagEncoder = new CarFlagEncoder();
        DecimalEncodedValue maxWeightEnc = new UnsignedDecimalEncodedValue(MaxWeight.KEY, 8, 0.1, Double.POSITIVE_INFINITY, true);
        EncodingManager em = new EncodingManager.Builder(4).add(maxWeightEnc).add(carFlagEncoder).build();
        List<GSAssignment> assignments = new GSParser().parse(new StringReader("base: 'car'\n" +
                "speed:\n" +
                "  max_weight < 7.5 ? 80\n" + // speed in km/h
                "  100")); // default speed

        ScriptWeighting weighting = new ScriptWeighting(carFlagEncoder, assignments);

        IntsRef ints = new IntsRef(1);
        maxWeightEnc.setDecimal(false, ints, 8);
        assertEquals(36, weighting.calcWeight(GHUtility.createMockedEdgeIteratorState(1000, ints),
                false, -1), .1);

        // max_weight on road is just 6
        maxWeightEnc.setDecimal(false, ints, 6);
        assertEquals(45, weighting.calcWeight(GHUtility.createMockedEdgeIteratorState(1000, ints),
                false, -1), .1);

        // fall back to default speed as reverse weight is infinity
        assertEquals(36, weighting.calcWeight(GHUtility.createMockedEdgeIteratorState(1000, ints),
                true, -1), .1);
    }
}