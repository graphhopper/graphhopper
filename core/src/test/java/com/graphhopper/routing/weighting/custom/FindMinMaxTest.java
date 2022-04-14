package com.graphhopper.routing.weighting.custom;

import com.graphhopper.json.Statement;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.util.CustomModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static com.graphhopper.json.Statement.*;
import static com.graphhopper.json.Statement.Op.LIMIT;
import static com.graphhopper.json.Statement.Op.MULTIPLY;
import static com.graphhopper.routing.weighting.custom.FindMinMax.findMax;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FindMinMaxTest {

    private EncodedValueLookup lookup;

    @BeforeEach
    void setup() {
        lookup = new EncodingManager.Builder().build();
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
    public void testFindMax() {
        List<Statement> statements = new ArrayList<>();
        Set<String> objs = new HashSet<>();
        statements.add(If("true", LIMIT, "100"));
        assertEquals(100, findMax(objs, statements, lookup, 120, "speed"));

        statements.add(Else(LIMIT, "20"));
        assertEquals(100, findMax(objs, statements, lookup, 120, "speed"));

        statements = new ArrayList<>();
        statements.add(If("road_environment == BRIDGE", LIMIT, "85"));
        statements.add(Else(LIMIT, "100"));
        assertEquals(100, findMax(objs, statements, lookup, 120, "speed"));

        // find bigger speed than stored max_speed in server-side custom_models
        double storedMaxSpeed = 30;
        statements = new ArrayList<>();
        statements.add(If("true", MULTIPLY, "2"));
        statements.add(If("true", LIMIT, "35"));
        assertEquals(35, findMax(objs, statements, lookup, 30, "speed"));
    }

    @Test
    public void findMax_limitAndMultiply() {
        Set<String> objs = new HashSet<>();
        List<Statement> statements = Arrays.asList(
                If("road_class == TERTIARY", LIMIT, "90"),
                ElseIf("road_class == SECONDARY", MULTIPLY, "1.0"),
                ElseIf("road_class == PRIMARY", LIMIT, "30"),
                Else(LIMIT, "3")
        );
        assertEquals(140, findMax(objs, statements, lookup, 140, "speed"));
    }

    @Test
    public void testFindMaxPriority() {
        Set<String> objs = new HashSet<>();
        List<Statement> statements = new ArrayList<>();
        statements.add(If("true", MULTIPLY, "2"));
        assertEquals(2, findMax(objs, statements, lookup, 1, "priority"));

        statements = new ArrayList<>();
        statements.add(If("true", MULTIPLY, "0.5"));
        assertEquals(0.5, findMax(objs, statements, lookup, 1, "priority"));
    }

    @Test
    public void findMax_multipleBlocks() {
        Set<String> objs = new HashSet<>();
        List<Statement> statements = Arrays.asList(
                If("road_class == TERTIARY", MULTIPLY, "0.2"),
                ElseIf("road_class == SECONDARY", LIMIT, "25"),
                If("road_environment == TUNNEL", LIMIT, "60"),
                ElseIf("road_environment == BRIDGE", LIMIT, "50"),
                Else(MULTIPLY, "0.8")
        );
        assertEquals(120, findMax(objs, statements, lookup, 150, "speed"));
        assertEquals(80, findMax(objs, statements, lookup, 100, "speed"));
        assertEquals(60, findMax(objs, statements, lookup, 60, "speed"));

        statements = Arrays.asList(
                If("road_class == TERTIARY", MULTIPLY, "0.2"),
                ElseIf("road_class == SECONDARY", LIMIT, "25"),
                Else(LIMIT, "40"),
                If("road_environment == TUNNEL", MULTIPLY, "0.8"),
                ElseIf("road_environment == BRIDGE", LIMIT, "30")
        );
        assertEquals(40, findMax(objs, statements, lookup, 150, "speed"));
        assertEquals(40, findMax(objs, statements, lookup, 40, "speed"));
    }
}