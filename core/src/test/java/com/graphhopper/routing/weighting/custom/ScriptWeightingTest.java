package com.graphhopper.routing.weighting.custom;

import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.CustomModel;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.EdgeIteratorState;
import org.codehaus.janino.ExpressionEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static com.graphhopper.routing.ev.RoadClass.*;
import static com.graphhopper.routing.weighting.TurnCostProvider.NO_TURN_COST_PROVIDER;
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
    public void testBasics() {
        EdgeIteratorState primary = graphHopperStorage.edge(0, 1, 10, true).
                set(roadClassEnc, PRIMARY).set(avSpeedEnc, 80);
        EdgeIteratorState secondary = graphHopperStorage.edge(1, 2, 10, true).
                set(roadClassEnc, SECONDARY).set(avSpeedEnc, 70);

        CustomModel vehicleModel = new CustomModel();
        vehicleModel.getPriority().put("road_class == PRIMARY", 1.0);
        vehicleModel.getPriority().put("*", 0.5);

        assertEquals(1.15, createWeighting(vehicleModel).calcEdgeWeight(primary, false), 0.01);
        assertEquals(1.73, createWeighting(vehicleModel).calcEdgeWeight(secondary, false), 0.01);

        vehicleModel = new CustomModel();
        vehicleModel.getPriority().put("road_class != PRIMARY", 0.5);
        assertEquals(1.15, createWeighting(vehicleModel).calcEdgeWeight(primary, false), 0.01);
        assertEquals(1.73, createWeighting(vehicleModel).calcEdgeWeight(secondary, false), 0.01);

        // change priority for primary explicitly and change priority for secondary using catch all
        vehicleModel.getPriority().put("road_class == SECONDARY", 0.7);
        vehicleModel.getPriority().put(CustomWeighting.CATCH_ALL, 0.9);
        assertEquals(1.2, createWeighting(vehicleModel).calcEdgeWeight(primary, false), 0.01);
        assertEquals(1.73, createWeighting(vehicleModel).calcEdgeWeight(secondary, false), 0.01);

        // force integer value
        vehicleModel = new CustomModel();
        vehicleModel.getPriority().put("road_class == PRIMARY", 1);
        assertEquals(1.15, createWeighting(vehicleModel).calcEdgeWeight(primary, false), 0.01);
    }

    private Weighting createWeighting(CustomModel vehicleModel) {
        return new ScriptWeighting(carFE, encodingManager, NO_TURN_COST_PROVIDER, vehicleModel);
    }

    @Test
    public void isValidAndSimpleExpression() {
        HashSet<String> set = new HashSet<>();
        ScriptWeighting.NameValidator allNamesValid = s -> false;
        assertFalse(ScriptWeighting.parseAndGuessParameters(set, "new Object()", allNamesValid));
        assertFalse(ScriptWeighting.parseAndGuessParameters(set, "java.lang.Object", allNamesValid));
        assertFalse(ScriptWeighting.parseAndGuessParameters(set, "new Object(){}.toString().length", allNamesValid));
        assertFalse(ScriptWeighting.parseAndGuessParameters(set, "Object.class", allNamesValid));
        assertFalse(ScriptWeighting.parseAndGuessParameters(set, "System.out.println(\"\")", allNamesValid));
        assertFalse(ScriptWeighting.parseAndGuessParameters(set, "something.newInstance()", allNamesValid));
        assertFalse(ScriptWeighting.parseAndGuessParameters(set, "e.getClass ( )", allNamesValid));
        assertEquals("[]", set.toString());
        assertFalse(ScriptWeighting.parseAndGuessParameters(set, "edge; getClass()", allNamesValid));
        set.clear();
        assertFalse(ScriptWeighting.parseAndGuessParameters(set, "edge . getClass()", allNamesValid));
        assertFalse(ScriptWeighting.parseAndGuessParameters(set, "(edge = edge) == edge", allNamesValid));
        assertFalse(ScriptWeighting.parseAndGuessParameters(set, "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd" +
                "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd", allNamesValid));
        assertEquals("[]", set.toString());

        ScriptWeighting.NameValidator nameValidator1 = s -> s.equals("edge") || s.equals("PRIMARY") || s.equals("road_class");
        assertTrue(ScriptWeighting.parseAndGuessParameters(set, "edge == edge", nameValidator1));
        assertEquals("[edge]", set.toString());
        assertTrue(ScriptWeighting.parseAndGuessParameters(set, "edge.getDistance()", nameValidator1));
        assertEquals("[edge]", set.toString());
        assertTrue(ScriptWeighting.parseAndGuessParameters(set, "road_class == PRIMARY", nameValidator1));
        assertEquals("[edge, road_class]", set.toString());
        assertFalse(ScriptWeighting.parseAndGuessParameters(set, "road_class == PRIMARY", allNamesValid));
        assertTrue(ScriptWeighting.parseAndGuessParameters(set, "road_class.ordinal()*2 == PRIMARY.ordinal()*2", nameValidator1));
        assertTrue(ScriptWeighting.parseAndGuessParameters(set, "Math.sqrt(road_class.ordinal()) > 1", nameValidator1));
    }

    @Test
    public void testBasicsWithCompiler() throws Exception {
        EdgeIteratorState primary = graphHopperStorage.edge(0, 1, 10, true).
                set(roadClassEnc, PRIMARY).set(avSpeedEnc, 80);
        EdgeIteratorState secondary = graphHopperStorage.edge(1, 2, 10, true).
                set(roadClassEnc, SECONDARY).set(avSpeedEnc, 70);

        ExpressionEvaluator ee = new ExpressionEvaluator();
        ee.setClassName("GScript");
        ee.setDefaultImports("static com.graphhopper.routing.ev.RoadClass.*");
        ee.setOverrideMethod(new boolean[]{
                true,
        });
        ee.setStaticMethod(new boolean[]{
                false,
        });
        ee.setExpressionTypes(new Class[]{
                double.class,
        });
        ee.setExtendedClass(BaseClass.class);
        ee.setMethodNames(new String[]{
                "getValue",
        });
        ee.setParameters(new String[][]{
                {"edge", "reverse"},
        }, new Class[][]{
                {EdgeIteratorState.class, boolean.class},
        });

        ee.cook(new String[]{
                "e(road_class) == PRIMARY ? 0.7 : 1".replaceAll("e\\(", "edge.get("),
        });

        // init
        BaseClass.road_class = roadClassEnc;

        // per request
        EdgeToValueEntry instance = (EdgeToValueEntry) ee.getClazz().getDeclaredConstructor().newInstance();
        assertEquals(0.7, instance.getValue(primary, false));

        EdgeToValueEntry instance2 = (EdgeToValueEntry) ee.getClazz().getDeclaredConstructor().newInstance();
        assertEquals(1.0, instance2.getValue(secondary, false));
    }
}