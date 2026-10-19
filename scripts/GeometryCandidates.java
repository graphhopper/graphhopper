import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.reader.ReaderElement;
import com.graphhopper.reader.ReaderNode;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.reader.osm.OSMInputFile;
import com.graphhopper.reader.osm.SkipOptions;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

/** Bounded OSM metadata/geometry scan for real opposing carriageways and a local footbridge. */
class GeometryCandidates {
    private static final String SHA = "11f9b3f5b1d63cdf107f5804bd46131929dc2f13a1bd2837c6611429d8b9e4d3";
    private record Position(double lon, double lat) {
        List<Double> coordinates() { return List.of(lon, lat); }
    }
    private record Way(long id, long[] nodes, Map<String, Object> tags, boolean motorway, int direction) { }
    private record Segment(Way way, long from, long to, Position a, Position b, double x, double y, double length) { }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: GeometryCandidates.java <pinned.pbf> <output.json>");
        Path pbf = Path.of(args[0]).toAbsolutePath(), output = Path.of(args[1]).toAbsolutePath();
        if (!SHA.equals(sha256(pbf))) throw new IllegalArgumentException("Unexpected PBF checksum");
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("started_utc", Instant.now().toString());
        report.put("source_sha256", SHA);
        report.put("source_pbf", pbf.toString());
        report.put("graph_imported", false);
        report.put("pbf_worker_threads", 1);
        report.put("java_runtime", System.getProperty("java.runtime.version"));
        long started = System.nanoTime();
        List<Way> ways = new ArrayList<>();
        Set<Long> ids = new HashSet<>();
        scan(pbf, new SkipOptions(true, false, true), element -> {
            if (!(element instanceof ReaderWay way)) return;
            String highway = way.getTag("highway", ""), name = way.getTag("name", ""), ref = way.getTag("ref", "");
            boolean motorway = highway.equals("motorway") && (ref.contains("A57(M)") || name.equals("Mancunian Way"));
            boolean footbridge = Set.of("footway", "pedestrian", "path").contains(highway)
                    && !way.hasTag("area", "yes")
                    && (way.hasTag("bridge", "yes") || name.toLowerCase(Locale.ROOT).contains("trinity bridge"))
                    && !way.hasTag("access", "no", "private") && !way.hasTag("foot", "no", "private");
            if (!motorway && !footbridge) return;
            int direction = motorway ? direction(way) : 0;
            if (motorway && direction == 0) return;
            if (ways.size() >= 2000) throw new IllegalStateException("Way count bound exceeded");
            long[] nodes = way.getNodes().toArray();
            for (long id : nodes) ids.add(id);
            if (ids.size() > 100000) throw new IllegalStateException("Retained-node bound exceeded");
            ways.add(new Way(way.getId(), nodes, new TreeMap<>(way.getTags()), motorway, direction));
        });
        Map<Long, Position> positions = new HashMap<>();
        scan(pbf, new SkipOptions(false, true, true), element -> {
            if (element instanceof ReaderNode node && ids.contains(node.getId()))
                positions.put(node.getId(), new Position(node.getLon(), node.getLat()));
        });
        List<Segment> segments = new ArrayList<>();
        for (Way way : ways) {
            if (!way.motorway) continue;
            for (int i = 1; i < way.nodes.length; i++) {
                Position a = positions.get(way.nodes[i - 1]), b = positions.get(way.nodes[i]);
                if (a == null || b == null || !central(a) || !central(b)) continue;
                double[] vector = delta(a, b);
                double length = Math.hypot(vector[0], vector[1]);
                if (length >= 20) segments.add(new Segment(way, way.nodes[i - 1], way.nodes[i], a, b,
                        vector[0] / length * way.direction, vector[1] / length * way.direction, length));
            }
        }
        Map<String, Map<String, Object>> bestByWayPair = new HashMap<>();
        for (Segment a : segments) {
            Position first = interpolate(a.a, a.b, .5);
            for (Segment b : segments) {
                if (a.way.id >= b.way.id || !String.valueOf(a.way.tags.getOrDefault("layer", "0")).equals(String.valueOf(b.way.tags.getOrDefault("layer", "0")))) continue;
                double tangentDot = a.x * b.x + a.y * b.y;
                if (tangentDot > -.95) continue;
                double[] difference = delta(b.a, first), vector = delta(b.a, b.b);
                double fraction = (difference[0] * vector[0] + difference[1] * vector[1]) / (b.length * b.length);
                if (fraction < .1 || fraction > .9) continue;
                Position second = interpolate(b.a, b.b, fraction);
                double separation = distance(first, second);
                if (separation < 8 || separation > 50) continue;
                double score = distance(first, new Position(-2.2426, 53.4808));
                String pair = a.way.id + ":" + b.way.id;
                if (bestByWayPair.containsKey(pair) && ((Number) bestByWayPair.get(pair).get("distance_from_city_anchor_m")).doubleValue() <= score) continue;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("first_way_id", a.way.id);
                item.put("first_way_tags", a.way.tags);
                item.put("first_segment_node_ids", List.of(a.from, a.to));
                item.put("first_permitted_tangent", List.of(a.x, a.y));
                item.put("second_way_id", b.way.id);
                item.put("second_way_tags", b.way.tags);
                item.put("second_segment_node_ids", List.of(b.from, b.to));
                item.put("second_permitted_tangent", List.of(b.x, b.y));
                item.put("points_lon_lat", List.of(first.coordinates(), second.coordinates()));
                item.put("separation_m", separation);
                item.put("permitted_tangent_dot_product", tangentDot);
                item.put("distance_from_city_anchor_m", score);
                item.put("confirmation", "SOURCE_GEOMETRY_CONFIRMED; IMPORTED_SNAPS_AND_ROUTE_PENDING");
                bestByWayPair.put(pair, item);
            }
        }
        List<Map<String, Object>> opposing = bestByWayPair.values().stream()
                .sorted(Comparator.comparingDouble(item -> ((Number) item.get("distance_from_city_anchor_m")).doubleValue())).limit(10).toList();
        List<Map<String, Object>> footbridges = new ArrayList<>();
        for (Way way : ways) {
            if (way.motorway) continue;
            List<Position> geometry = new ArrayList<>();
            boolean complete = true;
            for (long node : way.nodes) {
                Position position = positions.get(node);
                if (position == null) { complete = false; break; }
                geometry.add(position);
            }
            if (!complete || geometry.size() < 2) continue;
            double length = 0;
            for (int i = 1; i < geometry.size(); i++) length += distance(geometry.get(i - 1), geometry.get(i));
            if (length < 15 || length > 400) continue;
            Position middle = along(geometry, length * .5);
            if (!central(middle)) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("osm_way_id", way.id);
            item.put("way_tags", way.tags);
            item.put("way_node_ids", way.nodes);
            item.put("way_geometry_lon_lat", geometry.stream().map(Position::coordinates).toList());
            item.put("points_lon_lat", List.of(along(geometry, length * .25).coordinates(), along(geometry, length * .75).coordinates()));
            item.put("way_length_m", length);
            item.put("distance_from_city_anchor_m", distance(middle, new Position(-2.2426, 53.4808)));
            item.put("confirmation", "OSM_FOOTWAY_GEOMETRY_CONFIRMED; IMPORTED_ACCESS_AND_ROUTE_PENDING");
            footbridges.add(item);
        }
        footbridges.sort(Comparator.comparingDouble(item -> ((Number) item.get("distance_from_city_anchor_m")).doubleValue()
                - (String.valueOf(((Map<?, ?>) item.get("way_tags")).get("name")).toLowerCase(Locale.ROOT).contains("trinity bridge") ? 10000 : 0)));
        report.put("retained_way_count", ways.size());
        report.put("retained_node_count", positions.size());
        report.put("motorway_segment_count", segments.size());
        report.put("opposing_carriageways", opposing);
        report.put("footways", footbridges.stream().limit(10).toList());
        report.put("scan_ms", (System.nanoTime() - started) / 1e6);
        report.put("completed_utc", Instant.now().toString());
        report.put("status", !opposing.isEmpty() && !footbridges.isEmpty() ? "SOURCE_CANDIDATES_CONFIRMED_ROUTES_PENDING" : "MISSING_CANDIDATE");
        Files.createDirectories(output.getParent());
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
        System.out.println("Opposing pairs=" + opposing.size() + ", footways=" + footbridges.size() + ", saved=" + output);
    }

    private static int direction(ReaderWay way) {
        String value = way.getTag("oneway", "yes"); // Selected motorway class has default one-way travel.
        if (Set.of("yes", "true", "1").contains(value)) return 1;
        if (Set.of("-1", "reverse").contains(value)) return -1;
        return 0;
    }

    private static boolean central(Position p) { return p.lon >= -2.28 && p.lon <= -2.20 && p.lat >= 53.455 && p.lat <= 53.505; }
    private static Position interpolate(Position a, Position b, double f) { return new Position(a.lon + (b.lon - a.lon) * f, a.lat + (b.lat - a.lat) * f); }
    private static double[] delta(Position a, Position b) { return new double[]{(b.lon - a.lon) * 111320 * Math.cos(Math.toRadians((a.lat + b.lat) / 2)), (b.lat - a.lat) * 111320}; }
    private static double distance(Position a, Position b) { double[] d = delta(a, b); return Math.hypot(d[0], d[1]); }
    private static Position along(List<Position> geometry, double metres) {
        for (int i = 1; i < geometry.size(); i++) {
            double length = distance(geometry.get(i - 1), geometry.get(i));
            if (length >= metres && length > 0) return interpolate(geometry.get(i - 1), geometry.get(i), metres / length);
            metres -= length;
        }
        return geometry.get(geometry.size() - 1);
    }
    private static void scan(Path pbf, SkipOptions skip, Consumer<ReaderElement> consumer) throws Exception {
        try (OSMInputFile input = new OSMInputFile(pbf.toFile()).setWorkerThreads(1).setSkipOptions(skip).open()) {
            ReaderElement element;
            while ((element = input.getNext()) != null) consumer.accept(element);
        }
    }
    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] bytes = new byte[1024 * 1024]; int size;
            while ((size = input.read(bytes)) >= 0) digest.update(bytes, 0, size);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
