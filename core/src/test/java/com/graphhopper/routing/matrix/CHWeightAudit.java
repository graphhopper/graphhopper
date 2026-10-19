package com.graphhopper.routing.matrix;

import com.graphhopper.routing.SPTEntry;
import com.graphhopper.routing.ch.CHEntry;
import com.graphhopper.routing.ch.ShortcutUnpacker;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.RoutingCHEdgeIteratorState;
import com.graphhopper.storage.RoutingCHGraph;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Test-only accounting for the actual CH search trees supplied to its normal path extractor. */
public final class CHWeightAudit {
    private static final double MAX_WEIGHT = 4294967294L / 1000d;

    private CHWeightAudit() {
    }

    public record Audit(boolean proved, Map<String, Object> details) {
    }

    public static Audit analyze(RoutingCHGraph graph, SPTEntry fwd, SPTEntry bwd, double reportedWeight) {
        Map<String, Object> details = new LinkedHashMap<>();
        List<String> failures = new ArrayList<>();
        List<Map<String, Object>> selectedEdges = new ArrayList<>();
        List<Map<String, Object>> shortcuts = new ArrayList<>();
        details.put("method", "actual_default_CH_SPT_edges_and_upstream_ShortcutUnpacker");
        details.put("reported_weight", reportedWeight);
        details.put("selected_edges", selectedEdges);
        details.put("selected_shortcuts", shortcuts);
        details.put("failures", failures);
        try {
            require(fwd != null && bwd != null, "missing selected CH search entries");
            require(fwd.adjNode == bwd.adjNode, "selected entries meet at different nodes");
            require(finite(reportedWeight), "reported weight is not finite/nonnegative");
            Weighting wrapped = graph.getBaseGraph().wrapWeighting(graph.getWeighting());
            Totals totals = new Totals();
            double fwdWeight = chain(graph, wrapped, fwd, false, selectedEdges, shortcuts, totals, failures);
            double bwdWeight = chain(graph, wrapped, bwd, true, selectedEdges, shortcuts, totals, failures);
            int in = incoming(graph, fwd);
            int out = incoming(graph, bwd);
            double bridge = graph.isEdgeBased() && valid(in) && valid(out)
                    ? graph.getTurnWeight(in, fwd.adjNode, out) : 0;
            require(finite(bridge), "illegal/nonfinite selected meeting turn");
            double reconstructed = (fwdWeight + bwdWeight) + bridge;
            require(same(reconstructed, reportedWeight), "selected CH terms do not exactly reconstruct reported weight");
            totals.stored = totals.stored.add(exact(bridge));
            totals.expanded = totals.expanded.add(exact(bridge));
            BigDecimal queryArithmetic = exact(reportedWeight).subtract(totals.stored);
            BigDecimal expandedResidual = exact(reportedWeight).subtract(totals.expanded);
            BigDecimal explained = totals.shortcutResidual.add(queryArithmetic);
            require(expandedResidual.compareTo(explained) == 0, "shortcut residual ledger does not telescope");
            require(expandedResidual.abs().compareTo(totals.bound.add(queryArithmetic.abs())) <= 0,
                    "expanded selected path exceeds its derived shortcut/arithmetic bound");
            details.put("meeting_node", fwd.adjNode);
            details.put("meeting_turn", map("in_edge", in, "out_edge", out, "weight", bridge));
            details.put("forward_chain_weight", fwdWeight);
            details.put("backward_chain_weight", bwdWeight);
            details.put("reconstructed_reported_weight", reconstructed);
            details.put("exact_selected_stored_terms_sum", totals.stored.toPlainString());
            details.put("exact_selected_expanded_terms_sum", totals.expanded.toPlainString());
            details.put("exact_shortcut_residual_sum", totals.shortcutResidual.toPlainString());
            details.put("exact_query_addition_residual", queryArithmetic.toPlainString());
            details.put("exact_reported_minus_expanded", expandedResidual.toPlainString());
            details.put("derived_absolute_bound", totals.bound.add(queryArithmetic.abs()).toPlainString());
        } catch (RuntimeException exception) {
            failures.add(exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
        details.put("proved", failures.isEmpty());
        return new Audit(failures.isEmpty(), details);
    }

    private static double chain(RoutingCHGraph graph, Weighting wrapped, SPTEntry selected, boolean reverse,
                                List<Map<String, Object>> selectedEdges, List<Map<String, Object>> shortcuts,
                                Totals totals, List<String> failures) {
        List<SPTEntry> entries = new ArrayList<>();
        Set<SPTEntry> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        SPTEntry current = selected;
        while (valid(current.edge)) {
            require(seen.add(current), "cycle in selected CH search parents");
            require(current.parent != null, "selected CH edge has no parent");
            entries.add(current);
            current = current.parent;
        }
        require(same(current.getWeightOfVisitedPath(), 0), "nonzero CH root weight");
        Collections.reverse(entries);
        double cumulative = 0;
        for (SPTEntry entry : entries) {
            RoutingCHEdgeIteratorState edge = graph.getEdgeIteratorState(entry.edge, entry.adjNode);
            require(edge != null && edge.getBaseNode() == entry.parent.adjNode, "selected CH edge does not join its parent");
            int previous = incoming(graph, entry.parent);
            int boundaryOriginal = GHUtility.getEdgeFromEdgeKey(reverse ? edge.getOrigEdgeKeyLast() : edge.getOrigEdgeKeyFirst());
            double edgeWeight = edge.getWeight(reverse);
            double turn = reverse ? graph.getTurnWeight(boundaryOriginal, edge.getBaseNode(), previous)
                    : graph.getTurnWeight(previous, edge.getBaseNode(), boundaryOriginal);
            require(finite(edgeWeight) && finite(turn), "illegal/nonfinite selected CH edge or boundary turn");
            cumulative = (edgeWeight + turn) + cumulative;
            require(same(cumulative, entry.getWeightOfVisitedPath()), "selected CH entry weight differs from its actual local terms");
            Map<String, Object> term = map("edge", edge.getEdge(), "base", edge.getBaseNode(), "adj", edge.getAdjNode(),
                    "reverse_search", reverse, "shortcut", edge.isShortcut(), "edge_weight", edgeWeight,
                    "connecting_turn", turn, "parent_incoming_edge", previous,
                    "boundary_original_edge", boundaryOriginal, "cumulative_visited_weight", cumulative);
            selectedEdges.add(term);
            totals.stored = totals.stored.add(exact(edgeWeight)).add(exact(turn));
            totals.expanded = totals.expanded.add(exact(turn));
            if (edge.isShortcut()) {
                ShortcutCost cost = shortcut(graph, wrapped, edge, reverse);
                shortcuts.add(cost.details);
                totals.expanded = totals.expanded.add(cost.expanded);
                totals.shortcutResidual = totals.shortcutResidual.add(exact(edgeWeight).subtract(cost.expanded));
                totals.bound = totals.bound.add(cost.bound);
                if (!cost.proved)
                    failures.add("selected shortcut " + edge.getEdge() + " is outside its explicit storage/preparation bound");
            } else {
                EdgeIteratorState original = graph.getBaseGraph().getEdgeIteratorState(edge.getOrigEdge(), edge.getAdjNode());
                require(original != null, "missing selected original/virtual edge");
                double originalWeight = wrapped.calcEdgeWeight(original, reverse);
                require(finite(originalWeight) && same(originalWeight, edgeWeight), "selected original/virtual weight differs from wrapped query weighting");
                // Virtual CH IDs are shifted by the shortcut count. No float preparation conversion applies here.
                totals.expanded = totals.expanded.add(exact(originalWeight));
            }
        }
        return cumulative;
    }

    private static ShortcutCost shortcut(RoutingCHGraph graph, Weighting wrapped, RoutingCHEdgeIteratorState selected,
                                         boolean reverse) {
        List<Map<String, Object>> leaves = new ArrayList<>();
        BigDecimal[] expanded = {BigDecimal.ZERO};
        BigDecimal[] prepared = {BigDecimal.ZERO};
        BigDecimal[] floatAbsolute = {BigDecimal.ZERO};
        double[] maxSourceTurn = {0};
        ShortcutUnpacker unpacker = new ShortcutUnpacker(graph, (edge, leafReverse, previous) -> {
            double weight = wrapped.calcEdgeWeight(edge, leafReverse);
            double turn = valid(previous) ? (leafReverse
                    ? wrapped.calcTurnWeight(edge.getEdge(), edge.getBaseNode(), previous)
                    : wrapped.calcTurnWeight(previous, edge.getBaseNode(), edge.getEdge())) : 0;
            require(finite(weight) && finite(turn), "illegal/nonfinite expanded shortcut edge or turn");
            double cached = (double) (float) weight;
            require(finite(cached), "float preparation base weight overflows");
            BigDecimal floatResidual = exact(cached).subtract(exact(weight));
            expanded[0] = expanded[0].add(exact(weight)).add(exact(turn));
            prepared[0] = prepared[0].add(exact(cached)).add(exact(turn));
            floatAbsolute[0] = floatAbsolute[0].add(floatResidual.abs());
            int source = leafReverse ? edge.getAdjNode() : edge.getBaseNode();
            EdgeIterator incoming = graph.getBaseGraph().createEdgeExplorer().setBaseNode(source);
            while (incoming.next()) {
                if (!finite(wrapped.calcEdgeWeight(incoming, true)))
                    continue;
                double initial = wrapped.calcTurnWeight(incoming.getEdge(), source, edge.getEdge());
                if (finite(initial))
                    maxSourceTurn[0] = Math.max(maxSourceTurn[0], initial);
            }
            leaves.add(map("edge", edge.getEdge(), "directed_edge_key", leafReverse ? edge.getReverseEdgeKey() : edge.getEdgeKey(),
                    "base", edge.getBaseNode(), "adj", edge.getAdjNode(), "reverse", leafReverse,
                    "previous_or_next_edge", previous, "edge_weight", weight, "internal_turn", turn,
                    "preparation_float_edge_weight", cached, "exact_float_residual", floatResidual.toPlainString()));
        }, graph.isEdgeBased());
        if (reverse)
            unpacker.visitOriginalEdgesBwd(selected.getEdge(), selected.getAdjNode(), false, EdgeIterator.NO_EDGE);
        else
            unpacker.visitOriginalEdgesFwd(selected.getEdge(), selected.getAdjNode(), false, EdgeIterator.NO_EDGE);
        require(leaves.size() >= 2, "shortcut expands into fewer than two original edges");

        long leafCount = leaves.size();
        long internalCount = leafCount - 1; // Every upstream skipped1/skipped2 expansion is binary; count occurrences, not IDs.
        // Source accounting for this selected tree, not a tolerance on the observed discrepancy:
        // CHPreparationGraph stores float base costs and double shortcut costs. BridgePathFinder adds
        // (current + turn) + segmentWeight: two adds per preparation segment. Each historical bridge
        // represented in this binary expansion has at most n segments, because every segment expands
        // into at least one of these n leaf occurrences. EdgeBasedNodeContractor then removes one
        // initial source turn. Across n-1 internal occurrences this is at most (2*n+1)*(n-1)
        // additions/subtractions. 8*n^2 conservatively also covers regrouping cumulative loop prefixes
        // and the comparison sum's parenthesization. Stored child milliweights are never fed back into
        // preparation. NodeBasedNodeContractor merely adds its two prepare weights: n-1 operations.
        long operations = graph.isEdgeBased() ? Math.multiplyExact(8, Math.multiplyExact(leafCount, leafCount)) : internalCount;
        double unitRoundoff = Math.scalb(1d, -53);
        require(operations * unitRoundoff < 0.5, "shortcut tree too large for arithmetic bound");
        double gamma = Math.nextUp((operations * unitRoundoff) / (1 - operations * unitRoundoff));
        // Initial source turns are added, then subtracted by EdgeBasedNodeContractor. They are absent
        // from the final tree's costs, but must be present in an absolute roundoff magnitude bound.
        BigDecimal magnitude = prepared[0].add(exact(maxSourceTurn[0]).multiply(BigDecimal.valueOf(2 * internalCount)));
        BigDecimal arithmeticBound = exact(gamma).multiply(magnitude);
        double center = prepared[0].doubleValue();
        require(finite(center), "expanded preparation sum is not finite");
        arithmeticBound = arithmeticBound.add(exact(Math.ulp(center)));
        double lower = Math.max(0, Math.nextDown(center - arithmeticBound.doubleValue()));
        double upper = Math.nextUp(center + arithmeticBound.doubleValue());
        require(upper < MAX_WEIGHT, "selected shortcut reaches CHStorage infinity/saturation boundary");
        double roundedLower = storedWeight(lower);
        double roundedUpper = storedWeight(upper);
        double stored = selected.getWeight(reverse);
        boolean uniquePrediction = same(roundedLower, roundedUpper) && same(stored, roundedLower);
        boolean proved = uniquePrediction;
        BigDecimal residual = exact(stored).subtract(expanded[0]);
        // Storage clamps sub-millisecond inputs to one millisecond. For normal inputs its rounding
        // error is half a millisecond plus explicitly bounded binary multiply/divide roundoff.
        double quantizationBound = upper < 0.001 ? 0.001
                : Math.max(0.0005, Math.max(0, 0.001 - lower));
        quantizationBound = Math.nextUp(quantizationBound + Math.ulp(upper * 1000) / 1000 + Math.ulp(stored));
        BigDecimal bound = floatAbsolute[0].add(arithmeticBound).add(exact(quantizationBound));
        proved &= residual.abs().compareTo(bound) <= 0;
        Map<String, Object> details = map("edge", selected.getEdge(), "base", selected.getBaseNode(), "adj", selected.getAdjNode(),
                "reverse_search", reverse, "stored_weight", stored, "skipped1", selected.getSkippedEdge1(), "skipped2", selected.getSkippedEdge2(),
                "original_edge_occurrences", leafCount, "internal_shortcut_occurrences", internalCount,
                "exact_unrounded_expanded_cost", expanded[0].toPlainString(), "exact_float_preparation_terms_sum", prepared[0].toPlainString(),
                "exact_float_leaf_residual_sum", prepared[0].subtract(expanded[0]).toPlainString(),
                "float_leaf_absolute_residual_sum", floatAbsolute[0].toPlainString(), "preparation_arithmetic_operation_bound", operations,
                "max_finite_source_initial_turn", maxSourceTurn[0], "preparation_arithmetic_absolute_bound", arithmeticBound.toPlainString(),
                "possible_pre_storage_lower", lower, "possible_pre_storage_upper", upper,
                "CHStorage_rounded_lower", roundedLower, "CHStorage_rounded_upper", roundedUpper,
                "unique_predicted_stored_weight", uniquePrediction,
                "preparation_operation_bound_derivation", graph.isEdgeBased()
                        ? "n actual leaf occurrences; n-1 binary internal occurrences; <=n bridge segments per internal; two additions per segment plus one initial-turn subtraction; (2*n+1)*(n-1) <= 8*n^2"
                        : "n actual leaf occurrences; each of n-1 binary internal occurrences adds two double prepare weights",
                "storage_rounding_absolute_bound", quantizationBound, "exact_stored_minus_expanded", residual.toPlainString(),
                "derived_absolute_bound", bound.toPlainString(), "proved", proved, "expanded_terms", leaves);
        return new ShortcutCost(proved, expanded[0], bound, details);
    }

    private static double storedWeight(double weight) {
        return Math.round(Math.max(0.001, weight) * 1000) / 1000d;
    }

    private static int incoming(RoutingCHGraph graph, SPTEntry entry) {
        return graph.isEdgeBased() ? ((CHEntry) entry).incEdge : entry.edge;
    }

    private static boolean valid(int edge) {
        return EdgeIterator.Edge.isValid(edge);
    }

    private static boolean finite(double value) {
        return Double.isFinite(value) && value >= 0;
    }

    private static boolean same(double first, double second) {
        return first == second;
    }

    private static BigDecimal exact(double value) {
        return new BigDecimal(value);
    }

    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2)
            map.put((String) pairs[i], pairs[i + 1]);
        return map;
    }

    private static void require(boolean condition, String message) {
        if (!condition)
            throw new IllegalStateException(message);
    }

    private record ShortcutCost(boolean proved, BigDecimal expanded, BigDecimal bound, Map<String, Object> details) {
    }

    private static class Totals {
        BigDecimal stored = BigDecimal.ZERO;
        BigDecimal expanded = BigDecimal.ZERO;
        BigDecimal shortcutResidual = BigDecimal.ZERO;
        BigDecimal bound = BigDecimal.ZERO;
    }
}
