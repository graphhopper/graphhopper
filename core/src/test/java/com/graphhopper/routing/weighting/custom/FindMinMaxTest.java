package com.graphhopper.routing.weighting.custom;

import com.graphhopper.json.MinMax;
import com.graphhopper.json.Statement;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.util.CustomModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static com.graphhopper.json.Statement.*;
import static com.graphhopper.json.Statement.Op.LIMIT;
import static com.graphhopper.json.Statement.Op.MULTIPLY;
import static com.graphhopper.routing.weighting.custom.FindMinMax.findMinMax;
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

        statements = new ArrayList<>();
        statements.add(If("true", MULTIPLY, "0.5"));
        assertEquals(0.5, findMinMax(new MinMax(0, 1), statements, lookup).max);

        statements = new ArrayList<>();
        statements.add(If("road_class == MOTORWAY", MULTIPLY, "0.5"));
        statements.add(Else(MULTIPLY, "-0.5"));
        MinMax minMax = findMinMax(new MinMax(1, 1), statements, lookup);
        assertEquals(-0.5, minMax.min);
        assertEquals(0.5, minMax.max);
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
                If("road_class == TERTIARY", MULTIPLY, "0.2"),
                ElseIf("road_class == SECONDARY", LIMIT, "25"),
                Else(LIMIT, "40"),
                If("road_environment == TUNNEL", MULTIPLY, "0.8"),
                ElseIf("road_environment == BRIDGE", LIMIT, "30")
        );
        assertEquals(40, findMinMax(new MinMax(0, 150), statements, lookup).max);
        assertEquals(40, findMinMax(new MinMax(0, 40), statements, lookup).max);
    }
}
