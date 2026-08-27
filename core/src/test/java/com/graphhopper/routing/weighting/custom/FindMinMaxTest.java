package com.graphhopper.routing.weighting.custom;

import com.graphhopper.json.MinMax;
import com.graphhopper.json.Statement;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.RoadEnvironment;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.util.CustomModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.graphhopper.json.Statement.*;
import static com.graphhopper.json.Statement.Op.LIMIT;
import static com.graphhopper.json.Statement.Op.MULTIPLY;
import static org.junit.jupiter.api.Assertions.*;

class FindMinMaxTest {

    static MinMax findMinMax(MinMax minMax, List<Statement> statements, EncodedValueLookup lookup) {
        return FindMinMax.findMinMax(minMax, statements, lookup, Map.of());
    }

    private EncodedValueLookup lookup;

    @BeforeEach
    void setup() {
        lookup = new EncodingManager.Builder().add(RoadEnvironment.create()).build();
    }

    @Test
    public void testCheck() {
        CustomModel queryModel = new CustomModel();
        queryModel.addToPriority(If("max_width < 3", MULTIPLY, "10"));
        assertEquals(1, CustomModel.merge(new CustomModel(), queryModel).getPriority().size());
        // priority bigger than 1 is not ok for CustomModel of query
        assertThrows(IllegalArgumentException.class, () -> FindMinMax.checkLMConstraints(new CustomModel(), queryModel, lookup));
    }

    @Test
    public void testCheckParameters() {
        CustomModel baseModel = new CustomModel().setParameter("max_speed", 90.0).setParameter("flag", true);
        baseModel.addToSpeed(If("true", LIMIT, "p_max_speed"));

        // parameters can only be defined in the server-side custom model
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> FindMinMax.checkLMConstraints(baseModel, new CustomModel().setParameter("other", 0.8), lookup));
        assertTrue(ex.getMessage().contains("not defined in the server-side custom model"), ex.getMessage());

        // a lower speed limit increases the weight and is fine, a higher one is rejected
        FindMinMax.checkLMConstraints(baseModel, new CustomModel().setParameter("max_speed", 80), lookup);
        ex = assertThrows(IllegalArgumentException.class,
                () -> FindMinMax.checkLMConstraints(baseModel, new CustomModel().setParameter("max_speed", 100), lookup));
        assertTrue(ex.getMessage().contains("could increase"), ex.getMessage());

        // a boolean parameter cannot be changed
        ex = assertThrows(IllegalArgumentException.class,
                () -> FindMinMax.checkLMConstraints(baseModel, new CustomModel().setParameter("flag", false), lookup));
        assertTrue(ex.getMessage().contains("cannot change the parameter 'flag'"), ex.getMessage());

        // a parameter used in turn_penalty cannot be changed
        CustomModel turnModel = new CustomModel().setParameter("penalty", 10.0);
        turnModel.addToTurnPenalty(If("road_environment == FERRY", MULTIPLY, "p_penalty"));
        ex = assertThrows(IllegalArgumentException.class,
                () -> FindMinMax.checkLMConstraints(turnModel, new CustomModel().setParameter("penalty", 5), lookup));
        assertTrue(ex.getMessage().contains("turn_penalty"), ex.getMessage());
    }

    @Test
    public void testChangeParameterInCondition() {
        // like the bike custom models: a lower rating excludes more roads and is fine
        CustomModel bike = new CustomModel().setParameter("max_rating", 2.0);
        bike.addToPriority(If("mtb_rating > p_max_rating", MULTIPLY, "0"));
        bike.addToPriority(ElseIf("mtb_rating > 1", MULTIPLY, "0.5"));
        FindMinMax.checkLMConstraints(bike, new CustomModel().setParameter("max_rating", 1), lookup);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> FindMinMax.checkLMConstraints(bike, new CustomModel().setParameter("max_rating", 3), lookup));
        assertTrue(ex.getMessage().contains("fewer edges"), ex.getMessage());

        // like the truck custom model: a larger truck excludes more roads and is fine
        CustomModel truck = new CustomModel().setParameter("weight", 18.0).setParameter("width", 3.0);
        truck.addToPriority(If("max_weight < p_weight || max_width < p_width", MULTIPLY, "0"));
        FindMinMax.checkLMConstraints(truck, new CustomModel().setParameter("weight", 25), lookup);

        // even if the condition applies to more edges, only blocking statements (value 0) are supported
        CustomModel nonBlocking = new CustomModel().setParameter("max_rating", 2.0);
        nonBlocking.addToPriority(If("mtb_rating > p_max_rating", MULTIPLY, "0.6"));
        ex = assertThrows(IllegalArgumentException.class,
                () -> FindMinMax.checkLMConstraints(nonBlocking, new CustomModel().setParameter("max_rating", 1), lookup));
        assertTrue(ex.getMessage().contains("blocking statements"), ex.getMessage());

        // a condition that cannot be analyzed is rejected
        CustomModel equality = new CustomModel().setParameter("rating", 2.0);
        equality.addToPriority(If("mtb_rating == p_rating", MULTIPLY, "0"));
        ex = assertThrows(IllegalArgumentException.class,
                () -> FindMinMax.checkLMConstraints(equality, new CustomModel().setParameter("rating", 1), lookup));
        assertTrue(ex.getMessage().contains("cannot analyze"), ex.getMessage());
    }

    @Test
    public void testFindMax() {
        List<Statement> statements = new ArrayList<>();
        statements.add(If("true", LIMIT, "100"));
        assertEquals(100, findMinMax(new MinMax(0, 120), statements, lookup).max);

        statements.add(Else(LIMIT, "20"));
        assertEquals(100, findMinMax(new MinMax(0, 120), statements, lookup).max);

        statements = new ArrayList<>();
        statements.add(If("road_environment == BRIDGE", LIMIT, "85"));
        statements.add(Else(LIMIT, "100"));
        assertEquals(100, findMinMax(new MinMax(0, 120), statements, lookup).max);

        // find bigger speed than stored max_speed (30) in server-side custom_models
        statements = new ArrayList<>();
        statements.add(If("true", MULTIPLY, "2"));
        statements.add(If("true", LIMIT, "35"));
        assertEquals(35, findMinMax(new MinMax(0, 30), statements, lookup).max);
    }

    @Test
    public void findMax_limitAndMultiply() {
        List<Statement> statements = Arrays.asList(
                If("road_class == TERTIARY", LIMIT, "90"),
                ElseIf("road_class == SECONDARY", MULTIPLY, "1.0"),
                ElseIf("road_class == PRIMARY", LIMIT, "30"),
                Else(LIMIT, "3")
        );
        assertEquals(140, findMinMax(new MinMax(0, 140), statements, lookup).max);
    }

    @Test
    public void testFindMaxPriority() {
        List<Statement> statements = new ArrayList<>();
        statements.add(If("true", MULTIPLY, "2"));
        assertEquals(2, findMinMax(new MinMax(0, 1), statements, lookup).max);

        List<Statement> statements2 = new ArrayList<>();
        statements2.add(If("true", MULTIPLY, "0.5"));
        assertEquals(0.5, findMinMax(new MinMax(0, 1), statements2, lookup).max);

        List<Statement> statements3 = new ArrayList<>();
        statements3.add(If("road_class == MOTORWAY", MULTIPLY, "0.5"));
        statements3.add(Else(MULTIPLY, "-0.5"));
        IllegalArgumentException m = assertThrows(IllegalArgumentException.class, () -> findMinMax(new MinMax(1, 1), statements3, lookup));
        assertTrue(m.getMessage().startsWith("statement resulted in negative value"));
    }

    @Test
    public void findMax_multipleBlocks() {
        List<Statement> statements = Arrays.asList(
                If("road_class == TERTIARY", MULTIPLY, "0.2"),
                ElseIf("road_class == SECONDARY", LIMIT, "25"),
                If("road_environment == TUNNEL", LIMIT, "60"),
                ElseIf("road_environment == BRIDGE", LIMIT, "50"),
                Else(MULTIPLY, "0.8")
        );
        assertEquals(120, findMinMax(new MinMax(0, 150), statements, lookup).max);
        assertEquals(80, findMinMax(new MinMax(0, 100), statements, lookup).max);
        assertEquals(60, findMinMax(new MinMax(0, 60), statements, lookup).max);

        statements = Arrays.asList(
                If("road_environment == TUNNEL", LIMIT, "130"),
                ElseIf("road_environment == BRIDGE", LIMIT, "50"),
                Else(MULTIPLY, "0.8")
        );
        assertEquals(130, findMinMax(new MinMax(0, 150), statements, lookup).max);

        statements = Arrays.asList(
                If("road_class == TERTIARY", MULTIPLY, "0.2"),
                ElseIf("road_class == SECONDARY", LIMIT, "25"),
                Else(LIMIT, "40"),
                If("road_environment == TUNNEL", MULTIPLY, "0.8"),
                ElseIf("road_environment == BRIDGE", LIMIT, "30")
        );
        assertEquals(40, findMinMax(new MinMax(0, 150), statements, lookup).max);
        assertEquals(40, findMinMax(new MinMax(0, 40), statements, lookup).max);
    }

    @Test
    public void testBlock() {
        List<Statement> statements = Arrays.asList(
                If("road_class == TERTIARY",
                        List.of(If("max_speed > 100", LIMIT, "100"),
                                Else(LIMIT, "30"))),
                ElseIf("road_class == SECONDARY", LIMIT, "25"),
                Else(MULTIPLY, "0.8")
        );
        assertEquals(100, findMinMax(new MinMax(0, 120), statements, lookup).max);

        statements = Arrays.asList(
                If("road_class == TERTIARY",
                        List.of(If("max_speed > 100", LIMIT, "90"),
                                Else(LIMIT, "30"))),
                ElseIf("road_class == SECONDARY", LIMIT, "25"),
                Else(MULTIPLY, "0.8")
        );
        assertEquals(96, findMinMax(new MinMax(0, 120), statements, lookup).max);
    }
}
