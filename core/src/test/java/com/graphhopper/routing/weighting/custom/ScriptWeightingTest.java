package com.graphhopper.routing.weighting.custom;

import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.MaxSpeed;
import com.graphhopper.routing.ev.RoadClass;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.CustomModel;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.EdgeIteratorState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;

import static com.graphhopper.routing.ev.RoadClass.*;
import static com.graphhopper.routing.weighting.TurnCostProvider.NO_TURN_COST_PROVIDER;
import static com.graphhopper.routing.weighting.custom.ScriptWeighting.parseAndGuessParametersFromCondition;
import static com.graphhopper.routing.weighting.custom.ScriptWeighting.parseAndGuessParametersFromExpression;
import static org.junit.jupiter.api.Assertions.*;

// TODO NOW copy entire CustomWeightingTest
class ScriptWeightingTest {

    GraphHopperStorage graphHopperStorage;
    DecimalEncodedValue avSpeedEnc;
    DecimalEncodedValue maxSpeedEnc;
    EnumEncodedValue<RoadClass> roadClassEnc;
    EncodingManager encodingManager;
    FlagEncoder carFE;

    @BeforeEach
    public void setup() {
        carFE = new CarFlagEncoder().setSpeedTwoDirections(true);
        encodingManager = new EncodingManager.Builder().add(carFE).build();
        avSpeedEnc = carFE.getAverageSpeedEnc();
        maxSpeedEnc = encodingManager.getDecimalEncodedValue(MaxSpeed.KEY);
        roadClassEnc = encodingManager.getEnumEncodedValue(KEY, RoadClass.class);
        graphHopperStorage = new GraphBuilder(encodingManager).create();
    }

    @Test
    public void testPriority() {
        EdgeIteratorState primary = graphHopperStorage.edge(0, 1, 10, true).
                set(roadClassEnc, PRIMARY).set(avSpeedEnc, 80);
        EdgeIteratorState secondary = graphHopperStorage.edge(1, 2, 10, true).
                set(roadClassEnc, SECONDARY).set(avSpeedEnc, 70);

        CustomModel vehicleModel = new CustomModel();
        vehicleModel.getPriority().put("road_class == PRIMARY", 1.0);
        vehicleModel.getPriority().put("true", 0.5);

        assertEquals(1.15, createWeighting(vehicleModel).calcEdgeWeight(primary, false), 0.01);
        assertEquals(1.73, createWeighting(vehicleModel).calcEdgeWeight(secondary, false), 0.01);

        vehicleModel = new CustomModel();
        vehicleModel.getPriority().put("road_class != PRIMARY", 0.5);
        assertEquals(1.15, createWeighting(vehicleModel).calcEdgeWeight(primary, false), 0.01);
        assertEquals(1.73, createWeighting(vehicleModel).calcEdgeWeight(secondary, false), 0.01);

        vehicleModel.getPriority().put("road_class == SECONDARY", 0.7);
        vehicleModel.getPriority().put("true", 0.9);
        assertEquals(1.2, createWeighting(vehicleModel).calcEdgeWeight(primary, false), 0.01);
        assertEquals(1.73, createWeighting(vehicleModel).calcEdgeWeight(secondary, false), 0.01);

        // force integer value
        vehicleModel = new CustomModel();
        vehicleModel.getPriority().put("road_class == PRIMARY", 1);
        assertEquals(1.15, createWeighting(vehicleModel).calcEdgeWeight(primary, false), 0.01);
    }

    @Test
    public void testSpeedFactorAndPriority() {
        EdgeIteratorState primary = graphHopperStorage.edge(0, 1, 10, true).
                set(roadClassEnc, PRIMARY).set(avSpeedEnc, 80);
        EdgeIteratorState secondary = graphHopperStorage.edge(1, 2, 10, true).
                set(roadClassEnc, SECONDARY).set(avSpeedEnc, 70);

        CustomModel vehicleModel = new CustomModel();
        vehicleModel.getPriority().put("road_class == PRIMARY", 1.0);
        vehicleModel.getPriority().put("true", 0.5);

        vehicleModel.getSpeedFactor().put("road_class != PRIMARY", 0.9);
        assertEquals(1.15, createWeighting(vehicleModel).calcEdgeWeight(primary, false), 0.01);
        assertEquals(1.84, createWeighting(vehicleModel).calcEdgeWeight(secondary, false), 0.01);
    }

    @Test
    public void testSpeedFactorAndPriorityAndMaxSpeed() {
        EdgeIteratorState primary = graphHopperStorage.edge(0, 1, 10, true).
                set(roadClassEnc, PRIMARY).set(avSpeedEnc, 80);
        EdgeIteratorState secondary = graphHopperStorage.edge(1, 2, 10, true).
                set(roadClassEnc, SECONDARY).set(avSpeedEnc, 70);

        CustomModel vehicleModel = new CustomModel();
        vehicleModel.getPriority().put("road_class == PRIMARY", 0.9);
        vehicleModel.getSpeedFactor().put("road_class == PRIMARY", 0.8);
        assertEquals(1.33, createWeighting(vehicleModel).calcEdgeWeight(primary, false), 0.01);
        assertEquals(1.21, createWeighting(vehicleModel).calcEdgeWeight(secondary, false), 0.01);

        vehicleModel.getMaxSpeed().put("road_class != PRIMARY", 50);
        assertEquals(1.33, createWeighting(vehicleModel).calcEdgeWeight(primary, false), 0.01);
        assertEquals(1.42, createWeighting(vehicleModel).calcEdgeWeight(secondary, false), 0.01);
    }

    private Weighting createWeighting(CustomModel vehicleModel) {
        return new ScriptWeighting(carFE, encodingManager, NO_TURN_COST_PROVIDER, vehicleModel);
    }

    @Test
    public void protectUsFromStuff() {
        HashSet<String> set = new HashSet<>();
        ScriptWeighting.NameValidator allNamesInvalid = s -> false;
        for (String toParse : Arrays.asList("",
                "new Object()",
                "java.lang.Object",
                "Test.class",
                "new Object(){}.toString().length",
                "{ 5}",
                "{ 5, 7 }",
                "Object.class",
                "System.out.println(\"\")",
                "something.newInstance()",
                "e.getClass ( )",
                "edge.getDistance()*7/*test",
                "edge.getDistance()//*test",
                "edge . getClass()",
                "(edge = edge) == edge",
                ") edge (",
                "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd" +
                        "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd")) {
            assertFalse(parseAndGuessParametersFromCondition(set, toParse, allNamesInvalid), "should not be simple condition: " + toParse);
            assertFalse(parseAndGuessParametersFromExpression(set, toParse, allNamesInvalid), "should not be simple expression: " + toParse);
            assertEquals("[]", set.toString());
        }

        assertFalse(parseAndGuessParametersFromExpression(set, "edge; getClass()", allNamesInvalid));
        assertFalse(parseAndGuessParametersFromCondition(set, "edge; getClass()", allNamesInvalid));
    }

    @Test
    public void isValidAndSimpleExpression() {
        HashSet<String> set = new HashSet<>();
        ScriptWeighting.NameValidator allNamesInvalid = s -> false;

        // for now accept only number literals
        assertTrue(parseAndGuessParametersFromExpression(set, 5, allNamesInvalid));
        assertTrue(parseAndGuessParametersFromExpression(set, 5.0, allNamesInvalid));
        assertFalse(parseAndGuessParametersFromExpression(set, "5", allNamesInvalid));
        assertFalse(parseAndGuessParametersFromExpression(set, "false", allNamesInvalid));
        assertFalse(parseAndGuessParametersFromExpression(set, "test == TEST", allNamesInvalid));

        assertEquals("[]", set.toString());
    }

    @Test
    public void isValidAndSimpleCondition() {
        HashSet<String> set = new HashSet<>();
        ScriptWeighting.NameValidator nameValidator1 = s -> s.equals("edge") || s.equals("PRIMARY") || s.equals("road_class");
        assertTrue(parseAndGuessParametersFromCondition(set, "edge == edge", nameValidator1));
        assertEquals("[edge]", set.toString());
        assertTrue(parseAndGuessParametersFromCondition(set, "edge.getDistance()", nameValidator1));
        assertEquals("[edge]", set.toString());
        assertTrue(parseAndGuessParametersFromCondition(set, "road_class == PRIMARY", nameValidator1));
        assertEquals("[edge, road_class]", set.toString());
        assertFalse(parseAndGuessParametersFromCondition(set, "road_class == PRIMARY", s -> false));
        assertTrue(parseAndGuessParametersFromCondition(set, "road_class.ordinal()*2 == PRIMARY.ordinal()*2", nameValidator1));
        assertTrue(parseAndGuessParametersFromCondition(set, "Math.sqrt(road_class.ordinal()) > 1", nameValidator1));
    }
}