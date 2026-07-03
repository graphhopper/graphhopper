/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.routing.weighting.custom;

import com.graphhopper.json.Statement;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.PriorityCode;
import com.graphhopper.routing.weighting.custom.expression.ExpressionContext;
import com.graphhopper.routing.weighting.custom.expression.ExpressionScope;
import com.graphhopper.routing.weighting.custom.expression.ExpressionScopes;
import com.graphhopper.routing.weighting.custom.expression.ExpressionValidation;
import com.graphhopper.routing.weighting.custom.expression.ExpressionValidator;
import com.graphhopper.util.CustomModel;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.Helper;
import com.graphhopper.util.JsonFeature;
import com.graphhopper.util.JsonFeatureCollection;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import static com.graphhopper.json.Statement.*;
import static com.graphhopper.json.Statement.Op.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Differential test for the shared expression front-end (stage 3 of the custom-model
 * platform work): the pure-Kotlin recursive-descent parser + validator in
 * com.graphhopper.routing.weighting.custom.expression must make exactly the same
 * accept/reject decisions as the Janino-based pipeline.
 *
 * Two levels are compared, case by case:
 * <ul>
 * <li>parse level: {@link ExpressionValidator#condition}/{@link ExpressionValidator#value}
 * vs the Janino visitors ({@link ConditionalExpressionVisitor}/{@link ValueExpressionVisitor})
 * over a corpus of conditions/values from the unit tests, the bundled custom models and a
 * generated set of invalid expressions,</li>
 * <li>full-pipeline level: {@link ExpressionValidator#conditionStrict}/{@link ExpressionValidator#valueStrict}
 * vs {@link CustomModelParser#createWeightingParameters} (which compiles the generated class),
 * covering enum-constant validity, area references and variable declarability.</li>
 * </ul>
 * Error message texts intentionally do NOT need to match — only accept/reject must align.
 */
public class ExpressionParserDifferentialTest {

    static EncodingManager em;
    static NameValidator janinoConditionValidator;
    static ClassHelper janinoClassHelper;
    static NameValidator janinoValueValidator;
    static ExpressionScope conditionScope;
    static ExpressionScope valueScope;

    static final List<String> MODEL_FILES = Arrays.asList(
            "avoid_turns.json", "bike_avoid_private.json", "bike_avoid_private_node.json",
            "bike_elevation.json", "bike.json", "bike_tc.json", "bus.json", "car4wd.json",
            "car_avoid_private_etc.json", "car_avoid_private_etc_node.json", "cargo_bike.json",
            "car.json", "curvature.json", "foot_avoid_private.json", "foot_avoid_private_node.json",
            "foot_elevation.json", "foot.json", "hike.json", "motorcycle.json", "mtb.json",
            "racingbike.json", "softblock_entry_by_turn_restriction.json", "truck.json");

    @BeforeAll
    static void setup() {
        EncodingManager.Builder builder = new EncodingManager.Builder();
        for (String vehicle : Arrays.asList("car", "bike", "foot", "mtb", "racingbike", "bus"))
            builder.add(VehicleAccess.create(vehicle));
        for (String vehicle : Arrays.asList("car", "bike", "foot", "mtb", "racingbike"))
            builder.add(VehicleSpeed.create(vehicle, 5, 2, true));
        for (String vehicle : Arrays.asList("bike", "foot", "mtb", "racingbike"))
            builder.add(VehiclePriority.create(vehicle, 4, PriorityCode.getFactor(1), false));
        em = builder
                .add(RoadClass.create()).add(RoadEnvironment.create()).add(RoadAccess.create())
                .add(Roundabout.create()).add(Surface.create()).add(TrackType.create())
                .add(Toll.create()).add(Hazmat.create()).add(Hgv.create())
                .add(MaxSpeed.create()).add(MaxWeight.create()).add(MaxWeightExcept.create())
                .add(MaxHeight.create()).add(MaxWidth.create()).add(FerrySpeed.create())
                .add(AverageSlope.create()).add(Curvature.create()).add(GetOffBike.create())
                .add(UrbanDensity.create()).add(Country.create()).add(State.create())
                .add(new SimpleBooleanEncodedValue(IsSoftblockedAtEntry.KEY, false)).add(Orientation.create())
                .add(RouteNetwork.create(BikeNetwork.KEY)).add(RouteNetwork.create(FootNetwork.KEY))
                .add(RouteNetwork.create(MtbNetwork.KEY))
                .add(BikeRoadAccess.create()).add(FootRoadAccess.create())
                .add(HikeRating.create()).add(MtbRating.create())
                .build();

        // 1:1 mirror of CustomModelParser.verifyExpressions' nameInConditionValidator
        janinoConditionValidator = name -> em.hasEncodedValue(name)
                || Helper.toUpperCase(name).equals(name)
                || name.startsWith("in_")
                || name.equals("change_angle") || name.equals("street_name") || name.equals("prev_street_name")
                || (name.startsWith("backward_") && em.hasEncodedValue(name.substring("backward_".length())))
                || (name.startsWith("prev_") && em.hasEncodedValue(name.substring("prev_".length())));
        // 1:1 mirror of CustomModelParser.verifyExpressions' ClassHelper over createSimplifiedLookup
        janinoClassHelper = key -> {
            String k = key;
            if (k.equals("street_name") || k.equals("prev_street_name"))
                throw new IllegalArgumentException("Couldn't find class for " + key);
            if (k.startsWith("backward_")) k = k.substring("backward_".length());
            else if (k.startsWith("prev_")) k = k.substring("prev_".length());
            if (!em.hasEncodedValue(k))
                throw new IllegalArgumentException("Couldn't find class for " + key);
            return "T";
        };
        // 1:1 mirror of ValueExpressionVisitor.findVariables' validator
        janinoValueValidator = name -> em.hasEncodedValue(name) || name.contains("Infinity");

        conditionScope = ExpressionScopes.conditionScope(em);
        valueScope = ExpressionScopes.valueScope(em);
    }

    // ------------------------------------------------------------------
    // Part A: parse-level parity against the Janino visitors
    // ------------------------------------------------------------------

    static final List<String> CONDITION_CORPUS = Arrays.asList(
            // from ConditionalExpressionVisitorTest.protectUsFromStuff
            "", "new Object()", "java.lang.Object", "Test.class", "new Object(){}.toString().length",
            "{ 5}", "{ 5, 7 }", "Object.class", "System.out.println(\"\")", "something.newInstance()",
            "e.getClass ( )", "edge.getDistance()*7/*test", "edge.getDistance()//*test",
            "edge . getClass()", "(edge = edge) == edge", ") edge (", "in(area_blup(), edge)",
            "s -> truevalue", "edge; getClass()",
            // from the visitor unit tests, with encoded values of this EncodingManager
            "toll == NO", "road_class == PRIMARY", "toll == Toll.NO", "toll == NO || road_class == NO",
            "in_custom_1", "in_something", "edge == edge", "Math.sqrt(max_speed)", "Math.sqrt(2)",
            "edge.blup()", "edge.getDistance()", "road_class.ordinal()*2 == PRIMARY.ordinal()*2",
            "Math.sqrt(road_class.ordinal()) > 1", "(toll == NO || road_class == PRIMARY) && toll == NO",
            "backward_car_access", "Math.abs(average_slope) < -0.5", "average_slope < -0.5",
            "-average_slope > -0.5", "Math.sqrt(-2)", "road_class == primary",
            // operator ladder (Janino accepts all binary operators at parse level)
            "max_speed >> 2 == 1", "max_speed >>> 2 == 1", "(max_speed & 3) == 1",
            "car_access | roundabout", "car_access ^ roundabout", "max_speed % 2 == 0",
            "max_speed / 2 > 1", "max_speed instanceof String", "max_speed ? 1 : 0",
            "max_speed + 2 > 5", "max_speed - 2 < 5", "max_speed == 30 == car_access",
            // literals
            "0x10 == max_speed", "1_000 > max_speed", "1__0 > max_speed", "1_ > max_speed",
            "1e3 > max_speed", "1.5e > max_speed", "'c' == max_speed", "'ab' == max_speed",
            "'\\n' == max_speed", "010 > max_speed", "09 > max_speed", ".5 < max_speed",
            "5. < max_speed", "1d > max_speed", "2f > max_speed", "3L > max_speed", "0b101 == max_speed",
            "3.4e+2 > max_speed", "3.4e-2 > max_speed", "0.5f > max_speed",
            "street_name == \"x\"", "null == car_access", "\"unterminated == max_speed",
            // unary
            "- -max_speed > 0", "--max_speed > 0", "+max_speed > 0", "~max_speed > 0", "!(!car_access)", "!5",
            // enum comparison rule shapes
            "-max_speed == NO", "(max_speed) == NO", "max_speed == NO.X", "max_speed == (NO)",
            "NO == max_speed", "max_speed != NO", "max_speed < NO", "max_speed <= NO",
            "in_area1 == NO", "change_angle == NO", "street_name == NO", "backward_road_class == PRIMARY",
            "prev_road_class == PRIMARY", "toll == MISSING && road_class == PRIMARY", "hazmat != NO",
            "surface==COBBLESTONE||surface==GRASS",
            // methods
            "prev_street_name.equals(street_name)", "street_name.equals(prev_street_name, street_name)",
            "street_name.contains(\"main\")", "x.y.z()", "sqrt(2)", "edge.sqrt(2) > 0",
            "Math.getDistance() > 0", "country.isRightHandTraffic()", "road_class.ordinal() == 2",
            "(road_class).ordinal() == 2", "road_class.b", "edge.fetchWayGeometry().size() > 2",
            // structural garbage / trailing input
            "max_speed;", "max_speed 30", "max_speed == NO ||", "(max_speed", "max_speed)",
            "größe > 2", "in_xyz", "!in_xyz", "in_", "max_speed[0] > 1", "max_speed = 30",
            "(max_speed = 30) == 30", "max_speed++ > 0", "(double) max_speed > 5",
            "max_speed\t\n== 30", "max_speed == 30 &&& car_access", "max_speed & & 3",
            // comments (Janino quirk: line comment must be newline-terminated)
            "max_speed > 3 // c\n", "max_speed > 3 /* c */", "/* c */ max_speed > 3",
            "max_speed > 3 //", "max_speed > 3 // c", "max_speed /* unterminated",
            // special variables
            "change_angle > 25", "true", "false");

    static final List<String> VALUE_CORPUS = Arrays.asList(
            // from ValueExpressionVisitorTest.protectUsFromStuff
            "", "new Object()", "java.lang.Object", "Test.class", "new Object(){}.toString().length",
            "{ 5}", "{ 5, 7 }", "Object.class", "System.out.println(\"\")", "something.newInstance()",
            "e.getClass ( )", "edge.getDistance()*7/*test", "edge.getDistance()//*test",
            "edge . getClass()", "(edge = edge) == edge", ") edge (", "in(area_blup(), edge)",
            "s -> truevalue", "edge; getClass()",
            // visitor unit tests + operators
            "edge == edge", "Math.sqrt(2)", "Math.sqrt(max_speed)", "edge.getDistance()",
            "road_class == PRIMARY", "toll == Toll.NO", "max_speed * 2", "2*max_speed",
            "-2", "+2", "!max_speed", "max_speed / 2", "max_speed % 2", "1/max_speed",
            "Math.sqrt(2, 3)", "Math.sqrt()", "Math.abs(max_speed)", "sqrt(max_speed)",
            "max_speed.sqrt(2)", "Math.sqrt", "(max_speed + 2) * 3", "max_speed ? 1 : 0",
            "\"x\"", "'c'", "null", "true", "0x10", "1e3", "Infinity", "-Infinity",
            "max_speed // c", "Math.sqrt(Math.sqrt(max_speed))", "- -max_speed", "-(-max_speed)",
            "unknown*3", "my_priority - my_priority2 * 3", "max_speed*car_average_speed * 3",
            "max_speed - 100", "-0.5", "-max_speed", "0.9 * car_average_speed", "max_speed * 0.9",
            "car_average_speed", ".1", "100", "27.5", "2*Infinity");

    @Test
    public void conditionParseParity() {
        List<String> corpus = new ArrayList<>(CONDITION_CORPUS);
        corpus.addAll(collectModelExpressions(true));
        assertTrue(corpus.size() > 150, "condition corpus unexpectedly small: " + corpus.size());
        for (String expr : corpus) {
            ParseResult janino = ConditionalExpressionVisitor.parse(expr, janinoConditionValidator, janinoClassHelper);
            ExpressionValidation mine = ExpressionValidator.condition(expr, conditionScope);
            assertEquals(janino.ok, mine.ok, () -> "condition parity failed for <" + expr + ">: janino="
                    + janino.ok + " mine=" + mine.ok + " (mine message: " + mine.invalidMessage + ")");
            if (janino.ok)
                assertEquals(new ArrayList<>(janino.guessedVariables), new ArrayList<>(mine.guessedVariables),
                        () -> "guessed variables differ for <" + expr + ">");
        }
    }

    @Test
    public void valueParseParity() {
        List<String> corpus = new ArrayList<>(VALUE_CORPUS);
        corpus.addAll(collectModelExpressions(false));
        assertTrue(corpus.size() > 100, "value corpus unexpectedly small: " + corpus.size());
        for (String expr : corpus) {
            ParseResult janino = ValueExpressionVisitor.parse(expr, janinoValueValidator);
            ExpressionValidation mine = ExpressionValidator.value(expr, valueScope);
            assertEquals(janino.ok, mine.ok, () -> "value parity failed for <" + expr + ">: janino="
                    + janino.ok + " mine=" + mine.ok + " (mine message: " + mine.invalidMessage + ")");
            if (janino.ok) {
                assertEquals(new ArrayList<>(janino.guessedVariables), new ArrayList<>(mine.guessedVariables),
                        () -> "guessed variables differ for <" + expr + ">");
                assertEquals(new ArrayList<>(janino.operators), new ArrayList<>(mine.operators),
                        () -> "operators differ for <" + expr + ">");
            }
        }
    }

    // ------------------------------------------------------------------
    // Part B: full-pipeline parity against CustomModelParser (Janino compile)
    // ------------------------------------------------------------------

    @Test
    public void bundledModelsFullParity() {
        for (String file : MODEL_FILES) {
            CustomModel model = normalized(GHUtility.loadCustomModelFromJar(file));
            boolean janino = janinoAccepts(model);
            boolean mine = mineAccepts(model);
            assertEquals(janino, mine, "full-pipeline parity failed for bundled model " + file
                    + ": janino=" + janino + " mine=" + mine);
            assertTrue(janino, "expected bundled model to be accepted: " + file);
        }
    }

    @Test
    public void edgeConditionFullParity() {
        List<String> validConditions = Arrays.asList(
                "road_class == PRIMARY", "toll == NO", "max_speed > 50", "!car_access",
                "backward_car_access != car_access", "car_average_speed > 40 && road_class != MOTORWAY",
                "Math.sqrt(max_speed) > 5", "road_class.ordinal() > 1", "country == DEU",
                "state == US_AK", "surface==COBBLESTONE || surface==GRASS", "true",
                "max_weight_except == MISSING", "country.isRightHandTraffic()",
                "max_weight < 18 && max_weight_except == MISSING");
        List<String> invalidConditions = Arrays.asList(
                // unknown identifiers
                "unknown_ev == PRIMARY", "unknown_ev > 3", "unknown_ev",
                // enum constant not a member of the encoded value's enum
                "road_class == NO_SUCH_VALUE", "toll == PRIMARY", "road_environment == PRIMARY",
                // uppercase constants compared with non-enum encoded values
                "max_speed == MISSING", "car_access == MISSING",
                // bad operators on enums
                "road_class < PRIMARY", "road_class >= PRIMARY",
                // stray uppercase identifiers outside the enum position
                "road_class.ordinal() == PRIMARY.ordinal()", "PRIMARY == road_class",
                "road_class == (PRIMARY)",
                // whitelisted-at-parse-level but not declarable per context
                "edge.getDistance() > 100", "street_name.contains(\"main\")",
                "prev_road_class == PRIMARY", "change_angle > 25", "prev_street_name.equals(street_name)",
                // areas (none registered for this scope)
                "in_unknown_area",
                // method calls / array access / assignments / structure
                "edge.fetchWayGeometry().size() > 2", "System.exit(0) == 0", "road_class[0] == PRIMARY",
                "(road_class == PRIMARY", "road_class = PRIMARY", "road_class == PRIMARY ? true : false",
                "new Object() != null");
        for (String condition : validConditions)
            assertEdgeConditionFullParity(condition, true);
        for (String condition : invalidConditions)
            assertEdgeConditionFullParity(condition, false);
    }

    private void assertEdgeConditionFullParity(String condition, boolean expected) {
        CustomModel model = baseModel();
        model.addToPriority(If(condition, MULTIPLY, "1"));
        boolean janino = janinoAccepts(model);
        boolean mine = ExpressionValidator.conditionStrict(condition, conditionScope, ExpressionContext.EDGE).ok;
        assertEquals(janino, mine, "full-pipeline parity failed for <" + condition + ">: janino=" + janino + " mine=" + mine);
        assertEquals(expected, janino, "unexpected janino verdict for <" + condition + ">");
    }

    @Test
    public void turnPenaltyConditionFullParity() {
        List<String> validConditions = Arrays.asList(
                "change_angle > 25", "prev_road_class != road_class", "prev_street_name.equals(street_name)",
                "is_softblocked_at_entry", "!prev_car_access || !car_access", "street_name.contains(\"main\")",
                "road_class == PRIMARY && prev_road_class != PRIMARY");
        List<String> invalidConditions = Arrays.asList(
                // not declarable in turn_penalty context
                "backward_car_access", "in_unknown_area", "edge.getDistance() > 100",
                // invalid everywhere
                "unknown_ev > 3", "road_class == NO_SUCH_VALUE");
        for (String condition : validConditions)
            assertTurnPenaltyConditionFullParity(condition, true);
        for (String condition : invalidConditions)
            assertTurnPenaltyConditionFullParity(condition, false);
    }

    private void assertTurnPenaltyConditionFullParity(String condition, boolean expected) {
        CustomModel model = baseModel();
        model.addToTurnPenalty(If(condition, ADD, "1"));
        boolean janino = janinoAccepts(model);
        boolean mine = ExpressionValidator.conditionStrict(condition, conditionScope, ExpressionContext.TURN_PENALTY).ok;
        assertEquals(janino, mine, "full-pipeline parity failed for <" + condition + ">: janino=" + janino + " mine=" + mine);
        assertEquals(expected, janino, "unexpected janino verdict for <" + condition + ">");
    }

    @Test
    public void valueFullParity() {
        // speed values via limit_to
        List<String> validLimitValues = Arrays.asList(
                "100", "25", "0.9 * car_average_speed", "max_speed * 0.9", "car_average_speed",
                "Math.sqrt(max_speed)", "0x10", "max_speed + max_speed", "1_000");
        List<String> invalidLimitValues = Arrays.asList(
                "unknown", "max_speed * car_average_speed", "1/max_speed", "-0.5", "-max_speed",
                "max_speed - 200", "road_class", "car_access", "2*Infinity", "Infinity", "street_name");
        for (String value : validLimitValues)
            assertLimitValueFullParity(value, true);
        for (String value : invalidLimitValues)
            assertLimitValueFullParity(value, false);

        // turn penalty values via add (the only operation that maps "Infinity")
        for (String value : Arrays.asList("Infinity", "100", "2000", "car_average_speed * 2"))
            assertAddValueFullParity(value, true);
        assertAddValueFullParity("2*Infinity", false);
    }

    private void assertLimitValueFullParity(String value, boolean expected) {
        CustomModel model = baseModel();
        model.addToSpeed(If("true", LIMIT, value));
        boolean janino = janinoAccepts(model);
        boolean mine = ExpressionValidator.valueStrict(value, valueScope, false).ok;
        assertEquals(janino, mine, "full-pipeline parity failed for limit_to <" + value + ">: janino=" + janino + " mine=" + mine);
        assertEquals(expected, janino, "unexpected janino verdict for limit_to <" + value + ">");
    }

    private void assertAddValueFullParity(String value, boolean expected) {
        CustomModel model = baseModel();
        model.addToTurnPenalty(If("true", ADD, value));
        boolean janino = janinoAccepts(model);
        boolean mine = ExpressionValidator.valueStrict(value, valueScope, true).ok;
        assertEquals(janino, mine, "full-pipeline parity failed for add <" + value + ">: janino=" + janino + " mine=" + mine);
        assertEquals(expected, janino, "unexpected janino verdict for add <" + value + ">");
    }

    @Test
    public void areaFullParity() {
        assertAreaFullParity("in_custom_1", true);
        assertAreaFullParity("!in_custom_1", true);
        assertAreaFullParity("in_custom_2", false);
        assertAreaFullParity("in_x__y", false);
    }

    private void assertAreaFullParity(String condition, boolean expected) {
        CustomModel model = baseModel();
        model.setAreas(areas("custom_1"));
        model.addToPriority(If(condition, MULTIPLY, "0.5"));

        boolean janino = janinoAccepts(model);
        Set<String> areaIds = CustomModel.getAreasAsMap(model.getAreas()).keySet();
        ExpressionScope scope = ExpressionScopes.conditionScope(em, areaIds);
        boolean mine = ExpressionValidator.conditionStrict(condition, scope, ExpressionContext.EDGE).ok;
        assertEquals(janino, mine, "area parity failed for <" + condition + ">: janino=" + janino + " mine=" + mine);
        assertEquals(expected, janino, "unexpected janino verdict for <" + condition + ">");
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    static boolean janinoAccepts(CustomModel model) {
        try {
            CustomModelParser.createWeightingParameters(model, em);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    static boolean mineAccepts(CustomModel model) {
        Set<String> areaIds = CustomModel.getAreasAsMap(model.getAreas()).keySet();
        ExpressionScope scope = areaIds.isEmpty() ? conditionScope : ExpressionScopes.conditionScope(em, areaIds);
        return statementsOk(model.getPriority(), ExpressionContext.EDGE, scope)
                && statementsOk(model.getSpeed(), ExpressionContext.EDGE, scope)
                && statementsOk(model.getTurnPenalty(), ExpressionContext.TURN_PENALTY, scope);
    }

    static boolean statementsOk(List<Statement> statements, ExpressionContext context, ExpressionScope scope) {
        for (Statement st : statements) {
            if (st.keyword() != Statement.Keyword.ELSE) {
                if (!ExpressionValidator.conditionStrict(st.condition(), scope, context).ok)
                    return false;
            }
            if (st.isBlock()) {
                if (!statementsOk(st.doBlock(), context, scope))
                    return false;
            } else {
                if (!ExpressionValidator.valueStrict(st.value(), valueScope, st.operation() == Statement.Op.ADD).ok)
                    return false;
            }
        }
        return true;
    }

    /** A minimal valid model (speed must start with an unconditional limit). */
    static CustomModel baseModel() {
        CustomModel model = new CustomModel();
        model.addToSpeed(If("true", LIMIT, "100"));
        return model;
    }

    /** Prepends the base speed limit so partial models (e.g. elevation-only) become structurally valid. */
    static CustomModel normalized(CustomModel model) {
        CustomModel n = baseModel();
        model.getSpeed().forEach(n::addToSpeed);
        model.getPriority().forEach(n::addToPriority);
        model.getTurnPenalty().forEach(n::addToTurnPenalty);
        if (model.getAreas() != null) n.setAreas(model.getAreas());
        return n;
    }

    /** All conditions (or values) of the bundled custom models, blocks included. */
    static List<String> collectModelExpressions(boolean conditions) {
        List<String> result = new ArrayList<>();
        for (String file : MODEL_FILES) {
            CustomModel model = GHUtility.loadCustomModelFromJar(file);
            collect(model.getPriority(), conditions, result);
            collect(model.getSpeed(), conditions, result);
            collect(model.getTurnPenalty(), conditions, result);
        }
        return result;
    }

    static void collect(List<Statement> statements, boolean conditions, List<String> result) {
        for (Statement st : statements) {
            if (conditions && st.keyword() != Statement.Keyword.ELSE)
                result.add(st.condition());
            if (st.isBlock())
                collect(st.doBlock(), conditions, result);
            else if (!conditions)
                result.add(st.value());
        }
    }

    static JsonFeatureCollection areas(String id) {
        Coordinate[] coordinates = new Coordinate[]{
                new Coordinate(48.019, 11.28), new Coordinate(48.019, 11.53),
                new Coordinate(48.118, 11.53), new Coordinate(48.118, 11.28),
                new Coordinate(48.019, 11.28)};
        JsonFeatureCollection areas = new JsonFeatureCollection();
        areas.getFeatures().add(new JsonFeature(id, "Feature", null,
                new GeometryFactory().createPolygon(coordinates), new HashMap<>()));
        return areas;
    }
}
