package com.graphhopper.routing.weighting.custom;

import com.bedatadriven.jackson.datatype.jts.JtsModule;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.config.Profile;
import com.graphhopper.json.Statement;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.DefaultWeightingFactory;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.querygraph.VirtualEdgeIteratorState;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.DefaultTurnCostProvider;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static com.graphhopper.json.Statement.*;
import static com.graphhopper.json.Statement.Op.ADD;
import static com.graphhopper.json.Statement.Op.LIMIT;
import static com.graphhopper.json.Statement.Op.MULTIPLY;
import static com.graphhopper.routing.ev.RoadClass.*;
import static com.graphhopper.routing.weighting.TurnCostProvider.NO_TURN_COST_PROVIDER;
import static com.graphhopper.routing.weighting.Weighting.roundWeight;
import static com.graphhopper.util.GHUtility.getEdge;
import static org.junit.jupiter.api.Assertions.*;

class CustomWeightingTest {
    BaseGraph graph;
    DecimalEncodedValue avSpeedEnc;
    BooleanEncodedValue accessEnc;
    DecimalEncodedValue maxSpeedEnc;
    EnumEncodedValue<RoadClass> roadClassEnc;
    EncodingManager encodingManager;
    BooleanEncodedValue turnRestrictionEnc = TurnRestriction.create("car");

    @BeforeEach
    public void setup() {
        accessEnc = VehicleAccess.create("car");
        avSpeedEnc = VehicleSpeed.create("car", 5, 5, true);
        encodingManager = new EncodingManager.Builder().add(accessEnc).add(avSpeedEnc)
                .add(Toll.create())
                .add(Hazmat.create())
                .add(RouteNetwork.create(BikeNetwork.KEY))
                .add(MaxSpeed.create())
                .add(RoadClass.create())
                .add(RoadClassLink.create())
                .add(OSMWayID.create())
                .add(new DecimalEncodedValueImpl("my_priority", 4, 0.05, false))
                .addTurnCostEncodedValue(turnRestrictionEnc)
                .build();
        maxSpeedEnc = encodingManager.getDecimalEncodedValue(MaxSpeed.KEY);
        roadClassEnc = encodingManager.getEnumEncodedValue(KEY, RoadClass.class);
        graph = new BaseGraph.Builder(encodingManager).create();
    }

    private void setTurnRestriction(Graph graph, int from, int via, int to) {
        graph.getTurnCostStorage().set(turnRestrictionEnc, getEdge(graph, from, via).getEdge(), via, getEdge(graph, via, to).getEdge(), true);
    }

    private CustomModel createSpeedCustomModel(DecimalEncodedValue speedEnc) {
        CustomModel customModel = new CustomModel();
        customModel.addToSpeed(If("true", LIMIT, speedEnc.getName()));
        return customModel;
    }

    private Weighting createWeighting(CustomModel vehicleModel) {
        return CustomModelParser.createWeighting(encodingManager, NO_TURN_COST_PROVIDER, vehicleModel);
    }

    @Test
    public void testParameters() {
        // 25km/h -> 1440s per km, 100km/h -> 360s per km
        EdgeIteratorState slow = graph.edge(0, 1).set(avSpeedEnc, 25, 25).setDistance(1000);
        EdgeIteratorState fast = graph.edge(2, 3).set(avSpeedEnc, 100, 100).setDistance(1000);

        CustomModel customModel = createSpeedCustomModel(avSpeedEnc).setDistanceInfluence(0d).
                setParameter("speed_threshold", 50).setParameter("slow_factor", 0.5);
        // parameters in a condition and as value expression
        customModel.addToSpeed(If("car_average_speed < p_speed_threshold", MULTIPLY, "p_slow_factor"));
        Weighting weighting = createWeighting(customModel);
        assertEquals(2 * 1440, weighting.calcEdgeWeight(slow, false));
        assertEquals(360, weighting.calcEdgeWeight(fast, false));

        // the same model with different values shares the compiled class, only init changes
        Weighting changed = createWeighting(new CustomModel(customModel).
                setParameter("speed_threshold", 120).setParameter("slow_factor", 0.25));
        assertEquals(4 * 1440, changed.calcEdgeWeight(slow, false));
        assertEquals(4 * 360, changed.calcEdgeWeight(fast, false));

        // boolean parameters in conditions
        CustomModel boolModel = createSpeedCustomModel(avSpeedEnc).setDistanceInfluence(0d).setParameter("slow_mode", true);
        boolModel.addToSpeed(If("p_slow_mode", MULTIPLY, "0.5"));
        assertEquals(2 * 1440, createWeighting(boolModel).calcEdgeWeight(slow, false));
        assertEquals(1440, createWeighting(new CustomModel(boolModel).setParameter("slow_mode", false)).calcEdgeWeight(slow, false));

        // a parameter that makes a multiply_by negative is rejected
        CustomModel negModel = createSpeedCustomModel(avSpeedEnc).setParameter("slow_factor", -0.5);
        negModel.addToSpeed(If("true", MULTIPLY, "p_slow_factor"));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> createWeighting(negModel));
        assertTrue(ex.getMessage().contains("negative"), ex.getMessage());

        // a parameter must be referenced with the p_ prefix
        CustomModel unprefixedModel = createSpeedCustomModel(avSpeedEnc).setParameter("slow_factor", 0.5);
        unprefixedModel.addToSpeed(If("true", MULTIPLY, "slow_factor"));
        ex = assertThrows(IllegalArgumentException.class, () -> createWeighting(unprefixedModel));
        assertTrue(ex.getMessage().contains("'slow_factor' not available"), ex.getMessage());

        // a negative parameter value directly after '-' must still compile ("50--5.0" would not)
        CustomModel negCorr = createSpeedCustomModel(avSpeedEnc).setDistanceInfluence(0d).setParameter("corr", -5.0);
        negCorr.addToSpeed(If("true", LIMIT, "50-p_corr"));
        assertEquals(1440, createWeighting(negCorr).calcEdgeWeight(slow, false));

        // a value expression can use only a single parameter and only once, so that validating the
        // range endpoints covers all values in between (unlike e.g. p_aa - p_bb at aa==min and bb==max)
        CustomModel twoParams = createSpeedCustomModel(avSpeedEnc).setParameter("aa", 0.9).setParameter("bb", 0.0);
        twoParams.addToSpeed(If("true", MULTIPLY, "p_aa - p_bb"));
        ex = assertThrows(IllegalArgumentException.class, () -> createWeighting(twoParams));
        assertTrue(ex.getMessage().contains("single parameter"), ex.getMessage());
        CustomModel repeated = createSpeedCustomModel(avSpeedEnc).setParameter("xx", 0.5);
        repeated.addToSpeed(If("true", MULTIPLY, "p_xx * p_xx"));
        ex = assertThrows(IllegalArgumentException.class, () -> createWeighting(repeated));
        assertTrue(ex.getMessage().contains("more than once"), ex.getMessage());

        // invalid names and values are rejected
        ex = assertThrows(IllegalArgumentException.class, () -> createWeighting(
                createSpeedCustomModel(avSpeedEnc).setParameter("myValue", 30)));
        assertTrue(ex.getMessage().contains("invalid name"), ex.getMessage());
        ex = assertThrows(IllegalArgumentException.class, () -> createWeighting(
                createSpeedCustomModel(avSpeedEnc).setParameter("nan", Double.NaN)));
        assertTrue(ex.getMessage().contains("finite"), ex.getMessage());
    }

    @Test
    public void testSetParameters() {
        IntEncodedValue wayIdEnc = encodingManager.getIntEncodedValue(OSMWayID.KEY);
        EdgeIteratorState closed = graph.edge(0, 1).set(avSpeedEnc, 25, 25).setDistance(1000).set(wayIdEnc, 23323061);
        EdgeIteratorState open = graph.edge(1, 2).set(avSpeedEnc, 25, 25).setDistance(1000).set(wayIdEnc, 5023188);

        CustomModel customModel = createSpeedCustomModel(avSpeedEnc).setDistanceInfluence(0d).setParameter("closed_ways", List.of(23323061, 1));
        customModel.addToPriority(If("p_closed_ways.contains(osm_way_id)", MULTIPLY, "0"));
        Weighting weighting = createWeighting(customModel);
        assertEquals(Double.POSITIVE_INFINITY, weighting.calcEdgeWeight(closed, false));
        assertEquals(1440, weighting.calcEdgeWeight(open, false));

        // like for other parameters a different set shares the compiled class, only init changes
        Weighting changed = createWeighting(new CustomModel(customModel).setParameter("closed_ways", List.of(5023188)));
        assertEquals(1440, changed.calcEdgeWeight(closed, false));
        assertEquals(Double.POSITIVE_INFINITY, changed.calcEdgeWeight(open, false));

        // a set is only usable in conditions
        CustomModel valueModel = createSpeedCustomModel(avSpeedEnc).setParameter("closed_ways", List.of(1));
        valueModel.addToSpeed(If("true", MULTIPLY, "p_closed_ways"));
        assertThrows(IllegalArgumentException.class, () -> createWeighting(valueModel));

        // an empty set excludes nothing and a negated or combined condition works too
        assertEquals(1440, createWeighting(new CustomModel(customModel).setParameter("closed_ways", List.of())).calcEdgeWeight(closed, false));
        CustomModel openModel = createSpeedCustomModel(avSpeedEnc).setDistanceInfluence(0d).setParameter("open_ways", List.of(5023188));
        openModel.addToPriority(If("!p_open_ways.contains(osm_way_id) && car_average_speed < 50", MULTIPLY, "0"));
        assertEquals(Double.POSITIVE_INFINITY, createWeighting(openModel).calcEdgeWeight(closed, false));
        assertEquals(1440, createWeighting(openModel).calcEdgeWeight(open, false));

        // a large set, next to a number parameter in a value expression
        CustomModel largeModel = createSpeedCustomModel(avSpeedEnc).setDistanceInfluence(0d).setParameter("factor", 0.5).
                setParameter("closed_ways", IntStream.range(23313062, 23333062).boxed().toList());
        largeModel.addToPriority(If("p_closed_ways.contains(osm_way_id)", MULTIPLY, "0"));
        largeModel.addToSpeed(If("true", MULTIPLY, "p_factor"));
        assertEquals(Double.POSITIVE_INFINITY, createWeighting(largeModel).calcEdgeWeight(closed, false));
        assertEquals(2 * 1440, createWeighting(largeModel).calcEdgeWeight(open, false));

        // only contains with an int argument, and contains only on a set
        for (String condition : List.of("p_closed_ways.add(osm_way_id)", "p_closed_ways.removeAll(osm_way_id)",
                "p_closed_ways.contains(road_class)", "p_closed_ways.contains(osm_way_id).equals(true)", "p_closed_ways", "p_factor.contains(osm_way_id)")) {
            CustomModel invalid = createSpeedCustomModel(avSpeedEnc).setParameter("closed_ways", List.of(1)).setParameter("factor", 1.0);
            invalid.addToPriority(If(condition, MULTIPLY, "0"));
            assertThrows(IllegalArgumentException.class, () -> createWeighting(invalid), condition);
        }
    }

    @Test
    public void testSetParameterWithOtherIntEncodedValues() {
        DecimalEncodedValue speedEnc = VehicleSpeed.create("car", 5, 5, false);
        IntEncodedValue lanesEnc = Lanes.create(), mtbEnc = MtbRating.create(), hikeEnc = HikeRating.create(), horseEnc = HorseRating.create();
        IntEncodedValue zoneEnc = new IntEncodedValueImpl("zone_id", 20, false);
        IntEncodedValue twoDirEnc = new IntEncodedValueImpl("lane_group", 4, true);
        IntEncodedValue offsetEnc = new IntEncodedValueImpl("offset", 5, -10, false, false);
        EncodingManager em = new EncodingManager.Builder().add(speedEnc).add(lanesEnc).add(mtbEnc).add(hikeEnc).add(horseEnc)
                .add(zoneEnc).add(twoDirEnc).add(offsetEnc).add(MaxSpeed.create()).add(Roundabout.create()).build();
        BaseGraph g = new BaseGraph.Builder(em).create();
        EdgeIteratorState edge = g.edge(0, 1).set(speedEnc, 25).setDistance(1000).set(lanesEnc, 2).set(mtbEnc, 3).set(hikeEnc, 4)
                .set(horseEnc, 5).set(zoneEnc, 1_000_000).set(twoDirEnc, 6, 7).set(offsetEnc, -5);

        // the built-in int encoded values and custom ones like an id
        for (IntEncodedValue enc : List.of(lanesEnc, mtbEnc, hikeEnc, horseEnc, zoneEnc)) {
            CustomModel model = createSpeedCustomModel(speedEnc).setDistanceInfluence(0d).setParameter("values", List.of(edge.get(enc)));
            model.addToPriority(If("p_values.contains(" + enc.getName() + ")", MULTIPLY, "0"));
            assertEquals(Double.POSITIVE_INFINITY, CustomModelParser.createWeighting(em, NO_TURN_COST_PROVIDER, model).calcEdgeWeight(edge, false), enc.getName());
            model.setParameter("values", List.of(edge.get(enc) + 1));
            assertEquals(1440, CustomModelParser.createWeighting(em, NO_TURN_COST_PROVIDER, model).calcEdgeWeight(edge, false), enc.getName());
        }

        // like for == the value of the direction is used and backward_ gives the reverse direction
        CustomModel twoDirModel = createSpeedCustomModel(speedEnc).setDistanceInfluence(0d).setParameter("values", List.of(6));
        twoDirModel.addToPriority(If("p_values.contains(lane_group)", MULTIPLY, "0"));
        Weighting twoDir = CustomModelParser.createWeighting(em, NO_TURN_COST_PROVIDER, twoDirModel);
        assertEquals(Double.POSITIVE_INFINITY, twoDir.calcEdgeWeight(edge, false));
        assertEquals(1440, twoDir.calcEdgeWeight(edge, true));
        CustomModel backwardModel = createSpeedCustomModel(speedEnc).setDistanceInfluence(0d).setParameter("values", List.of(6));
        backwardModel.addToPriority(If("p_values.contains(backward_lane_group)", MULTIPLY, "0"));
        Weighting backward = CustomModelParser.createWeighting(em, NO_TURN_COST_PROVIDER, backwardModel);
        assertEquals(1440, backward.calcEdgeWeight(edge, false));
        assertEquals(Double.POSITIVE_INFINITY, backward.calcEdgeWeight(edge, true));

        // negative values work too
        CustomModel offsetModel = createSpeedCustomModel(speedEnc).setDistanceInfluence(0d).setParameter("values", List.of(-5));
        offsetModel.addToPriority(If("p_values.contains(offset)", MULTIPLY, "0"));
        assertEquals(Double.POSITIVE_INFINITY, CustomModelParser.createWeighting(em, NO_TURN_COST_PROVIDER, offsetModel).calcEdgeWeight(edge, false));
        offsetModel.setParameter("values", List.of(5));
        assertEquals(1440, CustomModelParser.createWeighting(em, NO_TURN_COST_PROVIDER, offsetModel).calcEdgeWeight(edge, false));

        // decimal and boolean encoded values are rejected
        for (String ev : List.of("max_speed", "roundabout")) {
            CustomModel invalid = createSpeedCustomModel(speedEnc).setParameter("values", List.of(1));
            invalid.addToPriority(If("p_values.contains(" + ev + ")", MULTIPLY, "0"));
            assertThrows(IllegalArgumentException.class, () -> CustomModelParser.createWeighting(em, NO_TURN_COST_PROVIDER, invalid), ev);
        }
    }

    @Test
    public void testSetParametersReuseCompiledClass() throws Exception {
        IntEncodedValue wayIdEnc = encodingManager.getIntEncodedValue(OSMWayID.KEY);
        EdgeIteratorState edge1 = graph.edge(0, 1).set(avSpeedEnc, 25, 25).setDistance(1000).set(wayIdEnc, 1);
        EdgeIteratorState edge2 = graph.edge(1, 2).set(avSpeedEnc, 25, 25).setDistance(1000).set(wayIdEnc, 2);
        // a priority statement that only this test uses, so its class key is not in the cache yet
        CustomModel model = createSpeedCustomModel(avSpeedEnc).setDistanceInfluence(0d).setParameter("reuse_ways", List.of(1));
        model.addToPriority(If("p_reuse_ways.contains(osm_way_id)", MULTIPLY, "0"));
        String key = model.createClassKey();

        Field cacheField = CustomModelParser.class.getDeclaredField("CACHE");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Class<?>> cache = (Map<String, Class<?>>) cacheField.get(null);
        Weighting weighting1 = createWeighting(model);
        Class<?> compiled = cache.get(key);
        assertNotNull(compiled);

        // models with other members, an empty or a large set have the same key and do not compile a new class
        Weighting weighting2 = createWeighting(new CustomModel(model).setParameter("reuse_ways", List.of(2)));
        Weighting empty = createWeighting(new CustomModel(model).setParameter("reuse_ways", List.of()));
        Weighting large = createWeighting(new CustomModel(model).setParameter("reuse_ways", IntStream.range(2, 10_000).boxed().toList()));
        assertSame(compiled, cache.get(key));

        // each weighting keeps its own members, also when used interleaved
        for (int i = 0; i < 2; i++) {
            assertEquals(Double.POSITIVE_INFINITY, weighting1.calcEdgeWeight(edge1, false));
            assertEquals(Double.POSITIVE_INFINITY, weighting2.calcEdgeWeight(edge2, false));
            assertEquals(1440, weighting1.calcEdgeWeight(edge2, false));
            assertEquals(1440, weighting2.calcEdgeWeight(edge1, false));
            assertEquals(1440, empty.calcEdgeWeight(edge1, false));
            assertEquals(Double.POSITIVE_INFINITY, large.calcEdgeWeight(edge2, false));
            assertEquals(1440, large.calcEdgeWeight(edge1, false));
        }
    }

    @Test
    public void testSetParametersInTurnPenalty() {
        BaseGraph turnGraph = new BaseGraph.Builder(encodingManager).withTurnCosts(true).create();
        IntEncodedValue wayIdEnc = encodingManager.getIntEncodedValue(OSMWayID.KEY);
        EdgeIteratorState edge01 = turnGraph.edge(0, 1).set(avSpeedEnc, 60, 60).setDistance(100).set(wayIdEnc, 10);
        EdgeIteratorState edge12 = turnGraph.edge(1, 2).set(avSpeedEnc, 60, 60).setDistance(100).set(wayIdEnc, 20);
        EdgeIteratorState edge23 = turnGraph.edge(2, 3).set(avSpeedEnc, 60, 60).setDistance(100).set(wayIdEnc, 30);
        CustomModel model = createSpeedCustomModel(avSpeedEnc).setParameter("to_ways", List.of(20)).setParameter("from_ways", List.of(10));
        model.addToTurnPenalty(If("p_to_ways.contains(osm_way_id)", ADD, "100"));
        model.addToTurnPenalty(If("p_from_ways.contains(prev_osm_way_id)", ADD, "1000"));

        // osm_way_id is read from the outgoing edge and prev_osm_way_id from the incoming one
        Weighting weighting = createTurnWeighting(encodingManager, turnGraph, model);
        assertEquals(10 * 1100, weighting.calcTurnWeight(edge01.getEdge(), 1, edge12.getEdge()));
        assertEquals(0, weighting.calcTurnWeight(edge12.getEdge(), 2, edge23.getEdge()));
        // the other members are used without a new class
        weighting = createTurnWeighting(encodingManager, turnGraph, new CustomModel(model).setParameter("to_ways", List.of(30)).setParameter("from_ways", List.of()));
        assertEquals(0, weighting.calcTurnWeight(edge01.getEdge(), 1, edge12.getEdge()));
        assertEquals(10 * 100, weighting.calcTurnWeight(edge12.getEdge(), 2, edge23.getEdge()));
    }

    @Test
    public void testSetParametersInTurnPenaltyWithTwoDirections() {
        DecimalEncodedValue speedEnc = VehicleSpeed.create("car", 5, 5, false);
        IntEncodedValue laneGroupEnc = new IntEncodedValueImpl("lane_group", 4, true);
        BooleanEncodedValue restrictionEnc = TurnRestriction.create("car");
        EncodingManager em = new EncodingManager.Builder().add(speedEnc).add(laneGroupEnc).addTurnCostEncodedValue(restrictionEnc).build();
        BaseGraph turnGraph = new BaseGraph.Builder(em).withTurnCosts(true).create();
        EdgeIteratorState edge01 = turnGraph.edge(0, 1).set(speedEnc, 60).setDistance(100).set(laneGroupEnc, 1, 2);
        // the value is 6 in the direction 1->2 and 7 in the direction 2->1
        EdgeIteratorState edge12 = turnGraph.edge(1, 2).set(speedEnc, 60).setDistance(100).set(laneGroupEnc, 6, 7);

        // like for == the value of the travel direction of the outgoing edge is used, 1->2
        CustomModel model = createSpeedCustomModel(speedEnc).setParameter("groups", List.of(6));
        model.addToTurnPenalty(If("p_groups.contains(lane_group)", ADD, "100"));
        assertEquals(10 * 100, createTurnWeighting(em, turnGraph, model).calcTurnWeight(edge01.getEdge(), 1, edge12.getEdge()));
        model.setParameter("groups", List.of(7));
        assertEquals(0, createTurnWeighting(em, turnGraph, model).calcTurnWeight(edge01.getEdge(), 1, edge12.getEdge()));

        // and of the incoming edge, 2->1
        CustomModel prevModel = createSpeedCustomModel(speedEnc).setParameter("groups", List.of(7));
        prevModel.addToTurnPenalty(If("p_groups.contains(prev_lane_group)", ADD, "100"));
        assertEquals(10 * 100, createTurnWeighting(em, turnGraph, prevModel).calcTurnWeight(edge12.getEdge(), 1, edge01.getEdge()));
        prevModel.setParameter("groups", List.of(6));
        assertEquals(0, createTurnWeighting(em, turnGraph, prevModel).calcTurnWeight(edge12.getEdge(), 1, edge01.getEdge()));
    }

    private static Weighting createTurnWeighting(EncodingManager em, BaseGraph graph, CustomModel model) {
        CustomWeighting.Config config = CustomModelParser.createWeightingConfig(model, em);
        BooleanEncodedValue restrictionEnc = em.getTurnBooleanEncodedValue(TurnRestriction.key("car"));
        return new CustomWeighting(new DefaultTurnCostProvider(restrictionEnc, graph, new TurnCostsConfig(), config.getTurnPenaltyMapping()), config);
    }

    @Test
    public void testCheckSetSize() {
        CustomModel query = new CustomModel().setParameter("ways", List.of(1, 2)).setParameter("nodes", List.of(3)).setParameter("factor", 4.0);
        CustomModelParser.checkSetSize(query, 3);
        Exception ex = assertThrows(IllegalArgumentException.class, () -> CustomModelParser.checkSetSize(query, 2));
        assertTrue(ex.getMessage().contains("not have more than 2 members in total, but had: 3"), ex.getMessage());

        // checked for every request
        Profile profile = new Profile("car").setCustomModel(createSpeedCustomModel(avSpeedEnc)
                .setParameter("ways", List.of()).setParameter("nodes", List.of()).setParameter("factor", 1.0));
        PMap hints = new PMap().putObject(CustomModel.KEY, query);
        new DefaultWeightingFactory(graph, encodingManager, 3).createWeighting(profile, hints, false);
        assertThrows(IllegalArgumentException.class, () -> new DefaultWeightingFactory(graph, encodingManager, 2).createWeighting(profile, hints, false));

        // the sets of the server-side model are not limited
        Profile largeProfile = new Profile("car").setCustomModel(createSpeedCustomModel(avSpeedEnc).setParameter("ways", List.of(1, 2, 3)));
        new DefaultWeightingFactory(graph, encodingManager, 2).createWeighting(largeProfile, new PMap(), false);
        new DefaultWeightingFactory(graph, encodingManager, 2).createWeighting(largeProfile, new PMap().putObject(CustomModel.KEY, new CustomModel()), false);

        // zero allows only empty sets in a request, a negative limit is refused
        new DefaultWeightingFactory(graph, encodingManager, 0).createWeighting(profile, new PMap().putObject(CustomModel.KEY,
                new CustomModel().setParameter("ways", List.of())), false);
        assertThrows(IllegalArgumentException.class, () -> new DefaultWeightingFactory(graph, encodingManager, 0).createWeighting(profile,
                new PMap().putObject(CustomModel.KEY, new CustomModel().setParameter("ways", List.of(1))), false));
        assertThrows(IllegalArgumentException.class, () -> new DefaultWeightingFactory(graph, encodingManager, -1));
        Exception configEx = assertThrows(IllegalArgumentException.class, () -> new GraphHopper().init(
                new GraphHopperConfig().putObject("graph.location", "target/unused-gh")
                        .putObject("import.osm.ignored_highways", "")
                        .putObject("routing.custom_model.max_set_size", -1)));
        assertTrue(configEx.getMessage().contains("routing.custom_model.max_set_size must not be negative"), configEx.getMessage());
    }

    @Test
    public void testUnsupportedParameterValue() {
        // like before sets were supported, a parameter value that is no number, boolean or set is rejected
        CustomModel textModel = createSpeedCustomModel(avSpeedEnc).setParameters(Map.of("text", "abc"));
        Exception ex = assertThrows(IllegalArgumentException.class, () -> createWeighting(textModel));
        assertTrue(ex.getMessage().contains("parameter 'text' must be a finite number, a boolean or a set, but was: abc"), ex.getMessage());
    }

    @Test
    public void testCheckParameterOverrides() {
        CustomModel base = new CustomModel().setParameter("width", 3.0, 2, 5).setParameter("push", true).setParameter("private_debug", 1.0);
        CustomModelParser.checkParameterOverrides(base, new CustomModel().setParameter("width", 4.0));

        Exception ex = assertThrows(IllegalArgumentException.class,
                () -> CustomModelParser.checkParameterOverrides(base, new CustomModel().setParameter("height", 2.0)));
        assertTrue(ex.getMessage().contains("not defined in the server-side custom model"), ex.getMessage());
        // private_ parameters are not advertised
        assertTrue(ex.getMessage().contains("[width, push]"), ex.getMessage());
        ex = assertThrows(IllegalArgumentException.class,
                () -> CustomModelParser.checkParameterOverrides(base, new CustomModel().setParameter("push", 1.0)));
        assertTrue(ex.getMessage().contains("same type"), ex.getMessage());
        // a String value (e.g. a quoted number in JSON) is rejected here and not deep in the parser
        ex = assertThrows(IllegalArgumentException.class,
                () -> CustomModelParser.checkParameterOverrides(base, new CustomModel().setParameters(Map.of("width", "4"))));
        assertTrue(ex.getMessage().contains("same type"), ex.getMessage());
        ex = assertThrows(IllegalArgumentException.class,
                () -> CustomModelParser.checkParameterOverrides(base, new CustomModel().setParameter("width", 7.0)));
        assertTrue(ex.getMessage().contains("within its range"), ex.getMessage());
        // a range can only be specified in a server-side custom model
        ex = assertThrows(IllegalArgumentException.class,
                () -> CustomModelParser.checkParameterOverrides(base, new CustomModel().setParameter("width", 4.0, 2, 5)));
        assertTrue(ex.getMessage().contains("server-side"), ex.getMessage());

        // a boolean can be overridden by a boolean only
        CustomModelParser.checkParameterOverrides(base, new CustomModel().setParameter("push", false));
        ex = assertThrows(IllegalArgumentException.class,
                () -> CustomModelParser.checkParameterOverrides(base, new CustomModel().setParameter("width", true)));
        assertTrue(ex.getMessage().contains("same type"), ex.getMessage());

        // a set can be overridden by a set only
        CustomModel setBase = new CustomModel().setParameter("ways", List.of()).setParameter("push", true);
        CustomModelParser.checkParameterOverrides(setBase, new CustomModel().setParameter("ways", List.of(1, 2)));
        ex = assertThrows(IllegalArgumentException.class,
                () -> CustomModelParser.checkParameterOverrides(setBase, new CustomModel().setParameter("ways", 1.0)));
        assertTrue(ex.getMessage().contains("same type"), ex.getMessage());
        ex = assertThrows(IllegalArgumentException.class,
                () -> CustomModelParser.checkParameterOverrides(setBase, new CustomModel().setParameter("ways", true)));
        assertTrue(ex.getMessage().contains("same type"), ex.getMessage());
        ex = assertThrows(IllegalArgumentException.class,
                () -> CustomModelParser.checkParameterOverrides(setBase, new CustomModel().setParameter("push", List.of(1))));
        assertTrue(ex.getMessage().contains("same type"), ex.getMessage());
        // the message shows a large set by its size
        ex = assertThrows(IllegalArgumentException.class, () -> CustomModelParser.checkParameterOverrides(base,
                new CustomModel().setParameter("width", IntStream.range(0, 1000).boxed().toList())));
        assertTrue(ex.getMessage().contains("but was: set(size=1000, sha256="), ex.getMessage());
    }

    @Test
    public void testCheckParameterRanges() {
        // without an explicit range the default [0, Infinity) fails for a speed limit (0 => max speed 0)
        CustomModel defaultRange = createSpeedCustomModel(avSpeedEnc).setParameter("max_speed", 90);
        defaultRange.addToSpeed(If("true", LIMIT, "p_max_speed"));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> CustomModelParser.checkParameterRanges(defaultRange, encodingManager));
        assertTrue(ex.getMessage().contains("maximum speed has to be >0"), ex.getMessage());

        CustomModel explicitRange = createSpeedCustomModel(avSpeedEnc).setParameter("max_speed", 90, 10, 140);
        explicitRange.addToSpeed(If("true", LIMIT, "p_max_speed"));
        CustomModelParser.checkParameterRanges(explicitRange, encodingManager);

        // a parameter that can scale the speed up requires a finite max
        CustomModel scale = createSpeedCustomModel(avSpeedEnc).setParameter("factor", 1.0);
        scale.addToSpeed(If("road_class == MOTORWAY", MULTIPLY, "p_factor"));
        ex = assertThrows(IllegalArgumentException.class,
                () -> CustomModelParser.checkParameterRanges(scale, encodingManager));
        assertTrue(ex.getMessage().contains("finite"), ex.getMessage());
        CustomModelParser.checkParameterRanges(scale.setParameter("factor", 1.0, 0, 1.2), encodingManager);

        // Infinity * 0 (the minimum of the encoded value) is NaN and must not slip through
        CustomModel nan = createSpeedCustomModel(avSpeedEnc).setParameter("factor", 1.0, 0.5, Double.POSITIVE_INFINITY);
        nan.addToSpeed(If("true", LIMIT, "p_factor * car_average_speed"));
        ex = assertThrows(IllegalArgumentException.class,
                () -> CustomModelParser.checkParameterRanges(nan, encodingManager));
        assertTrue(ex.getMessage().contains("finite"), ex.getMessage());

        CustomModel turnNaN = createSpeedCustomModel(avSpeedEnc).setParameter("scale", 1.0);
        turnNaN.addToTurnPenalty(If("true", MULTIPLY, "p_scale"));
        ex = assertThrows(IllegalArgumentException.class,
                () -> CustomModelParser.checkParameterRanges(turnNaN, encodingManager));
        assertTrue(ex.getMessage().contains(">=0"), ex.getMessage());

        // turn_penalty is validated at the endpoints too
        CustomModel turn = createSpeedCustomModel(avSpeedEnc).setParameter("discount", 30.0, 0, 600);
        turn.addToTurnPenalty(If("true", Statement.Op.ADD, "60 - p_discount"));
        ex = assertThrows(IllegalArgumentException.class,
                () -> CustomModelParser.checkParameterRanges(turn, encodingManager));
        assertTrue(ex.getMessage().contains("negative"), ex.getMessage());
        CustomModelParser.checkParameterRanges(turn.setParameter("discount", 30.0, 0, 60), encodingManager);

        // "Infinity" is a valid turn_penalty value (see avoid_turns.json) and must pass the endpoint check
        CustomModel infiniteTurn = createSpeedCustomModel(avSpeedEnc).setParameter("xx", 1.0, 0, 2);
        infiniteTurn.addToTurnPenalty(If("true", Statement.Op.ADD, "Infinity"));
        CustomModelParser.checkParameterRanges(infiniteTurn, encodingManager);
    }

    public void testMinMaxInValueExpression() {
        DecimalEncodedValue myPriorityEnc = encodingManager.getDecimalEncodedValue("my_priority");
        EdgeIteratorState edge = graph.edge(0, 1).setDistance(1000).set(avSpeedEnc, 50, 50).set(myPriorityEnc, 0.7);
        Weighting weighting = createWeighting(createSpeedCustomModel(avSpeedEnc).setDistanceInfluence(0d).
                addToPriority(If("true", MULTIPLY, "Math.min(my_priority, 0.5)")));
        // the priority is limited to 0.5 -> the weight doubles compared to 72s
        assertEquals(10 * 72 / 0.5, weighting.calcEdgeWeight(edge, false), 0.1);
        weighting = createWeighting(createSpeedCustomModel(avSpeedEnc).setDistanceInfluence(0d).
                addToPriority(If("true", MULTIPLY, "Math.max(0.8, 1 - my_priority)")));
        assertEquals(10 * 72 / 0.8, weighting.calcEdgeWeight(edge, false), 0.1);
    }

    @Test
    public void speedOnly() {
        // 50km/h -> 72s per km, 100km/h -> 36s per km
        EdgeIteratorState edge = graph.edge(0, 1).setDistance(1000).set(avSpeedEnc, 50, 100);
        CustomModel customModel = createSpeedCustomModel(avSpeedEnc)
                .setDistanceInfluence(0d);
        Weighting weighting = createWeighting(customModel);

        assertEquals(720, weighting.calcEdgeWeight(edge, false));
        assertEquals(360, weighting.calcEdgeWeight(edge, true));
    }

    @Test
    public void withPriority() {
        // 25km/h -> 144s per km, 50km/h -> 72s per km, 100km/h -> 36s per km
        EdgeIteratorState slow = graph.edge(0, 1).set(avSpeedEnc, 25, 25).setDistance(1000).
                set(roadClassEnc, SECONDARY);
        EdgeIteratorState medium = graph.edge(0, 1).set(avSpeedEnc, 50, 50).setDistance(1000).
                set(roadClassEnc, SECONDARY);
        EdgeIteratorState fast = graph.edge(0, 1).set(avSpeedEnc, 100).setDistance(1000).
                set(roadClassEnc, SECONDARY);

        Weighting weighting = createWeighting(createSpeedCustomModel(avSpeedEnc));
        assertEquals(1440, weighting.calcEdgeWeight(slow, false));
        assertEquals(720, weighting.calcEdgeWeight(medium, false));
        assertEquals(360, weighting.calcEdgeWeight(fast, false));

        // if we reduce the priority we get higher edge weights
        weighting = CustomModelParser.createWeighting(encodingManager, NO_TURN_COST_PROVIDER,
                createSpeedCustomModel(avSpeedEnc)
                        .addToPriority(If("road_class == SECONDARY", MULTIPLY, "0.5"))
        );
        assertEquals(2 * 1440, weighting.calcEdgeWeight(slow, false));
        assertEquals(2 * 720, weighting.calcEdgeWeight(medium, false));
        assertEquals(2 * 360, weighting.calcEdgeWeight(fast, false));
    }

    @Test
    public void withDistanceInfluence() {
        EdgeIteratorState edge1 = graph.edge(0, 1).setDistance(10_000).set(avSpeedEnc, 50);
        EdgeIteratorState edge2 = graph.edge(0, 1).setDistance(5_000).set(avSpeedEnc, 25);
        Weighting weighting = createWeighting(createSpeedCustomModel(avSpeedEnc).setDistanceInfluence(0d));
        assertEquals(7200, weighting.calcEdgeWeight(edge1, false));
        assertEquals(720_000, weighting.calcEdgeMillis(edge1, false));
        // we can also imagine a shorter but slower road that takes the same time
        assertEquals(7200, weighting.calcEdgeWeight(edge2, false));
        assertEquals(720_000, weighting.calcEdgeMillis(edge2, false));

        // distance_influence=30 means that for every kilometer we get additional costs of 30s, so +300s here
        weighting = createWeighting(createSpeedCustomModel(avSpeedEnc).setDistanceInfluence(30d));
        assertEquals(10200, weighting.calcEdgeWeight(edge1, false));
        // for the shorter but slower edge the distance influence also increases the weight, but not as much because it is shorter
        assertEquals(8700, weighting.calcEdgeWeight(edge2, false));
        // ... the travelling times stay the same
        assertEquals(720_000, weighting.calcEdgeMillis(edge1, false));
        assertEquals(720_000, weighting.calcEdgeMillis(edge2, false));
    }

    @Test
    public void testSpeedFactorBooleanEV() {
        EdgeIteratorState edge = graph.edge(0, 1).set(avSpeedEnc, 15, 15).setDistance(10);
        Weighting weighting = createWeighting(createSpeedCustomModel(avSpeedEnc).setDistanceInfluence(70d));
        assertEquals(31, weighting.calcEdgeWeight(edge, false));
        // here we increase weight for edges that are road class links
        weighting = createWeighting(createSpeedCustomModel(avSpeedEnc)
                .setDistanceInfluence(70d)
                .addToPriority(If(RoadClassLink.KEY, MULTIPLY, "0.5")));
        BooleanEncodedValue rcLinkEnc = encodingManager.getBooleanEncodedValue(RoadClassLink.KEY);
        assertEquals(31, weighting.calcEdgeWeight(edge.set(rcLinkEnc, false), false));
        assertEquals(55, weighting.calcEdgeWeight(edge.set(rcLinkEnc, true), false));
    }

    @Test
    public void testBoolean() {
        BooleanEncodedValue specialEnc = new SimpleBooleanEncodedValue("special", true);
        DecimalEncodedValue avSpeedEnc = VehicleSpeed.create("car", 5, 5, false);
        encodingManager = new EncodingManager.Builder().add(specialEnc).add(avSpeedEnc).build();
        graph = new BaseGraph.Builder(encodingManager).create();

        EdgeIteratorState edge = graph.edge(0, 1).set(specialEnc, false, true).set(avSpeedEnc, 15).setDistance(10);

        Weighting weighting = createWeighting(createSpeedCustomModel(avSpeedEnc).setDistanceInfluence(70d));
        assertEquals(31, weighting.calcEdgeWeight(edge, false));
        weighting = createWeighting(createSpeedCustomModel(avSpeedEnc)
                .setDistanceInfluence(70d)
                .addToPriority(If("special == true", MULTIPLY, "0.8"))
                .addToPriority(If("special == false", MULTIPLY, "0.4")));
        assertEquals(67, weighting.calcEdgeWeight(edge, false));
        assertEquals(37, weighting.calcEdgeWeight(edge, true));
    }

    @Test
    public void testSpeedFactorAndPriority() {
        EdgeIteratorState primary = graph.edge(0, 1).setDistance(10).
                set(roadClassEnc, PRIMARY).set(avSpeedEnc, 80);
        EdgeIteratorState secondary = graph.edge(1, 2).setDistance(10).
                set(roadClassEnc, SECONDARY).set(avSpeedEnc, 70);

        CustomModel customModel = createSpeedCustomModel(avSpeedEnc).setDistanceInfluence(70d).
                addToPriority(If("road_class != PRIMARY", MULTIPLY, "0.5")).
                addToSpeed(If("road_class != PRIMARY", MULTIPLY, "0.9"));
        Weighting weighting = createWeighting(customModel);
        assertEquals(12, weighting.calcEdgeWeight(primary, false));
        assertEquals(18, weighting.calcEdgeWeight(secondary, false));

        customModel = createSpeedCustomModel(avSpeedEnc).setDistanceInfluence(70d).
                addToPriority(If("road_class == PRIMARY", MULTIPLY, "1.0")).
                addToPriority(Else(MULTIPLY, "0.5")).
                addToSpeed(If("road_class != PRIMARY", MULTIPLY, "0.9"));
        weighting = createWeighting(customModel);
        assertEquals(12, weighting.calcEdgeWeight(primary, false));
        assertEquals(18, weighting.calcEdgeWeight(secondary, false));
    }

    @Test
    public void testIssueSameKey() {
        EdgeIteratorState withToll = graph.edge(0, 1).setDistance(10).set(avSpeedEnc, 80);
        EdgeIteratorState noToll = graph.edge(1, 2).setDistance(10).set(avSpeedEnc, 80);

        CustomModel customModel = createSpeedCustomModel(avSpeedEnc);
        customModel.setDistanceInfluence(70d).
                addToSpeed(If("toll == HGV || toll == ALL", MULTIPLY, "0.8")).
                addToSpeed(If("hazmat != NO", MULTIPLY, "0.8"));
        Weighting weighting = createWeighting(customModel);
        assertEquals(13, weighting.calcEdgeWeight(withToll, false));
        assertEquals(13, weighting.calcEdgeWeight(noToll, false));

        customModel = createSpeedCustomModel(avSpeedEnc);
        customModel.setDistanceInfluence(70d).
                addToSpeed(If("bike_network != OTHER", MULTIPLY, "0.8"));
        weighting = createWeighting(customModel);
        assertEquals(13, weighting.calcEdgeWeight(withToll, false));
        assertEquals(13, weighting.calcEdgeWeight(noToll, false));
    }

    @Test
    public void testFirstMatch() {
        EdgeIteratorState primary = graph.edge(0, 1).setDistance(10).
                set(roadClassEnc, PRIMARY).set(avSpeedEnc, 80);
        EdgeIteratorState secondary = graph.edge(1, 2).setDistance(10).
                set(roadClassEnc, SECONDARY).set(avSpeedEnc, 70);

        CustomModel customModel = createSpeedCustomModel(avSpeedEnc).setDistanceInfluence(70d).
                addToSpeed(If("road_class == PRIMARY", MULTIPLY, "0.8"));
        Weighting weighting = createWeighting(customModel);
        assertEquals(13, weighting.calcEdgeWeight(primary, false));
        assertEquals(12, weighting.calcEdgeWeight(secondary, false));

        customModel.addToPriority(If("road_class == PRIMARY", MULTIPLY, "0.9"));
        customModel.addToPriority(ElseIf("road_class == SECONDARY", MULTIPLY, "0.8"));

        weighting = createWeighting(customModel);
        assertEquals(13, weighting.calcEdgeWeight(primary, false));
        assertEquals(13, weighting.calcEdgeWeight(secondary, false));
    }

    @Test
    public void testSpeedBiggerThan() {
        EdgeIteratorState edge40 = graph.edge(0, 1).setDistance(10).set(avSpeedEnc, 40);
        EdgeIteratorState edge50 = graph.edge(1, 2).setDistance(10).set(avSpeedEnc, 50);

        CustomModel customModel = createSpeedCustomModel(avSpeedEnc).setDistanceInfluence(70d).
                addToPriority(If("car_average_speed > 40", MULTIPLY, "0.5"));
        Weighting weighting = createWeighting(customModel);

        assertEquals(16, weighting.calcEdgeWeight(edge40, false));
        assertEquals(21, weighting.calcEdgeWeight(edge50, false));
    }

    @Test
    public void testRoadClass() {
        EdgeIteratorState primary = graph.edge(0, 1).setDistance(10).
                set(roadClassEnc, PRIMARY).set(avSpeedEnc, 80);
        EdgeIteratorState secondary = graph.edge(1, 2).setDistance(10).
                set(roadClassEnc, SECONDARY).set(avSpeedEnc, 80);
        CustomModel customModel = createSpeedCustomModel(avSpeedEnc).setDistanceInfluence(70d).
                addToPriority(If("road_class == PRIMARY", MULTIPLY, "0.5"));
        Weighting weighting = createWeighting(customModel);
        assertEquals(16, weighting.calcEdgeWeight(primary, false));
        assertEquals(12, weighting.calcEdgeWeight(secondary, false));
    }

    @Test
    public void testArea() throws Exception {
        EdgeIteratorState edge1 = graph.edge(0, 1).setDistance(10).
                set(roadClassEnc, PRIMARY).set(avSpeedEnc, 80);
        EdgeIteratorState edge2 = graph.edge(2, 3).setDistance(10).
                set(roadClassEnc, PRIMARY).set(avSpeedEnc, 80);
        graph.getNodeAccess().setNode(0, 50.0120, 11.582);
        graph.getNodeAccess().setNode(1, 50.0125, 11.585);
        graph.getNodeAccess().setNode(2, 40.0, 8.0);
        graph.getNodeAccess().setNode(3, 40.1, 8.1);
        CustomModel customModel = createSpeedCustomModel(avSpeedEnc).setDistanceInfluence(70d).
                addToPriority(If("in_custom1", MULTIPLY, "0.5"));

        ObjectMapper om = new ObjectMapper().registerModule(new JtsModule());
        JsonFeature json = om.readValue("{ \"geometry\":{ \"type\": \"Polygon\", \"coordinates\": " +
                "[[[11.5818,50.0126], [11.5818,50.0119], [11.5861,50.0119], [11.5861,50.0126], [11.5818,50.0126]]] }}", JsonFeature.class);
        json.setId("custom1");
        customModel.getAreas().getFeatures().add(json);

        Weighting weighting = createWeighting(customModel);
        // edge1 is located within the area custom1, edge2 is not
        assertEquals(16, weighting.calcEdgeWeight(edge1, false));
        assertEquals(12, weighting.calcEdgeWeight(edge2, false));
    }

    @Test
    public void testMaxSpeed() {
        assertEquals(155, avSpeedEnc.getMaxOrMaxStorableDecimal(), 0.1);

        assertEquals(10d / 72 * 3.6, createWeighting(createSpeedCustomModel(avSpeedEnc).
                addToSpeed(If("true", LIMIT, "72"))).calcMinWeightPerDistance(), .001);

        // ignore too big limit to let custom model compatibility not break when max speed of encoded value later decreases
        assertEquals(10d / 155 * 3.6, createWeighting(createSpeedCustomModel(avSpeedEnc).
                addToSpeed(If("true", LIMIT, "180"))).calcMinWeightPerDistance(), .001);

        // reduce speed only a bit
        assertEquals(10d / 150 * 3.6, createWeighting(createSpeedCustomModel(avSpeedEnc).
                addToSpeed(If("road_class == SERVICE", MULTIPLY, "1.5")).
                addToSpeed(If("true", LIMIT, "150"))).calcMinWeightPerDistance(), .001);
    }

    @Test
    public void testMaxPriority() {
        double maxSpeed = 155;
        assertEquals(maxSpeed, avSpeedEnc.getMaxOrMaxStorableDecimal(), 0.1);
        assertEquals(10d / maxSpeed / 0.5 * 3.6, createWeighting(createSpeedCustomModel(avSpeedEnc).
                addToPriority(If("true", MULTIPLY, "0.5"))).calcMinWeightPerDistance(), 1.e-6);

        // ignore too big limit
        assertEquals(10d / maxSpeed / 1.0 * 3.6, createWeighting(createSpeedCustomModel(avSpeedEnc).
                addToPriority(If("true", LIMIT, "2.0"))).calcMinWeightPerDistance(), 1.e-6);

        // priority bigger 1 is fine (if CustomModel not in query)
        assertEquals(10d / maxSpeed / 2.0 * 3.6, createWeighting(createSpeedCustomModel(avSpeedEnc).
                addToPriority(If("true", MULTIPLY, "3.0")).
                addToPriority(If("true", LIMIT, "2.0"))).calcMinWeightPerDistance(), 1.e-6);
        assertEquals(10d / maxSpeed / 1.5 * 3.6, createWeighting(createSpeedCustomModel(avSpeedEnc).
                addToPriority(If("true", MULTIPLY, "1.5"))).calcMinWeightPerDistance(), 1.e-6);

        // pick maximum priority from value even if this is for a special case
        assertEquals(10d / maxSpeed / 3.0 * 3.6, createWeighting(createSpeedCustomModel(avSpeedEnc).
                addToPriority(If("road_class == SERVICE", MULTIPLY, "3.0"))).calcMinWeightPerDistance(), 1.e-6);

        // do NOT pick maximum priority when it is for a special case
        assertEquals(10d / maxSpeed / 1.0 * 3.6, createWeighting(createSpeedCustomModel(avSpeedEnc).
                addToPriority(If("road_class == SERVICE", MULTIPLY, "0.5"))).calcMinWeightPerDistance(), 1.e-6);

        // an unconditional multiply_by with an encoded value must use its maximum (0.75) only once, not squared
        assertEquals(10d / maxSpeed / 0.75 * 3.6, createWeighting(createSpeedCustomModel(avSpeedEnc).
                addToPriority(If("true", MULTIPLY, "my_priority"))).calcMinWeightPerDistance(), 1.e-6);

        // a leading unconditional 'do' block must not throw and the maximum (0.9) is picked from its branches
        assertEquals(10d / maxSpeed / 0.9 * 3.6, createWeighting(createSpeedCustomModel(avSpeedEnc).
                addToPriority(If("true", List.of(If("road_class == MOTORWAY", MULTIPLY, "0.5"), Else(MULTIPLY, "0.9"))))).
                calcMinWeightPerDistance(), 1.e-6);
    }

    @Test
    public void maxSpeedViolated_bug_2307() {
        EdgeIteratorState motorway = graph.edge(0, 1).setDistance(10).
                set(roadClassEnc, MOTORWAY).set(avSpeedEnc, 80);
        CustomModel customModel = createSpeedCustomModel(avSpeedEnc)
                .setDistanceInfluence(70d)
                .addToSpeed(If("road_class == MOTORWAY", Statement.Op.MULTIPLY, "0.7"))
                .addToSpeed(Else(LIMIT, "30"));
        Weighting weighting = createWeighting(customModel);
        assertEquals(13, weighting.calcEdgeWeight(motorway, false));
        assertEquals(10 / (80 * 0.7 / 3.6) * 1000, weighting.calcEdgeMillis(motorway, false), 1);
    }

    @Test
    public void bugWithNaNForBarrierEdges() {
        EdgeIteratorState motorway = graph.edge(0, 1).setDistance(0).
                set(roadClassEnc, MOTORWAY).set(avSpeedEnc, 80);
        CustomModel customModel = createSpeedCustomModel(avSpeedEnc)
                .addToPriority(If("road_class == MOTORWAY", Statement.Op.MULTIPLY, "0"));
        Weighting weighting = createWeighting(customModel);
        assertFalse(Double.isNaN(weighting.calcEdgeWeight(motorway, false)));
        assertTrue(Double.isInfinite(weighting.calcEdgeWeight(motorway, false)));
    }

    @Test
    public void testEdgeDistanceInCondition() {
        EdgeIteratorState shortEdge = graph.edge(0, 1).set(avSpeedEnc, 100, 100).setDistance(100);
        EdgeIteratorState longEdge = graph.edge(1, 2).set(avSpeedEnc, 100, 100).setDistance(1000);

        CustomModel customModel = createSpeedCustomModel(avSpeedEnc).setDistanceInfluence(0d).
                addToPriority(If("edge.getDistance() > 500", MULTIPLY, "0.1"));
        Weighting weighting = createWeighting(customModel);

        assertEquals(36, weighting.calcEdgeWeight(shortEdge, false));
        assertEquals(3600, weighting.calcEdgeWeight(longEdge, false));
    }

    @Test
    public void testMinWeightHasSameUnitAs_getWeight() {
        EdgeIteratorState edge = graph.edge(0, 1).set(avSpeedEnc, 140, 0).setDistance(1000);
        CustomModel customModel = createSpeedCustomModel(avSpeedEnc);
        Weighting weighting = createWeighting(customModel);
        assertEquals(roundWeight(weighting.calcMinWeightPerDistance() * 1000), weighting.calcEdgeWeight(edge, false));
    }

    @Test
    public void testWeightWrongHeading() {
        CustomModel customModel = createSpeedCustomModel(avSpeedEnc).setHeadingPenalty(100);
        Weighting weighting = createWeighting(customModel);
        EdgeIteratorState edge = graph.edge(1, 2)
                .set(avSpeedEnc, 10, 10)
                .setDistance(10).setWayGeometry(Helper.createPointList(51, 0, 51, 1));
        VirtualEdgeIteratorState virtEdge = new VirtualEdgeIteratorState(edge.getEdgeKey(), 99, 5, 6, edge.getDistance(), edge.getFlags(),
                edge.getKeyValues(), edge.fetchWayGeometry(FetchMode.PILLAR_ONLY), false);
        double time = weighting.calcEdgeWeight(virtEdge, false);

        virtEdge.setUnfavored(true);
        // heading penalty on edge
        assertEquals(time + 1000, weighting.calcEdgeWeight(virtEdge, false));
        // only after setting it
        virtEdge.setUnfavored(true);
        assertEquals(time + 1000, weighting.calcEdgeWeight(virtEdge, true));
        // but not after releasing it
        virtEdge.setUnfavored(false);
        assertEquals(time, weighting.calcEdgeWeight(virtEdge, true));

        // test default penalty
        virtEdge.setUnfavored(true);
        customModel = createSpeedCustomModel(avSpeedEnc);
        weighting = createWeighting(customModel);
        assertEquals(time + 10 * Parameters.Routing.DEFAULT_HEADING_PENALTY, weighting.calcEdgeWeight(virtEdge, false));
    }

    @Test
    public void testSpeed0() {
        EdgeIteratorState edge = graph.edge(0, 1).setDistance(10);
        CustomModel customModel = createSpeedCustomModel(avSpeedEnc);
        Weighting weighting = createWeighting(customModel);
        edge.set(avSpeedEnc, 0);
        assertEquals(1.0 / 0, weighting.calcEdgeWeight(edge, false), 1e-8);

        // 0 / 0 returns NaN but calcWeight should not return NaN!
        edge.setDistance(0);
        assertEquals(1.0 / 0, weighting.calcEdgeWeight(edge, false), 1e-8);
    }

    @Test
    public void testTime() {
        DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl("speed", 4, 2, true);
        EncodingManager em = EncodingManager.start().add(speedEnc).build();
        BaseGraph g = new BaseGraph.Builder(em).create();
        EdgeIteratorState edge = g.edge(0, 1).set(speedEnc, 15, 10).setDistance(100_000);
        CustomModel customModel = createSpeedCustomModel(speedEnc);
        Weighting w = CustomModelParser.createWeighting(em, NO_TURN_COST_PROVIDER, customModel);
        assertEquals(375 * 60 * 1000, w.calcEdgeMillis(edge, false));
        assertEquals(600 * 60 * 1000, w.calcEdgeMillis(edge, true));
    }

    @Test
    public void calcWeightAndTime_withTurnCosts() {
        BaseGraph graph = new BaseGraph.Builder(encodingManager).withTurnCosts(true).create();
        CustomModel customModel = createSpeedCustomModel(avSpeedEnc);
        Weighting weighting = CustomModelParser.createWeighting(encodingManager,
                new DefaultTurnCostProvider(turnRestrictionEnc, graph, new TurnCostsConfig(), null), customModel);
        graph.edge(0, 1).set(avSpeedEnc, 60, 60).setDistance(100);
        EdgeIteratorState edge = graph.edge(1, 2).set(avSpeedEnc, 60, 60).setDistance(100);
        setTurnRestriction(graph, 0, 1, 2);
        assertTrue(Double.isInfinite(GHUtility.calcWeightWithTurnWeight(weighting, edge, false, 0)));
        // the time only reflects the time for the edge, the turn time is 0
        assertEquals(6000, GHUtility.calcMillisWithTurnMillis(weighting, edge, false, 0));
    }

    @Test
    public void calcWeightAndTime_uTurnCosts() {
        BaseGraph graph = new BaseGraph.Builder(encodingManager).withTurnCosts(true).create();
        CustomModel customModel = createSpeedCustomModel(avSpeedEnc);
        Weighting weighting = CustomModelParser.createWeighting(encodingManager,
                new DefaultTurnCostProvider(turnRestrictionEnc, graph, new TurnCostsConfig().setUTurnCosts(40), null), customModel);
        EdgeIteratorState edge = graph.edge(0, 1).set(avSpeedEnc, 60, 60).setDistance(100);
        assertEquals(60 + 400, GHUtility.calcWeightWithTurnWeight(weighting, edge, false, 0), 1.e-6);
        assertEquals(6 * 1000, GHUtility.calcMillisWithTurnMillis(weighting, edge, false, 0), 1.e-6);
    }

    @Test
    public void testDestinationTag() {
        DecimalEncodedValue carSpeedEnc = new DecimalEncodedValueImpl("car_speed", 5, 5, false);
        DecimalEncodedValue bikeSpeedEnc = new DecimalEncodedValueImpl("bike_speed", 4, 2, false);
        EncodingManager em = EncodingManager.start().add(carSpeedEnc).add(bikeSpeedEnc).add(RoadAccess.create()).build();
        BaseGraph graph = new BaseGraph.Builder(em).create();
        EdgeIteratorState edge = graph.edge(0, 1).setDistance(1000);
        edge.set(carSpeedEnc, 60);
        edge.set(bikeSpeedEnc, 18);
        EnumEncodedValue<RoadAccess> roadAccessEnc = em.getEnumEncodedValue(RoadAccess.KEY, RoadAccess.class);

        CustomModel customModel = createSpeedCustomModel(carSpeedEnc)
                .addToPriority(If("road_access == DESTINATION", MULTIPLY, ".1"));
        Weighting weighting = CustomModelParser.createWeighting(em, NO_TURN_COST_PROVIDER, customModel);

        CustomModel bikeCustomModel = createSpeedCustomModel(bikeSpeedEnc);
        Weighting bikeWeighting = CustomModelParser.createWeighting(em, NO_TURN_COST_PROVIDER, bikeCustomModel);

        edge.set(roadAccessEnc, RoadAccess.YES);
        assertEquals(600, weighting.calcEdgeWeight(edge, false));
        assertEquals(2000, bikeWeighting.calcEdgeWeight(edge, false));

        // the destination tag does not change the weight for the bike weighting
        edge.set(roadAccessEnc, RoadAccess.DESTINATION);
        assertEquals(6000, weighting.calcEdgeWeight(edge, false));
        assertEquals(2000, bikeWeighting.calcEdgeWeight(edge, false));
    }

    @Test
    public void testPrivateTag() {
        DecimalEncodedValue carSpeedEnc = new DecimalEncodedValueImpl("car_speed", 5, 5, false);
        DecimalEncodedValue bikeSpeedEnc = new DecimalEncodedValueImpl("bike_speed", 4, 2, false);
        EncodingManager em = EncodingManager.start().add(carSpeedEnc).add(bikeSpeedEnc).add(RoadAccess.create()).build();
        BaseGraph graph = new BaseGraph.Builder(em).create();
        EdgeIteratorState edge = graph.edge(0, 1).setDistance(1000);
        edge.set(carSpeedEnc, 60);
        edge.set(bikeSpeedEnc, 18);
        EnumEncodedValue<RoadAccess> roadAccessEnc = em.getEnumEncodedValue(RoadAccess.KEY, RoadAccess.class);

        CustomModel customModel = createSpeedCustomModel(carSpeedEnc)
                .addToPriority(If("road_access == PRIVATE", MULTIPLY, ".1"));
        Weighting weighting = CustomModelParser.createWeighting(em, NO_TURN_COST_PROVIDER, customModel);

        customModel = createSpeedCustomModel(bikeSpeedEnc)
                .addToPriority(If("road_access == PRIVATE", MULTIPLY, "0.8333"));
        Weighting bikeWeighting = CustomModelParser.createWeighting(em, NO_TURN_COST_PROVIDER, customModel);

        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "secondary");

        edge.set(roadAccessEnc, RoadAccess.YES);
        assertEquals(600, weighting.calcEdgeWeight(edge, false));
        assertEquals(2000, bikeWeighting.calcEdgeWeight(edge, false));

        edge.set(roadAccessEnc, RoadAccess.PRIVATE);
        assertEquals(6000, weighting.calcEdgeWeight(edge, false));
        // private should influence bike only slightly
        assertEquals(2400, bikeWeighting.calcEdgeWeight(edge, false));
    }

    @Test
    public void testFirstSpeedGroupWithBlock() {
        // an else block sets the speed if its own first group does
        CustomModel cm = new CustomModel().addToSpeed(If("road_class == PRIMARY", LIMIT, "30")).
                addToSpeed(Else(List.of(If("true", LIMIT, avSpeedEnc.getName()), If("true", MULTIPLY, "0.5"))));
        assertNotNull(CustomModelParser.createWeighting(encodingManager, NO_TURN_COST_PROVIDER, cm));
        CustomModel invalid = new CustomModel().addToSpeed(If("road_class == PRIMARY", LIMIT, "30")).
                addToSpeed(Else(List.of(If("true", MULTIPLY, "0.5"))));
        String msg = assertThrows(IllegalArgumentException.class, () -> CustomModelParser.createWeighting(encodingManager, NO_TURN_COST_PROVIDER, invalid)).getMessage();
        assertTrue(msg.contains("'limit_to'"), msg);
    }
}
