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
import com.graphhopper.routing.querygraph.VirtualEdgeIteratorState;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.PriorityCode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.search.KVStorage;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

import java.util.*;
import java.util.function.Consumer;

import static com.graphhopper.json.Statement.*;
import static com.graphhopper.json.Statement.Op.*;
import static com.graphhopper.routing.weighting.TurnCostProvider.NO_TURN_COST_PROVIDER;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Differential test for the closure-composer back-end (stage 4 of the custom-model platform
 * work): {@link ClosureBackend} must be indistinguishable from {@link JaninoBackend} —
 * identical accept/reject decisions (including the type errors Janino only finds when
 * compiling the generated class) and BIT-IDENTICAL weights:
 * calcEdgeWeight/calcEdgeMillis for every edge of a randomized graph in both directions,
 * turn penalties for every incident edge pair, calcMinWeightPerDistance, and the
 * distance_influence/heading_penalty defaulting.
 *
 * Corpus: all bundled custom models, handcrafted models covering the typed-evaluation
 * corners (int-vs-double division, shifts, bitwise/boolean operators, enum/String/null
 * comparisons, Math.sqrt/abs overloads, literal typing, areas, backward_/prev_ prefixes,
 * change_angle/street_name) and randomized models over fixed seeds.
 */
public class ClosureBackendDifferentialTest {

    static final long GRAPH_SEED = 123L;
    static final long MODEL_SEED = 20260703L;
    static final int NODES = 40;
    static final int EDGES = 120;
    static final int RANDOM_MODELS = 120;

    static EncodingManager em;
    static BaseGraph graph;

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
        graph = createRandomGraph(new Random(GRAPH_SEED));
    }

    @AfterEach
    void restoreDefaultBackend() {
        CustomWeightingBackends.setDefault(JaninoBackend.INSTANCE);
    }

    static BaseGraph createRandomGraph(Random rnd) {
        BaseGraph g = new BaseGraph.Builder(em).create();
        NodeAccess na = g.getNodeAccess();
        for (int i = 0; i < NODES; i++)
            na.setNode(i, 48.0 + rnd.nextDouble() * 0.3, 11.2 + rnd.nextDouble() * 0.7);
        String[] names = {"Main St", "Oak Ave", "Elm St", "Hauptstrasse"};
        int created = 0;
        while (created < EDGES) {
            int a = rnd.nextInt(NODES), b = rnd.nextInt(NODES);
            if (a == b) continue;
            EdgeIteratorState edge = g.edge(a, b).setDistance(10 + rnd.nextInt(990));
            for (EncodedValue ev : em.getEncodedValues())
                setRandomValue(edge, ev, rnd);
            if (rnd.nextInt(3) > 0)
                edge.setKeyValues(Map.of(Parameters.Details.STREET_NAME, new KVStorage.KValue(names[rnd.nextInt(names.length)])));
            created++;
        }
        return g;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static void setRandomValue(EdgeIteratorState edge, EncodedValue ev, Random rnd) {
        // order matters: enum/boolean/decimal implementations also implement IntEncodedValue
        if (ev instanceof EnumEncodedValue) {
            EnumEncodedValue ee = (EnumEncodedValue) ev;
            Object[] values = ee.getValues();
            if (ee.isStoreTwoDirections())
                edge.set(ee, (Enum) values[rnd.nextInt(values.length)], (Enum) values[rnd.nextInt(values.length)]);
            else
                edge.set(ee, (Enum) values[rnd.nextInt(values.length)]);
        } else if (ev instanceof BooleanEncodedValue) {
            BooleanEncodedValue be = (BooleanEncodedValue) ev;
            if (be.isStoreTwoDirections()) edge.set(be, rnd.nextBoolean(), rnd.nextBoolean());
            else edge.set(be, rnd.nextBoolean());
        } else if (ev instanceof DecimalEncodedValue) {
            DecimalEncodedValue de = (DecimalEncodedValue) ev;
            double min = de.getMinStorableDecimal();
            double max = de.getMaxOrMaxStorableDecimal();
            if (Double.isInfinite(max)) max = 150;
            max = Math.min(max, 150);
            double v1 = min + rnd.nextDouble() * (max - min);
            if (de.isStoreTwoDirections()) edge.set(de, v1, min + rnd.nextDouble() * (max - min));
            else edge.set(de, v1);
        } else if (ev instanceof IntEncodedValue) {
            IntEncodedValue ie = (IntEncodedValue) ev;
            int min = Math.max(ie.getMinStorableInt(), -100);
            int max = Math.min(ie.getMaxOrMaxStorableInt(), 100);
            int v1 = min + rnd.nextInt(max - min + 1);
            if (ie.isStoreTwoDirections()) edge.set(ie, v1, min + rnd.nextInt(max - min + 1));
            else edge.set(ie, v1);
        }
    }

    // ------------------------------------------------------------------
    // corpus part 1: all bundled custom models
    // ------------------------------------------------------------------

    @Test
    public void bundledModels() {
        for (String file : MODEL_FILES) {
            CustomModel model = normalized(GHUtility.loadCustomModelFromJar(file));
            assertModelParity("bundled " + file, model, Boolean.TRUE);
        }
    }

    // ------------------------------------------------------------------
    // corpus part 2: handcrafted models incl. models from CustomWeightingTest/CustomModelParserTest
    // ------------------------------------------------------------------

    @Test
    public void handcraftedModels() {
        List<CustomModel> models = new ArrayList<>();
        models.add(base());
        models.add(base().setDistanceInfluence(70d));
        // CustomModelParserTest/CustomWeightingTest shapes
        models.add(base()
                .addToPriority(If("road_class == PRIMARY", MULTIPLY, "0.5"))
                .addToPriority(ElseIf("road_class == SECONDARY", MULTIPLY, "0.7"))
                .addToPriority(Else(MULTIPLY, "0.9"))
                .addToPriority(If("road_environment != FERRY", MULTIPLY, "0.8")));
        models.add(base()
                .addToPriority(If("country == USA", MULTIPLY, "0.5"))
                .addToPriority(If("country == USA && state == US_AK", MULTIPLY, "0.6"))
                .addToPriority(If("country == DEU", MULTIPLY, "0.8")));
        models.add(base()
                .addToPriority(If("(road_class == PRIMARY || car_access == true) && car_average_speed > 50", MULTIPLY, "0.9")));
        models.add(base()
                .addToPriority(If("road_class == PRIMARY", MULTIPLY, "0.9"))
                .addToSpeed(If("road_class == PRIMARY", MULTIPLY, "0.8"))
                .addToSpeed(If("road_class != PRIMARY", LIMIT, "50")));
        models.add(base().addToSpeed(If("true", LIMIT, "max_speed * 1.1")));
        models.add(base()
                .addToSpeed(If("max_speed > 40", List.of(If("true", LIMIT, "40"), If("roundabout", MULTIPLY, "0.5")))));
        models.add(base()
                .addToSpeed(If("max_speed > 30", List.of(
                        If("road_class == PRIMARY", MULTIPLY, "0.9"),
                        ElseIf("road_class == SECONDARY", MULTIPLY, "0.8"),
                        Else(MULTIPLY, "0.7")))));
        models.add(base().addToPriority(If("backward_car_access != car_access", MULTIPLY, "0.5")));
        models.add(base().addToPriority(If("roundabout", MULTIPLY, "0.5"))); // bare boolean encoded value
        models.add(base()
                .addToSpeed(If("road_environment == FERRY", MULTIPLY, "0.1"))
                .addToPriority(If("!car_access", MULTIPLY, "0")));
        // speed 'add' operation
        models.add(base().addToSpeed(If("car_access", ADD, "10")));
        // unconditional priority via encoded value (exercises the calcMaxPriority special case)
        models.add(base().addToPriority(If("true", MULTIPLY, "foot_priority")));
        // turn penalties
        models.add(base().addToTurnPenalty(If("prev_road_class != PRIMARY && road_class == PRIMARY", ADD, "100")));
        models.add(base().addToTurnPenalty(If("!prev_car_access || !car_access", ADD, "100")));
        models.add(base().addToTurnPenalty(If("prev_street_name.equals(street_name)", ADD, "100")));
        models.add(base().addToTurnPenalty(If("street_name.contains(\"Main\")", ADD, "37.5")));
        models.add(base().addToTurnPenalty(If("is_softblocked_at_entry", ADD, "2000")));
        models.add(base()
                .addToTurnPenalty(If("change_angle > -25 && change_angle < 25", ADD, "0"))
                .addToTurnPenalty(ElseIf("change_angle >= 25 && change_angle < 80", ADD, "3"))
                .addToTurnPenalty(Else(ADD, "Infinity")));
        models.add(base().addToTurnPenalty(If("prev_max_speed > 50", ADD, "car_average_speed * 2")));
        models.add(base().addToTurnPenalty(If("prev_country == country", ADD, "5")));
        for (CustomModel m : models)
            assertModelParity("handcrafted", m, Boolean.TRUE);
    }

    // ------------------------------------------------------------------
    // corpus part 3: typed-evaluation corners with expected verdicts — the categories
    // Janino only rejects at compile time must be rejected at composition time
    // ------------------------------------------------------------------

    static final String[] ACCEPTED_EDGE_CONDITIONS = {
            "road_class == PRIMARY", "toll == NO", "hazmat != NO", "max_speed > 50", "!car_access",
            "backward_car_access != car_access", "car_average_speed > 40 && road_class != MOTORWAY",
            "Math.sqrt(max_speed) > 7.5", "road_class.ordinal() > 5", "country == DEU", "state == US_AK",
            "surface == COBBLESTONE || surface == GRASS", "max_weight_except == MISSING",
            "country.isRightHandTraffic()", "max_weight < 18 && max_weight_except == MISSING",
            // int vs double arithmetic in conditions; the == cases flip verdicts if the
            // division were computed in double instead of Java's truncating int division
            "hike_rating / 2 >= 1", "hike_rating / 2.0 >= 1", "hike_rating / 2 == 1",
            "(hike_rating + 1) / 2 == 1", "hike_rating * 3 / 2 == 4", "hike_rating / 2L == 1",
            "mtb_rating % 2 == 1", "max_speed % 2.0 < 1",
            "(hike_rating + mtb_rating) * 2 > 5", "(hike_rating << 1) >= 4", "hike_rating >> 1 == 1",
            "hike_rating >>> 1 == 1", "(hike_rating & 1) == 0", "(hike_rating | mtb_rating) > 2",
            "(hike_rating ^ mtb_rating) == 1", "Math.abs(hike_rating - 3) >= 1", "hike_rating * 2 == mtb_rating * 3",
            // boolean operators
            "car_access & roundabout", "car_access | roundabout", "car_access ^ roundabout",
            "car_access & hike_rating == 1", "max_speed == 30 == car_access",
            "road_environment == FERRY == car_access",
            // literal typing
            "'0' < max_speed", "0x1F > hike_rating", "010 > hike_rating", "0b101 > hike_rating",
            "1_00 > max_speed", "3L > max_speed", "2f > max_speed", "1e2 > max_speed",
            ".5 < max_speed", "5. < max_speed",
            // enums, equals, null
            "road_class == road_class", "road_class != backward_road_class", "toll == null", "toll != null",
            "road_class.equals(road_class)", "road_class.equals(toll)",
            "country == DEU && country.equals(road_class)", "toll.ordinal() >= 1",
            // Math/unary
            "Math.abs(average_slope) > 2", "-average_slope > 1", "- -max_speed > 0", "average_slope < -0.5",
            "curvature >= 0.98", "true", "false"};

    static final String[] REJECTED_EDGE_CONDITIONS = {
            // shifts/bitwise on non-integrals
            "max_speed >> 2 == 1", "max_speed << 1 > 2", "(max_speed & 3) == 1", "car_access & max_speed",
            // boolean typing
            "!max_speed", "!5", "max_speed", "5", "hike_rating", "road_class",
            "max_speed && car_access", "car_access && 1", "car_access + roundabout > 1",
            // incomparable types
            "road_class == 2", "2 == road_class", "road_class == road_environment", "road_class == car_access",
            "car_access == 1", "max_speed == null", "null == car_access",
            // methods
            "Math.abs(road_class) > 0", "Math.sqrt(car_access) > 0", "max_speed.equals(max_speed)",
            "country.equals(road_class)", "state.isRightHandTraffic()", "max_speed.ordinal() > 0",
            "road_class.ordinal(1) > 0", "car_access.ordinal() == 0"};

    static final String[] ACCEPTED_TURN_CONDITIONS = {
            "change_angle > 25", "change_angle <= -25 && change_angle > -80",
            "prev_road_class != road_class", "prev_road_class == PRIMARY",
            "prev_street_name.equals(street_name)", "!street_name.equals(prev_street_name)",
            "street_name.contains(\"Main\")", "street_name.contains(prev_street_name)",
            "!prev_car_access || !car_access", "is_softblocked_at_entry",
            "street_name == \"Main St\"", "street_name == null", "street_name != null",
            "street_name == prev_street_name", "street_name.equals(toll)",
            "prev_max_speed > 50", "prev_country == country", "car_access"};

    static final String[] REJECTED_TURN_CONDITIONS = {
            "street_name == 1", "street_name.contains(5)", "street_name.contains(road_class)",
            "street_name < \"x\"", "street_name == road_class", "change_angle == NO"};

    /** verdict must match between the back-ends, but the Janino verdict is not pinned here (boxing corners) */
    static final String[] PARITY_ONLY_TURN_CONDITIONS = {
            "prev_street_name.equals(5)", "street_name.equals(null)"};

    @Test
    public void conditionTypingParity() {
        for (String condition : ACCEPTED_EDGE_CONDITIONS)
            assertModelParity("condition <" + condition + ">", withPriorityCondition(condition), Boolean.TRUE);
        for (String condition : REJECTED_EDGE_CONDITIONS)
            assertModelParity("condition <" + condition + ">", withPriorityCondition(condition), Boolean.FALSE);
        for (String condition : ACCEPTED_TURN_CONDITIONS)
            assertModelParity("turn condition <" + condition + ">", withTurnCondition(condition), Boolean.TRUE);
        for (String condition : REJECTED_TURN_CONDITIONS)
            assertModelParity("turn condition <" + condition + ">", withTurnCondition(condition), Boolean.FALSE);
        for (String condition : PARITY_ONLY_TURN_CONDITIONS)
            assertModelParity("turn condition <" + condition + ">", withTurnCondition(condition), null);
    }

    @Test
    public void valueTypingParity() {
        String[] acceptedValues = {"hike_rating * 3", "hike_rating", "max_speed + 5", "Math.sqrt(max_speed)",
                "0x10", "10f", "1_0", "2e1", "27.5", ".1", "- -30", "max_speed * 0.9"};
        for (String value : acceptedValues) {
            CustomModel m = base();
            m.addToSpeed(If("car_access", LIMIT, value));
            assertModelParity("value <" + value + ">", m, Boolean.TRUE);
        }
        String[] rejectedValues = {"max_speed * car_average_speed", "road_class", "car_access", "1/max_speed",
                "-0.5", "-max_speed", "max_speed - 500", "Infinity", "unknown"};
        for (String value : rejectedValues) {
            CustomModel m = base();
            m.addToSpeed(If("car_access", LIMIT, value));
            assertModelParity("value <" + value + ">", m, Boolean.FALSE);
        }
    }

    // ------------------------------------------------------------------
    // corpus part 4: areas
    // ------------------------------------------------------------------

    @Test
    public void areaModels() {
        CustomModel m1 = base();
        m1.setAreas(areas());
        m1.addToPriority(If("in_area_a", MULTIPLY, "0.5"));
        m1.addToSpeed(If("!in_area_b", LIMIT, "30"));
        assertModelParity("areas", m1, Boolean.TRUE);

        CustomModel m2 = base();
        m2.setAreas(areas());
        m2.addToSpeed(If("in_area_a && road_class == PRIMARY || in_area_b", MULTIPLY, "0.7"));
        assertModelParity("areas combined", m2, Boolean.TRUE);

        CustomModel m3 = base();
        m3.setAreas(areas());
        m3.addToPriority(If("in_area_missing", MULTIPLY, "0.5"));
        assertModelParity("missing area", m3, Boolean.FALSE);

        CustomModel m4 = base();
        m4.setAreas(areas());
        m4.addToPriority(If("in_area_a == true", MULTIPLY, "0.5"));
        assertModelParity("area compared with boolean", m4, Boolean.TRUE);
    }

    static JsonFeatureCollection areas() {
        JsonFeatureCollection collection = new JsonFeatureCollection();
        collection.getFeatures().add(area("area_a", 11.2, 48.0, 11.55, 48.15));
        collection.getFeatures().add(area("area_b", 11.5, 48.1, 11.9, 48.3));
        return collection;
    }

    static JsonFeature area(String id, double minX, double minY, double maxX, double maxY) {
        Coordinate[] coordinates = new Coordinate[]{
                new Coordinate(minX, minY), new Coordinate(maxX, minY), new Coordinate(maxX, maxY),
                new Coordinate(minX, maxY), new Coordinate(minX, minY)};
        return new JsonFeature(id, "Feature", null, new GeometryFactory().createPolygon(coordinates), new HashMap<>());
    }

    // ------------------------------------------------------------------
    // corpus part 5: distance_influence / heading_penalty defaulting
    // ------------------------------------------------------------------

    @Test
    public void headingPenaltyAndDistanceInfluenceDefaults() {
        List<CustomModel> models = List.of(
                base(), // both omitted -> defaults
                base().setDistanceInfluence(90d),
                base().setHeadingPenalty(100d),
                base().setDistanceInfluence(30d).setHeadingPenalty(250d));
        for (CustomModel model : models) {
            CustomWeighting.Parameters pj = JaninoBackend.INSTANCE.createParameters(model, em);
            CustomWeighting.Parameters pc = ClosureBackend.INSTANCE.createParameters(model, em);
            assertEquals(pj.getDistanceInfluence(), pc.getDistanceInfluence());
            assertEquals(pj.getHeadingPenaltySeconds(), pc.getHeadingPenaltySeconds());

            Weighting wj = new CustomWeighting(NO_TURN_COST_PROVIDER, pj);
            Weighting wc = new CustomWeighting(NO_TURN_COST_PROVIDER, pc);
            EdgeIteratorState edge = graph.getEdgeIteratorState(0, Integer.MIN_VALUE);
            VirtualEdgeIteratorState virtualEdge = new VirtualEdgeIteratorState(edge.getEdgeKey(), 99,
                    edge.getBaseNode(), edge.getAdjNode(), edge.getDistance(), edge.getFlags(),
                    edge.getKeyValues(), edge.fetchWayGeometry(FetchMode.PILLAR_ONLY), false);
            virtualEdge.setUnfavored(true); // triggers the heading penalty
            assertEquals(wj.calcEdgeWeight(virtualEdge, false), wc.calcEdgeWeight(virtualEdge, false));
            assertEquals(wj.calcEdgeWeight(virtualEdge, true), wc.calcEdgeWeight(virtualEdge, true));
            assertEquals(wj.calcEdgeMillis(virtualEdge, false), wc.calcEdgeMillis(virtualEdge, false));
        }
    }

    // ------------------------------------------------------------------
    // corpus part 6: randomized models (fixed seed)
    // ------------------------------------------------------------------

    static final String[] RANDOM_EDGE_VALUES = {"0.9", "0.5", "1", "0", "35", "car_average_speed",
            "max_speed * 0.9", "0.1 * max_speed", "Math.sqrt(max_speed)", "hike_rating * 3 + 1",
            "max_speed + 10", "27.5"};
    static final String[] RANDOM_PRIORITY_VALUES = {"0", "0.3", "0.5", "0.9", "1", "1.5", "foot_priority"};
    static final String[] RANDOM_TURN_VALUES = {"0", "3", "42.5", "100", "Infinity", "car_average_speed * 2"};

    @Test
    public void randomModels() {
        Random rnd = new Random(MODEL_SEED);
        for (int i = 0; i < RANDOM_MODELS; i++) {
            CustomModel model = randomModel(rnd);
            assertModelParity("random model " + i, model, null);
        }
    }

    static CustomModel randomModel(Random rnd) {
        CustomModel m = new CustomModel();
        if (rnd.nextBoolean()) m.setDistanceInfluence((double) rnd.nextInt(100));
        if (rnd.nextInt(4) == 0) m.setHeadingPenalty((double) rnd.nextInt(600));
        m.addToSpeed(If("true", LIMIT, rnd.nextBoolean() ? "car_average_speed" : String.valueOf(30 + rnd.nextInt(100))));
        int speedGroups = rnd.nextInt(3);
        for (int i = 0; i < speedGroups; i++)
            addRandomGroup(m::addToSpeed, RANDOM_EDGE_VALUES, rnd, true);
        int priorityGroups = rnd.nextInt(3);
        for (int i = 0; i < priorityGroups; i++)
            addRandomGroup(m::addToPriority, RANDOM_PRIORITY_VALUES, rnd, false);
        if (rnd.nextInt(3) == 0) {
            m.addToTurnPenalty(If(pick(ACCEPTED_TURN_CONDITIONS, rnd), ADD, pick(RANDOM_TURN_VALUES, rnd)));
            if (rnd.nextBoolean())
                m.addToTurnPenalty(ElseIf(pick(ACCEPTED_TURN_CONDITIONS, rnd), ADD, pick(RANDOM_TURN_VALUES, rnd)));
            if (rnd.nextBoolean())
                m.addToTurnPenalty(Else(ADD, pick(RANDOM_TURN_VALUES, rnd)));
        }
        return m;
    }

    static void addRandomGroup(Consumer<Statement> add, String[] values, Random rnd, boolean speed) {
        String condition = randomCondition(rnd);
        Statement.Op op = randomOp(rnd, speed);
        if (rnd.nextInt(6) == 0) {
            add.accept(If(condition, List.of(If("true", randomOp(rnd, speed), pick(values, rnd)))));
            return;
        }
        add.accept(If(condition, op, pick(values, rnd)));
        if (rnd.nextInt(3) == 0)
            add.accept(ElseIf(randomCondition(rnd), randomOp(rnd, speed), pick(values, rnd)));
        if (rnd.nextInt(3) == 0)
            add.accept(Else(randomOp(rnd, speed), pick(values, rnd)));
    }

    static Statement.Op randomOp(Random rnd, boolean speed) {
        if (speed && rnd.nextInt(5) == 0) return ADD;
        return rnd.nextBoolean() ? LIMIT : MULTIPLY;
    }

    static String randomCondition(Random rnd) {
        String c = pick(ACCEPTED_EDGE_CONDITIONS, rnd);
        if (rnd.nextInt(3) == 0)
            c = "(" + c + ") " + (rnd.nextBoolean() ? "&&" : "||") + " (" + pick(ACCEPTED_EDGE_CONDITIONS, rnd) + ")";
        if (rnd.nextInt(6) == 0)
            c = "!(" + c + ")";
        return c;
    }

    static String pick(String[] pool, Random rnd) {
        return pool[rnd.nextInt(pool.length)];
    }

    // ------------------------------------------------------------------
    // corpus part 7: the backend seam — closure backend selected only via
    // CustomWeightingBackends.default (restored in @AfterEach)
    // ------------------------------------------------------------------

    @Test
    public void closureBackendSelectableViaDefault() {
        CustomWeightingBackends.setDefault(ClosureBackend.INSTANCE);
        CustomModel model = base().addToPriority(If("road_class == PRIMARY", MULTIPLY, "0.5"));
        Weighting viaSeam = CustomModelParser.createWeighting(em, NO_TURN_COST_PROVIDER, model);
        Weighting janino = new CustomWeighting(NO_TURN_COST_PROVIDER, JaninoBackend.INSTANCE.createParameters(model, em));
        AllEdgesIterator iter = graph.getAllEdges();
        while (iter.next()) {
            assertEquals(janino.calcEdgeWeight(iter, false), viaSeam.calcEdgeWeight(iter, false));
            assertEquals(janino.calcEdgeWeight(iter, true), viaSeam.calcEdgeWeight(iter, true));
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    static CustomModel base() {
        CustomModel m = new CustomModel();
        m.addToSpeed(If("true", LIMIT, "car_average_speed"));
        return m;
    }

    static CustomModel withPriorityCondition(String condition) {
        return base().addToPriority(If(condition, MULTIPLY, "0.5"));
    }

    static CustomModel withTurnCondition(String condition) {
        return base().addToTurnPenalty(If(condition, ADD, "42"));
    }

    /** Prepends the base speed limit so partial models (e.g. elevation-only) become structurally valid. */
    static CustomModel normalized(CustomModel model) {
        CustomModel n = new CustomModel();
        n.addToSpeed(If("true", LIMIT, "100"));
        model.getSpeed().forEach(n::addToSpeed);
        model.getPriority().forEach(n::addToPriority);
        model.getTurnPenalty().forEach(n::addToTurnPenalty);
        if (model.getAreas() != null) n.setAreas(model.getAreas());
        return n;
    }

    static CustomWeighting.Parameters tryCreate(CustomWeightingBackend backend, CustomModel model) {
        try {
            return backend.createParameters(model, em);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Core assertion: identical accept/reject verdicts and, if accepted, bit-identical
     * weights/millis/min-weight/turn penalties for every edge and edge pair.
     *
     * @param expectedAccept the expected Janino verdict, or null if only parity is asserted
     */
    static void assertModelParity(String info, CustomModel model, Boolean expectedAccept) {
        CustomWeighting.Parameters pj = tryCreate(JaninoBackend.INSTANCE, model);
        CustomWeighting.Parameters pc = tryCreate(ClosureBackend.INSTANCE, model);
        assertEquals(pj != null, pc != null,
                () -> info + ": acceptance differs, janino=" + (pj != null) + " closure=" + (pc != null) + " model " + model);
        if (expectedAccept != null)
            assertEquals(expectedAccept, pj != null, () -> info + ": unexpected janino verdict for model " + model);
        if (pj == null)
            return;

        Weighting wj = new CustomWeighting(NO_TURN_COST_PROVIDER, pj);
        Weighting wc = new CustomWeighting(NO_TURN_COST_PROVIDER, pc);

        // calcMinWeightPerDistance (exercises the max speed/priority calculators): both throw or exact equal
        Double minJ = tryMinWeight(wj), minC = tryMinWeight(wc);
        assertEquals(minJ == null, minC == null, () -> info + ": min weight throw-behavior differs for model " + model);
        if (minJ != null)
            assertEquals(minJ, minC, () -> info + ": calcMinWeightPerDistance differs for model " + model);

        AllEdgesIterator iter = graph.getAllEdges();
        while (iter.next()) {
            for (boolean reverse : new boolean[]{false, true}) {
                double weightJ = wj.calcEdgeWeight(iter, reverse);
                double weightC = wc.calcEdgeWeight(iter, reverse);
                int edgeId = iter.getEdge();
                boolean rev = reverse;
                assertEquals(weightJ, weightC,
                        () -> info + ": weight differs at edge " + edgeId + " reverse=" + rev + " model " + model);
                assertEquals(wj.calcEdgeMillis(iter, reverse), wc.calcEdgeMillis(iter, reverse),
                        () -> info + ": millis differ at edge " + edgeId + " reverse=" + rev + " model " + model);
            }
        }

        if (!model.getTurnPenalty().isEmpty()) {
            CustomWeighting.TurnPenaltyMapping tj = pj.getTurnPenaltyMapping();
            CustomWeighting.TurnPenaltyMapping tc = pc.getTurnPenaltyMapping();
            EdgeIntAccess edgeIntAccess = graph.getEdgeAccess();
            EdgeExplorer explorer = graph.createEdgeExplorer();
            for (int node = 0; node < NODES; node++) {
                List<Integer> edgeIds = new ArrayList<>();
                EdgeIterator it = explorer.setBaseNode(node);
                while (it.next()) edgeIds.add(it.getEdge());
                for (int inEdge : edgeIds) {
                    for (int outEdge : edgeIds) {
                        double penaltyJ = tj.get(graph, edgeIntAccess, inEdge, node, outEdge);
                        double penaltyC = tc.get(graph, edgeIntAccess, inEdge, node, outEdge);
                        int viaNode = node;
                        assertEquals(penaltyJ, penaltyC, () -> info + ": turn penalty differs at "
                                + inEdge + "->" + viaNode + "->" + outEdge + " model " + model);
                    }
                }
            }
        }
    }

    static Double tryMinWeight(Weighting w) {
        try {
            return w.calcMinWeightPerDistance();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
