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
package com.graphhopper.routing.weighting.custom.generate;

import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.search.KVStorage;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.*;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static com.graphhopper.json.Statement.*;
import static com.graphhopper.json.Statement.Op.*;

/**
 * Shared fixtures for the stage-5 tests (SourceGeneratorTest + RegistryBackendDifferentialTest):
 * the encoding manager both the checked-in pre-generated classes and the golden sources were
 * generated against, the "kitchen sink" custom model covering the typed-emission corners, and
 * the randomized-but-seeded graph construction of the differential harnesses (also reused by
 * ClosureBackendDifferentialTest — the fill must stay STORABLE-range based, see setRandomValue).
 */
public class Stage5Fixtures {

    static final long GRAPH_SEED = 123L;
    public static final int NODES = 40;
    static final int EDGES = 120;

    static EncodingManager createEncodingManager() {
        return new EncodingManager.Builder()
                .add(VehicleAccess.create("car"))
                .add(VehicleSpeed.create("car", 5, 2, true))
                .add(RoadClass.create()).add(RoadEnvironment.create()).add(Roundabout.create())
                .add(MaxSpeed.create()).add(FerrySpeed.create())
                .add(HikeRating.create()).add(MtbRating.create())
                .add(AverageSlope.create()).add(Country.create())
                .add(Orientation.create())
                .build();
    }

    static CustomModel carModel() {
        return GHUtility.loadCustomModelFromJar("car.json");
    }

    /**
     * Covers the typed-emission corners in one accepted model: truncating int division and
     * bitwise/shift operators, Java literal typing (char/hex/octal/binary/underscore/long/
     * float/scientific), enum comparisons and ordinal(), backward_ prefix, areas,
     * Math.sqrt/abs, country.isRightHandTraffic(), enum-null folding, blocks, and the full
     * turn-penalty surface (change_angle, street-name identity/equals/contains, prev_ prefix,
     * Infinity).
     */
    static CustomModel kitchenSinkModel() {
        CustomModel m = new CustomModel();
        m.setDistanceInfluence(70d);
        m.setAreas(areas());
        m.addToSpeed(If("true", LIMIT, "car_average_speed"));
        m.addToSpeed(If("hike_rating / 2 == 1", MULTIPLY, "0.5"));
        m.addToSpeed(ElseIf("(hike_rating & 1) == 0", MULTIPLY, "0.8"));
        m.addToSpeed(Else(MULTIPLY, "0.9"));
        m.addToSpeed(If("max_speed > 100 || Math.abs(average_slope) > 2", LIMIT, "Math.sqrt(max_speed) * 10"));
        m.addToSpeed(If("road_environment == FERRY", List.of(
                If("true", LIMIT, "30"),
                If("roundabout", MULTIPLY, "0.5"))));
        m.addToPriority(If("backward_car_access != car_access", MULTIPLY, "0.5"));
        m.addToPriority(If("road_class == PRIMARY || in_area_a", MULTIPLY, "0.9"));
        m.addToPriority(ElseIf("road_class.ordinal() > 5 && (hike_rating >> 1) == 1", MULTIPLY, "0.8"));
        m.addToPriority(If("country.isRightHandTraffic() && country == DEU", MULTIPLY, "0.95"));
        m.addToPriority(If("'A' < max_speed && 0x1F > hike_rating && 0b101 > mtb_rating && 010 < max_speed"
                + " && 1_0 < max_speed && 3L > hike_rating && 2.5f < max_speed && 1e1 < max_speed", MULTIPLY, "0.99"));
        m.addToPriority(If("road_class != null && (hike_rating >>> 1) >= 1", MULTIPLY, "0.98"));
        m.addToTurnPenalty(If("change_angle > 25 && prev_road_class == road_class", ADD, "3"));
        m.addToTurnPenalty(ElseIf("street_name == \"Main St\"", ADD, "1"));
        m.addToTurnPenalty(ElseIf("street_name.contains(\"Main\") || prev_street_name.equals(street_name)", ADD, "5.5"));
        m.addToTurnPenalty(ElseIf("prev_max_speed > 50", ADD, "car_average_speed * 2"));
        m.addToTurnPenalty(Else(ADD, "Infinity"));
        return m;
    }

    static JsonFeatureCollection areas() {
        JsonFeatureCollection collection = new JsonFeatureCollection();
        Coordinate[] coordinates = new Coordinate[]{
                new Coordinate(11.2, 48.0), new Coordinate(11.55, 48.0), new Coordinate(11.55, 48.15),
                new Coordinate(11.2, 48.15), new Coordinate(11.2, 48.0)};
        collection.getFeatures().add(new JsonFeature("area_a", "Feature", null,
                new GeometryFactory().createPolygon(coordinates), new HashMap<>()));
        return collection;
    }

    /** The shared random-graph construction of the differential harnesses (fixed seed). */
    public static BaseGraph createRandomGraph(EncodingManager em, Random rnd) {
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
    public static void setRandomValue(EdgeIteratorState edge, EncodedValue ev, Random rnd) {
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
            // deliberately use the STORABLE range (not maxOrMax, which shrinks to the values
            // seen so far and would degenerate the fill to the first edge's value)
            double min = de.getMinStorableDecimal();
            double max = de.getMaxStorableDecimal();
            if (Double.isInfinite(max)) max = 150;
            max = Math.min(max, 150);
            double v1 = min + rnd.nextDouble() * (max - min);
            if (de.isStoreTwoDirections()) edge.set(de, v1, min + rnd.nextDouble() * (max - min));
            else edge.set(de, v1);
        } else if (ev instanceof IntEncodedValue) {
            IntEncodedValue ie = (IntEncodedValue) ev;
            int min = Math.max(ie.getMinStorableInt(), -100);
            int max = Math.min(ie.getMaxStorableInt(), 100);
            int v1 = min + rnd.nextInt(max - min + 1);
            if (ie.isStoreTwoDirections()) edge.set(ie, v1, min + rnd.nextInt(max - min + 1));
            else edge.set(ie, v1);
        }
    }
}
