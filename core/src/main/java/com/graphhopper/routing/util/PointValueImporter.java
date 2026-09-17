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
package com.graphhopper.routing.util;

import com.carrotsearch.hppc.IntDoubleHashMap;
import com.carrotsearch.hppc.IntHashSet;
import com.carrotsearch.hppc.cursors.IntDoubleCursor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.routing.ev.*;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.DAType;
import com.graphhopper.storage.GHDirectory;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.GHPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Sets an encoded value for the edges next to points read from GeoJSON files, e.g. official clearance heights of
 * bridges. Every point goes to the closest edge within max_distance, optionally only to edges with a similar name
 * and a matching heading. Runs directly after the import, so subnetworks, CH and LM see the values.
 * <p>
 * Feature properties: value (required), name, heading (degrees).
 */
public class PointValueImporter {
    private static final Logger logger = LoggerFactory.getLogger(PointValueImporter.class);
    // edges at the same distance are all taken, e.g. the artificial copies of an edge
    private static final double SAME_DISTANCE = 0.01;

    public enum Pick {MIN, MAX}

    public record Config(String file, String encodedValue, Pick pick, boolean overwrite, double maxDistance,
                         double headingTolerance) {
        public static Config fromMap(Map<String, Object> map) {
            PMap pmap = new PMap(map);
            String file = pmap.getString("file", "");
            String ev = pmap.getString("encoded_value", "");
            String pick = pmap.getString("pick", "");
            if (file.isEmpty() || ev.isEmpty() || pick.isEmpty())
                throw new IllegalArgumentException("import.point_values requires file, encoded_value and pick (min or max), but was " + map);
            return new Config(file, ev, Pick.valueOf(pick.toUpperCase(Locale.ROOT)), pmap.getBool("overwrite", false),
                    pmap.getDouble("max_distance", 10), pmap.getDouble("heading_tolerance", 30));
        }
    }

    record Point(double lat, double lon, double value, String name, double heading) {
    }

    private final List<Config> configs;

    public PointValueImporter(List<Config> configs) {
        this.configs = configs;
    }

    /**
     * @param object the list of import.point_values entries, several entries may use the same encoded value
     */
    @SuppressWarnings("unchecked")
    public static List<Config> parseConfigs(Object object) {
        if (object == null) return List.of();
        if (!(object instanceof List<?> list))
            throw new IllegalArgumentException("import.point_values must be a list but was " + object);
        return list.stream().map(o -> {
            if (!(o instanceof Map))
                throw new IllegalArgumentException("Every import.point_values entry must be a map but was " + o);
            return Config.fromMap((Map<String, Object>) o);
        }).toList();
    }

    public void execute(BaseGraph graph, EncodedValueLookup lookup) {
        StopWatch sw = StopWatch.started();
        // TODO GraphHopper creates its index only later, after sorting and cleanUp. Moving initLocationIndex to the
        //  end of postImportOSM would allow to reuse it.
        LocationIndexTree index = new LocationIndexTree(graph, new GHDirectory("", DAType.RAM));
        index.prepareIndex();
        for (Config config : configs) {
            if (!lookup.hasEncodedValue(config.encodedValue))
                throw new IllegalArgumentException("import.point_values: encoded value " + config.encodedValue + " does not exist");
            apply(graph, index, lookup.getEncodedValue(config.encodedValue, EncodedValue.class), config, readPoints(config.file));
        }
        logger.info("point values took: " + sw.stop().getSeconds() + "s");
    }

    static List<Point> readPoints(String file) {
        try {
            JsonNode features = new ObjectMapper().readTree(new File(file)).get("features");
            if (features == null)
                throw new IllegalArgumentException("Point values must be a GeoJSON FeatureCollection: " + file);
            List<Point> points = new ArrayList<>(features.size());
            for (JsonNode feature : features) {
                JsonNode props = feature.path("properties"), coords = feature.path("geometry").path("coordinates");
                if (!"Point".equals(feature.path("geometry").path("type").asText()) || coords.size() < 2 || !props.hasNonNull("value"))
                    throw new IllegalArgumentException("Every feature needs a Point geometry and a value, but was " + feature + " in " + file);
                points.add(new Point(coords.get(1).asDouble(), coords.get(0).asDouble(), props.get("value").asDouble(),
                        props.path("name").asText(""), props.hasNonNull("heading") ? props.get("heading").asDouble() : Double.NaN));
            }
            return points;
        } catch (IOException ex) {
            throw new RuntimeException("Cannot read point values from " + file, ex);
        }
    }

    static void apply(BaseGraph graph, LocationIndexTree index, EncodedValue enc, Config config, List<Point> points) {
        if (!(enc instanceof IntEncodedValue intEnc) || enc instanceof EnumEncodedValue || enc instanceof BooleanEncodedValue)
            throw new IllegalArgumentException("import.point_values supports only numeric encoded values, but " + enc.getName() + " is not");
        // edge key -> picked value of all points on it
        IntDoubleHashMap values = new IntDoubleHashMap();
        int unmatched = 0;
        List<Point> unmatchedExamples = new ArrayList<>();
        for (Point p : points) {
            IntHashSet keys = findEdgeKeys(graph, index, p, config, enc.isStoreTwoDirections());
            if (keys.isEmpty()) {
                unmatched++;
                if (unmatchedExamples.size() < 3) unmatchedExamples.add(p);
                continue;
            }
            for (var key : keys) {
                double old = values.getOrDefault(key.value, Double.NaN);
                values.put(key.value, Double.isNaN(old) ? p.value : pick(config.pick, old, p.value));
            }
        }

        EdgeIntAccess access = graph.getEdgeAccess();
        int changed = 0;
        for (IntDoubleCursor c : values) {
            int edge = GHUtility.getEdgeFromEdgeKey(c.key);
            boolean reverse = c.key % 2 == 1;
            double current = get(intEnc, reverse, edge, access);
            double value = config.overwrite ? c.value : pick(config.pick, current, c.value);
            if (value == current) continue;
            set(intEnc, reverse, edge, access, value, config.pick);
            changed++;
        }
        logger.info("point values from {} for {}: points: {}, unmatched: {}, changed edges: {}",
                config.file, enc.getName(), points.size(), unmatched, changed);
        // the coordinates make it possible to look up on a map why a point was too far away or had the wrong name
        if (unmatched > 0) logger.info("unmatched points e.g. {}", unmatchedExamples.stream()
                .map(p -> p.lat + "," + p.lon + (p.name.isEmpty() ? "" : " (" + p.name + ")")).toList());
    }

    /**
     * @return keys of the closest edges within max_distance that pass the name and heading filter. Without a heading
     * (or for an encoded value with one direction) both directions of the edge are returned.
     */
    static IntHashSet findEdgeKeys(BaseGraph graph, LocationIndexTree index, Point p, Config config, boolean twoDirections) {
        IntHashSet edgeIds = new IntHashSet();
        index.query(DistanceCalcEarth.DIST_EARTH.createBBox(p.lat, p.lon, config.maxDistance), edgeIds::add);

        GHPoint point = new GHPoint(p.lat, p.lon);
        NameSimilarityEdgeFilter nameFilter = p.name.isEmpty() ? null
                : new NameSimilarityEdgeFilter(EdgeFilter.ALL_EDGES, p.name, point, config.maxDistance);
        IntHashSet keys = new IntHashSet();
        double best = Double.MAX_VALUE;
        for (var cursor : edgeIds) {
            EdgeIteratorState edge = graph.getEdgeIteratorState(cursor.value, Integer.MIN_VALUE);
            if (nameFilter != null && !nameFilter.accept(edge)) continue;
            double dist = distance(edge.fetchWayGeometry(FetchMode.ALL), point);
            if (dist > config.maxDistance || dist > best + SAME_DISTANCE) continue;
            boolean fwd = true, bwd = true;
            if (!Double.isNaN(p.heading)) {
                double heading = HeadingEdgeFilter.getHeadingOfGeometryNearPoint(edge, point, config.maxDistance);
                fwd = angleDiff(heading, p.heading) <= config.headingTolerance;
                bwd = angleDiff(heading + 180, p.heading) <= config.headingTolerance;
                if (!fwd && !bwd) continue;
                if (!twoDirections) fwd = bwd = true;
            }
            if (dist < best - SAME_DISTANCE) keys.clear();
            best = Math.min(best, dist);
            if (fwd) keys.add(edge.getEdgeKey());
            if (bwd && twoDirections) keys.add(edge.getReverseEdgeKey());
        }
        return keys;
    }

    private static double distance(PointList pl, GHPoint p) {
        DistanceCalc calc = DistanceCalcEarth.DIST_EARTH;
        double best = calc.calcDist(p.lat, p.lon, pl.getLat(0), pl.getLon(0));
        for (int i = 1; i < pl.size(); i++) {
            double d = calc.validEdgeDistance(p.lat, p.lon, pl.getLat(i - 1), pl.getLon(i - 1), pl.getLat(i), pl.getLon(i))
                    ? calc.calcDenormalizedDist(calc.calcNormalizedEdgeDistance(p.lat, p.lon, pl.getLat(i - 1), pl.getLon(i - 1), pl.getLat(i), pl.getLon(i)))
                    : calc.calcDist(p.lat, p.lon, pl.getLat(i), pl.getLon(i));
            best = Math.min(best, d);
        }
        return best;
    }

    private static double angleDiff(double a, double b) {
        double diff = Math.abs(a - b) % 360;
        return diff > 180 ? 360 - diff : diff;
    }

    private static double pick(Pick pick, double a, double b) {
        return pick == Pick.MIN ? Math.min(a, b) : Math.max(a, b);
    }

    private static double get(IntEncodedValue enc, boolean reverse, int edge, EdgeIntAccess access) {
        return enc instanceof DecimalEncodedValue dec ? dec.getDecimal(reverse, edge, access) : enc.getInt(reverse, edge, access);
    }

    /**
     * Rounds towards the safe side: down for min, up for max. Values outside the storable range are clamped.
     */
    private static void set(IntEncodedValue enc, boolean reverse, int edge, EdgeIntAccess access, double value, Pick pick) {
        if (enc instanceof DecimalEncodedValue dec) {
            value = Math.max(dec.getMinStorableDecimal(), Math.min(dec.getMaxStorableDecimal(), value));
            // getNextStorableValue rounds up, setDecimal rounds to the closest value
            dec.setDecimal(reverse, edge, access, pick == Pick.MAX ? dec.getNextStorableValue(value) : value);
            int i = enc.getInt(reverse, edge, access);
            if (pick == Pick.MIN && dec.getDecimal(reverse, edge, access) > value && i > enc.getMinStorableInt())
                enc.setInt(reverse, edge, access, i - 1);
        } else {
            long rounded = pick == Pick.MIN ? (long) Math.floor(value) : (long) Math.ceil(value);
            enc.setInt(reverse, edge, access, (int) Math.max(enc.getMinStorableInt(), Math.min(enc.getMaxStorableInt(), rounded)));
        }
    }
}
