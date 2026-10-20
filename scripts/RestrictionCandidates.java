import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.reader.ReaderElement;
import com.graphhopper.reader.ReaderNode;
import com.graphhopper.reader.ReaderRelation;
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

/** Metadata-only PBF audit. Does not instantiate GraphHopper, import a graph or alter source data. */
class RestrictionCandidates {
    private static final String PINNED_SHA256 = "11f9b3f5b1d63cdf107f5804bd46131929dc2f13a1bd2837c6611429d8b9e4d3";
    private static final Set<String> TYPES = Set.of("no_left_turn", "no_right_turn", "no_straight_on");
    private static final Set<String> HIGHWAYS = Set.of("trunk", "primary", "secondary", "tertiary", "unclassified", "residential", "service",
            "trunk_link", "primary_link", "secondary_link", "tertiary_link");
    private static final int MAX_RESTRICTIONS = 5000, MAX_CENTRAL = 80, MAX_NODES = 100000;
    private static final ObjectMapper JSON = new ObjectMapper();
    private record Restriction(long id, String type, long from, long via, long to, Map<String, Object> tags) { }
    private record Way(long id, long[] nodes, Map<String, Object> tags) { }
    private record Position(double lon, double lat) {
        List<Double> coordinate() { return List.of(lon, lat); }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.out.println("Usage: java --class-path web/target/graphhopper-web-11.0-SNAPSHOT.jar scripts/RestrictionCandidates.java <pinned.pbf> <osm-import-warnings.json> <output.json>");
            return;
        }
        Path pbf = Path.of(args[0]).toAbsolutePath(), warnings = Path.of(args[1]).toAbsolutePath(), output = Path.of(args[2]).toAbsolutePath();
        if (!PINNED_SHA256.equals(sha256(pbf))) throw new IllegalArgumentException("PBF SHA-256 differs from the acquired fixture");
        Set<Long> ignored = new HashSet<>();
        JsonNode ignoredReport = JSON.readTree(warnings.toFile());
        if (!PINNED_SHA256.equals(ignoredReport.path("source_pbf_sha256").asText())) throw new IllegalArgumentException("Ignored-relation manifest belongs to a different PBF");
        ignoredReport.path("restriction_warnings").forEach(item -> ignored.add(item.path("relation_id").asLong()));
        List<Restriction> restrictions = new ArrayList<>();
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("status", "METADATA_SCAN_RUNNING");
        report.put("started_utc", Instant.now().toString());
        report.put("source_pbf", pbf.toString());
        report.put("source_sha256", PINNED_SHA256);
        report.put("ignored_relation_manifest", warnings.toString());
        report.put("ignored_relation_manifest_sha256", sha256(warnings));
        report.put("worker_threads", 1);
        report.put("java_runtime", System.getProperty("java.runtime.version"));
        report.put("graph_imported", false);
        report.put("bounds", Map.of("max_relations", MAX_RESTRICTIONS, "max_central_relations", MAX_CENTRAL, "max_retained_way_nodes", MAX_NODES));
        long started = System.nanoTime();
        scan(pbf, new SkipOptions(true, true, false), element -> {
            if (!(element instanceof ReaderRelation relation) || !relation.hasTag("type", "restriction")) return;
            String type = relation.getTag("restriction");
            if (type == null || !TYPES.contains(type) || ignored.contains(relation.getId()) || relation.hasTag("except")) return;
            if (relation.getTags().keySet().stream().anyMatch(key -> key.startsWith("restriction:") || key.contains("conditional"))) return;
            if (relation.getMembers().size() != 3) return;
            Map<String, ReaderRelation.Member> members = new HashMap<>();
            for (ReaderRelation.Member member : relation.getMembers()) {
                if (members.put(member.getRole(), member) != null) return;
            }
            ReaderRelation.Member from = members.get("from"), via = members.get("via"), to = members.get("to");
            if (from == null || via == null || to == null || from.getType() != ReaderElement.Type.WAY ||
                    via.getType() != ReaderElement.Type.NODE || to.getType() != ReaderElement.Type.WAY || from.getRef() == to.getRef()) return;
            if (restrictions.size() >= MAX_RESTRICTIONS) throw new IllegalStateException("Candidate relation bound exceeded; no silent truncation");
            restrictions.add(new Restriction(relation.getId(), type, from.getRef(), via.getRef(), to.getRef(), new TreeMap<>(relation.getTags())));
        });
        report.put("simple_nonignored_relations", restrictions.size());
        System.out.println("Relation metadata candidates: " + restrictions.size());
        Set<Long> viaIds = new HashSet<>();
        restrictions.forEach(restriction -> viaIds.add(restriction.via));
        Map<Long, Position> positions = new HashMap<>();
        scan(pbf, new SkipOptions(false, true, true), element -> {
            if (element instanceof ReaderNode node && viaIds.contains(node.getId()))
                positions.put(node.getId(), new Position(node.getLon(), node.getLat()));
        });
        List<Restriction> central = restrictions.stream().filter(restriction -> {
            Position point = positions.get(restriction.via);
            return point != null && point.lon >= -2.28 && point.lon <= -2.20 && point.lat >= 53.455 && point.lat <= 53.505;
        }).sorted(Comparator.comparingDouble(restriction -> distance(positions.get(restriction.via), new Position(-2.2426, 53.4808))))
                .limit(MAX_CENTRAL).toList();
        report.put("selected_central_relations", central.size());
        Set<Long> wayIds = new HashSet<>();
        central.forEach(restriction -> { wayIds.add(restriction.from); wayIds.add(restriction.to); });
        Map<Long, Way> ways = new HashMap<>();
        Set<Long> nodeIds = new HashSet<>();
        scan(pbf, new SkipOptions(true, false, true), element -> {
            if (!(element instanceof ReaderWay way) || !wayIds.contains(way.getId())) return;
            long[] nodes = way.getNodes().toArray();
            if (nodes.length > MAX_NODES) throw new IllegalStateException("Individual member-way node bound exceeded");
            ways.put(way.getId(), new Way(way.getId(), nodes, new TreeMap<>(way.getTags())));
            for (long node : nodes) nodeIds.add(node);
            if (nodeIds.size() > MAX_NODES) throw new IllegalStateException("Retained node bound exceeded; no silent truncation");
        });
        scan(pbf, new SkipOptions(false, true, true), element -> {
            if (element instanceof ReaderNode node && nodeIds.contains(node.getId()))
                positions.put(node.getId(), new Position(node.getLon(), node.getLat()));
        });
        List<Map<String, Object>> candidates = new ArrayList<>();
        for (Restriction restriction : central) {
            Way from = ways.get(restriction.from), to = ways.get(restriction.to);
            if (!drivable(from) || !drivable(to)) continue;
            int fromVia = endpoint(from.nodes, restriction.via), toVia = endpoint(to.nodes, restriction.via);
            if (fromVia < 0 || toVia < 0 || !directionAllowed(from, fromVia == from.nodes.length - 1) || !directionAllowed(to, toVia == 0)) continue;
            Position start = approach(from.nodes, fromVia, positions, 30), finish = approach(to.nodes, toVia, positions, 30);
            Position via = positions.get(restriction.via);
            if (start == null || finish == null || via == null || distance(start, via) < 5 || distance(finish, via) < 5 || distance(start, finish) < 5) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("relation_id", restriction.id);
            item.put("relation_url", "https://www.openstreetmap.org/relation/" + restriction.id);
            item.put("restriction", restriction.type);
            item.put("relation_tags", restriction.tags);
            item.put("via_osm_node", restriction.via);
            item.put("via_coordinate_lon_lat", via.coordinate());
            item.put("from_osm_way", from.id);
            item.put("from_way_tags", from.tags);
            item.put("to_osm_way", to.id);
            item.put("to_way_tags", to.tags);
            item.put("from_way_node_ids", from.nodes);
            item.put("to_way_node_ids", to.nodes);
            item.put("origin_coordinate_lon_lat", start.coordinate());
            item.put("destination_coordinate_lon_lat", finish.coordinate());
            item.put("origin_distance_from_via_m", distance(start, via));
            item.put("destination_distance_from_via_m", distance(finish, via));
            item.put("in_recorded_ignored_relation_ids", false);
            item.put("manual_import_and_detour_confirmation", "PENDING");
            item.put("route_request", Map.of("profile", "car", "points", List.of(start.coordinate(), finish.coordinate()),
                    "algorithm", "dijkstra", "ch.disable", true, "lm.disable", true, "calc_points", true,
                    "points_encoded", false, "instructions", true, "snap_preventions", List.of()));
            item.put("matrix_request", Map.of("profile", "car", "from_points", List.of(start.coordinate()), "to_points", List.of(finish.coordinate()),
                    "out_arrays", List.of("weights", "times_ms", "times", "distances"), "fail_fast", true, "local", Map.of("algorithm", "flexible")));
            candidates.add(item);
            if (candidates.size() == 10) break;
        }
        report.put("status", candidates.isEmpty() ? "NO_USABLE_CANDIDATE" : "CANDIDATES_NOT_YET_ROUTED");
        report.put("candidate_count", candidates.size());
        report.put("candidates", candidates);
        report.put("retained_way_count", ways.size());
        report.put("retained_node_count", positions.size());
        report.put("scan_ms", (System.nanoTime() - started) / 1e6);
        report.put("completed_utc", Instant.now().toString());
        report.put("limitations", List.of("Absence from ignored-relation warnings is supporting evidence, not proof that the restriction was imported.",
                "Coordinates follow actual member-way geometry approximately 30 metres from the via node. Verify snaps and the detour on the running imported graph.",
                "Use two-point routes for the turn check. Adding the via node as a route waypoint can reset endpoint turn history."));
        Files.createDirectories(output.getParent());
        JSON.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
        System.out.println("Saved " + candidates.size() + " candidates to " + output);
    }

    private static void scan(Path pbf, SkipOptions skip, Consumer<ReaderElement> consumer) throws Exception {
        try (OSMInputFile input = new OSMInputFile(pbf.toFile()).setWorkerThreads(1).setSkipOptions(skip).open()) {
            ReaderElement element;
            while ((element = input.getNext()) != null) consumer.accept(element);
        }
    }

    private static boolean drivable(Way way) {
        if (way == null || way.nodes.length < 2 || !HIGHWAYS.contains(String.valueOf(way.tags.get("highway"))) || "roundabout".equals(way.tags.get("junction"))) return false;
        for (String key : List.of("access", "vehicle", "motor_vehicle", "motorcar"))
            if (Set.of("no", "private", "agricultural", "forestry", "delivery").contains(String.valueOf(way.tags.get(key)))) return false;
        return way.tags.keySet().stream().noneMatch(key -> key.contains("conditional"));
    }

    private static int endpoint(long[] nodes, long via) {
        int found = -1;
        for (int index = 0; index < nodes.length; index++)
            if (nodes[index] == via) { if (found >= 0) return -1; found = index; }
        return found == 0 || found == nodes.length - 1 ? found : -1;
    }

    private static boolean directionAllowed(Way way, boolean forward) {
        String oneWay = String.valueOf(way.tags.getOrDefault("oneway", "no"));
        if (Set.of("yes", "1", "true").contains(oneWay)) return forward;
        if (Set.of("-1", "reverse").contains(oneWay)) return !forward;
        return true;
    }

    private static Position approach(long[] nodes, int via, Map<Long, Position> coordinates, double metres) {
        int step = via == 0 ? 1 : -1;
        Position previous = coordinates.get(nodes[via]);
        if (previous == null) return null;
        double total = 0;
        for (int index = via + step; index >= 0 && index < nodes.length; index += step) {
            Position next = coordinates.get(nodes[index]);
            if (next == null) return null;
            total += distance(previous, next);
            previous = next;
        }
        if (total <= 0) return null;
        // Keep raw endpoints in the interior of each member way, including short ways.
        metres = Math.min(metres, total * .75);
        previous = coordinates.get(nodes[via]);
        for (int index = via + step; index >= 0 && index < nodes.length; index += step) {
            Position next = coordinates.get(nodes[index]);
            if (next == null) return null;
            double length = distance(previous, next);
            if (length >= metres && length > 0) {
                double fraction = metres / length;
                return new Position(previous.lon + (next.lon - previous.lon) * fraction, previous.lat + (next.lat - previous.lat) * fraction);
            }
            metres -= length;
            previous = next;
        }
        return null;
    }

    private static double distance(Position a, Position b) {
        double dy = (b.lat - a.lat) * 111320, dx = (b.lon - a.lon) * 111320 * Math.cos(Math.toRadians((a.lat + b.lat) / 2));
        return Math.hypot(dx, dy);
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[1024 * 1024];
            int size;
            while ((size = input.read(buffer)) >= 0) digest.update(buffer, 0, size);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
