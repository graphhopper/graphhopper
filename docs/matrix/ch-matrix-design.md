# Experimental node-based CH matrix design

This milestone adds an explicitly forced, fixed-profile experiment. **CH means Contraction Hierarchies**: preprocessing orders the graph's nodes and inserts shortcut edges so later searches can visit a much smaller part of the network. The experiment combines these prepared shortcuts across many origins and destinations. It is a local implementation of published ideas, not a claim about GraphHopper's commercial matrix service.

The selected public backend is `ch_experimental`. `auto` continues to select `flexible`. Turn-cost profiles continue to use the validated flexible backend, with an explicit fallback reason. A full flexible certificate is mandatory for every experimental request: the public response preserves the existing flexible matrix's weights, times, distances and nulls. Raw CH candidates are separately labelled diagnostic/benchmark results; their speed is not the speed of the certified public backend.

This note records the design and mathematical limits before benchmark results are known. The milestone report records measured results and implementation details.

## Primary research and selected-version inspection

The published CH construction preserves directed shortest paths using shortcuts and queries an upward graph from the source and the reversed downward graph from the target. Its many-to-many section explicitly reuses backward search spaces across sources. The theorem assumes that contraction preserves the same edge objective used by the query. [Geisberger, Sanders, Schultes and Delling, WEA 2008, sections 1, 4 and 5](https://ai.dmi.unibas.ch/research/reading_group/geisberger-et-al-wea2008.pdf). [Author institution publication record](https://ae.iti.kit.edu/english/999.php).

The bucket method performs a backward search for each destination, associates destination/distance entries with meeting nodes, and scans these entries during each source search. Its implementation discussion groups entries into flat arrays, and its experiments show that rectangular shapes and the number of overlapping search spaces affect cost. [Knopp, Sanders, Schultes, Schulz and Wagner, ALENEX 2007, sections 3 and 4](https://turing.iem.thm.de/routeplanning/hwy/distTable.pdf), linked from the [author's research page](https://turing.iem.thm.de/routeplanning/hwy/).

Local source inspection used GraphHopper tag 11.1, base commit `ebb578f4e94db72e82f2f8d3bc69417758e16e1c`, particularly:

- `RoutingCHGraph`: incoming/outgoing explorers include finite base edges and the stored upward shortcut orientation; callers must still filter base-edge rank.
- `AbstractBidirCHAlgo`: the level filter accepts real-node edges when `level(base) <= level(adj)` and always accepts edges incident to virtual nodes. CH termination differs from ordinary bidirectional Dijkstra.
- `QueryRoutingCHGraph`: request-local virtual nodes and shifted virtual edge IDs, replacement of split base edges in adjacency, unchanged shortcuts, directional virtual costs, and query-overlay weighting.
- `CHRoutingAlgorithmFactory`: node-based default is bidirectional Dijkstra with stall-on-demand; edge-based default is a different A* algorithm with directed edge states.
- `NodeBasedCHBidirPathExtractor` and `ShortcutUnpacker`: directional recursive expansion to original/virtual edge states; original elapsed time and distance are calculated during expansion.
- `CHPreparationGraph`, `NodeBasedNodeContractor`, `NodeBasedWitnessPathSearcher`, `CHStorage`: preparation base costs are cast to float, preparation shortcut costs remain double, and stored finite shortcut costs are quantized to 0.001 profile-weight-unit increments.
- `RandomCHRoutingTest`, `QueryRoutingCHGraphTest`, `CHQueryWithTurnCostsTest`, and `NodeBasedNodeContractorTest`: randomized QueryGraphs, virtual-edge remapping, directed shortcuts, restrictions, loops, meeting turns and shortcut-rounding cases. Upstream randomized aggregate discrepancy allowances are not adopted as this matrix backend's correctness policy.

## Directed bucket composition

Let `s` be a source, `t` a target, and `v` a meeting node. Let `F_s(v)` be the shortest label in the allowed source search graph, and `B_t(v)` be the shortest label in the allowed reverse target search graph. A backward label represents a path **from `v` to `t`**, although the search traverses incoming adjacency from `t`.

For each distinct valid snapped target:

1. Seed the target with zero weight, time and distance.
2. Run a complete reverse Dijkstra search with `createInEdgeExplorer()` and reverse-direction costs.
3. For each settled node `v`, store `(target, B_t(v), predecessor/path metrics)` in `bucket[v]`.

For each distinct valid source coordinate (exact raw-coordinate deduplication):

1. Seed the source at zero and run a complete forward search with `createOutEdgeExplorer()`.
2. On settlement of `v`, scan `bucket[v]`.
3. Consider `F_s(v) + B_t(v)` for each stored target and keep the lowest candidate. Time and distance belong to the same selected candidate path.

The two halves concatenate in the same directed orientation: `s -> ... -> v -> ... -> t`. There is no symmetry assumption and no transposition shortcut. Same-node meetings include zero-length halves, so same snapped nodes return zero even if no edge is relaxed. Inaccessible directions have infinite weight and are never relaxed. If no meeting candidate exists, the raw candidate is disconnected; the mandatory certificate establishes the public null result.

For an exact distance-preserving hierarchy, some optimum has an up-then-down representation. Its peak is present in both complete search spaces, so their intersection includes an optimum. Every joined candidate is a legal directed path, so none can be below the true optimum. The minimum is therefore the optimum. This argument requires the distance-preservation precondition; the existing float-prepared hierarchy does not establish it for arbitrary original double objectives.

The initial search deliberately omits stall-on-demand. Each search ends when its queue is empty. Stale queue entries are discarded, strict improvements are enqueued, and nonnegative finite costs ensure termination on the finite request graph. Zero costs do not require repeated equal-label insertions. The pairwise rule based on one current best target must not truncate a many-target source scope. A future early-stop optimization needs a separate lower-bound proof for every unresolved destination.

Request cancellation, the whole-request deadline and the aggregate settled-state limit cover target searches, source searches, metric expansion and certification. Limits apply to all work, not just the final flexible certificate. Exhaustion fails the complete request instead of returning partially filled matrices.

## Virtual points and batching

Create one complete `QueryGraph` from every distinct valid coordinate in the request, then wrap the selected prepared graph in `QueryRoutingCHGraph`. Do not create an independently snapped graph for each pair. Reuse the established exact-coordinate mapping and scatter order for repeated origins/destinations.

On real-to-real adjacency, retain the selected-version level filter. On any adjacency with a virtual endpoint, accept the edge independently of its synthetic level, exactly as the upstream CH query does. Treating the virtual node's `Integer.MAX_VALUE` as an ordinary rank would strand a source at its virtual point. Requiring monotone rank across a virtual chain would also mishandle several snaps on the same original edge.

The wrapper already removes split base edges from request adjacency, supplies replacement segments and remaps virtual edge IDs above the prepared CH edge range. Never infer a virtual edge ID from a base-graph ID using an unverified offset. Do not close the request wrapper: its `close()` closes the shared prepared graph.

The relaxed virtual filter can enlarge search scopes and admit paths that do not have a single strict rank peak. These remain legal candidate paths; complete search includes the required virtual attachments. It is not a reason to reuse a pairwise stopping test. Partial edge costs come from the actual request-local segments. Shortcuts may still expand to full original base edges. Splitting and storage precision can make their summed metrics differ slightly from a flexible traversal through several virtual segments. Such differences must be captured and corrected, not hidden by a larger tolerance. Repeated/same-edge points, added unrelated points and permutations are explicit certificate and test cases.

## Objective, elapsed time and distance

The route objective is profile-dependent. A shortcut's stored weight is neither guaranteed elapsed time nor distance. For each directed candidate transition, recursively expand the selected shortcut into its actual original edges, then calculate:

- objective: `weighting.calcEdgeWeight(edge, reverse)`;
- elapsed milliseconds: `weighting.calcEdgeMillis(edge, reverse)`;
- distance: the selected original/virtual edge's stored distance.

Use the original edge states and direction supplied by the selected-version unpacker. Reverse search evaluates the edge in the direction of the represented `v -> t` path, not in the direction in which the reverse search enumerates nodes. A direction-specific cached result cannot be reused for the opposite direction without computing it.

For node-based profiles these metrics compose additively; turn costs must be absent. The backend rejects or explicitly falls back for both edge-based preparation and any weighting/profile with turn costs. Node IDs alone cannot represent incoming-edge-dependent restrictions or the meeting turn. A separately designed edge-based backend would need directed edge states and the legal bridge turn, including U-turns and virtual-edge original-edge mapping.

Expanded objective costs can be used for candidate search to remove stored shortcut quantization from its priorities. This improves the raw candidate but does not repair omitted shortcuts. Totals must reject negative/nonfinite costs and detect integer-millisecond overflow. Floating-point summation order can differ between a shortcut tree and a flat path; the public contract uses the independent flexible certificate's complete metric triple.

## Why a certificate is required

An adversarial directed diamond demonstrates the topology issue. Use edges `s -> v -> t` with cost `10000.0001` per leg and `s -> w -> t` with cost `10000.0002` per leg. Contract in order `v, s, w, t`. All four costs cast to float as `10000.0`. When contracting `v`, the route through `w` is a tied witness, so the shortcut needed for the original optimum can be omitted. The exact optimum through `v` costs `20000.0002`, while a later upward query can retain only the route through `w`, costing `20000.0004`. Replacing shortcut costs after preprocessing cannot recreate the missing topology. This is a designed counterexample; the implementation's test report records its executed reproduction.

Consequently, for each distinct valid source coordinate the public experimental backend also runs a fresh `TargetSetDijkstra` on the same complete QueryGraph with the same profile weighting and target set. It compares reachability and the full objective/time/distance triple, records candidate differences/corrections, and always returns the flexible result. Equal objective alone does not justify swapping elapsed time or distance when optimal paths tie. Returning the certificate triple also preserves the validated flexible backend's tie behavior.

The certificate does not call or reuse the bucket search, shortcut metric cache, CH predecessor labels or raw candidate totals. A candidate error therefore cannot corrupt the answer. It is a full exact verification/correction cost, not a sampled check or an error-bound claim. `auto` remains flexible because the certified experiment necessarily adds work; any raw CH gain is evidence for possible further development, not permission to remove certification.

The diamond is reproduced by the executed test `BucketCHMatrixTest.floatWitnessCanLoseOriginalObjectiveAndCandidateMustBeCertified`. Besides the objective difference, the deliberately distinct candidate path has time/distance `300/300` versus the independent optimum's `30/30`. This shows why merely rewriting a reported weight cannot fix all returned metrics.

## Public selection and measurement metadata

The integration's actual behavior is:

| Request | Selected backend | Metadata |
|---|---|---|
| `auto` | `flexible` | `requested_algorithm: auto` |
| `flexible` | `flexible` | `requested_algorithm: flexible` |
| `reference` | `reference` | Independent upstream pairwise Dijkstra |
| `ch_experimental`, prepared static node-based profile | `ch_experimental` | `experimental: true`, `optimality_verification: flexible_each_origin` |
| `ch_experimental`, turn-cost profile | `flexible` | `fallback_reason: TURN_COST_PROFILE_UNSUPPORTED` |
| `ch_experimental`, no matching prepared graph | `flexible` | `fallback_reason: NO_PREPARED_CH` |
| `ch_experimental`, edge-based or turn-cost CH preparation | `flexible` | `fallback_reason: EDGE_BASED_CH_UNSUPPORTED` |

Experimental `info.local_matrix.ch_matrix` contains `backward_searches`, `forward_searches`, `candidate_visited_states`, `bucket_entries`, `metric_cache_entries`, `unpacked_original_edges`, `metric_extraction_ms`, `certificate_searches`, `certificate_visited_states`, `corrected_cells`, `corrected_cells_basis` and `candidate_optimality_guaranteed: false`. `ch_preparation` records the loaded graph/profile fingerprints, node/shortcut counts, source and node traversal. The ordinary request metadata retains the build and graph/profile fingerprints.

`corrected_cells_basis` is exactly `distinct_origin_coordinate_by_destination_index`: count each valid destination column for each searched distinct raw origin coordinate before duplicate-origin rows are scattered. Duplicate destination columns therefore count separately; duplicate origin coordinates count once. Distinct coordinates snapping to the same node can still trigger separate source/certificate searches. Any binary weight/distance difference, integer-time difference or reachability difference counts; this includes floating-point association and different equal-cost paths. A correction count is not a count of suboptimal raw paths. Invalid snaps are excluded. With no valid source or no valid target, no candidate/certificate search runs.

`timings_ms.ch_bucket_preparation` includes request wrapper creation, reverse searches, metric expansion and bucket construction. `ch_candidates` covers the forward candidate rows; `ch_verification` covers flexible searches. `metric_extraction_ms` overlaps the first two phases and must not be added again. Backend call time additionally includes setup, comparisons and mapping into output cells. Total `searches` and `visited_states` include reverse candidates, forward candidates and certificates. The shared request budget therefore charges all three kinds of search.

## Cache, preprocessing and memory

Direction-specific expanded shortcut metrics are valid only for the exact loaded base graph, profile model/weighting, prepared hierarchy and build. Cache provenance must bind these inputs. Virtual edge metrics are request-local because IDs and segment lengths depend on all snapped points. They must never persist under a bare edge-ID key. No runtime custom model, time-dependent adjustment or mutable turn data is supported.

The first experiment reuses the existing prepared graph. It does not claim that preparation is free: comparisons report import/CH preparation separately, additional metric expansion/cache construction, cache population/retention and request-local bucket memory. Every public request builds a fresh metric cache and fresh buckets; repeated measurements warm the JVM and loaded graph, not a cross-request metric cache. Allocation volume, retained cache size, heap snapshots and resident memory are different measurements and must be named accurately.

Request-local retained bucket entries scale with the sum of destination search-space sizes, not just the final matrix cell count. Source labels, predecessor/metric entries and final matrices add storage. A matrix with many repeated coordinates can have many output cells but few searches; a wide one-to-many request can instead be dominated by target preprocessing. These shapes need separate measurements. No cross-request destination bucket cache is introduced without a complete target/snap/provenance key and an explicit memory bound.

The implementation caps the metric cache's lifetime entry count at the initial remaining settled-state budget, including arcs encountered before adjacent nodes settle. Reverse searches share one aggregate settled-state budget, so the total retained bucket-entry count cannot exceed it. The high-degree cache-cap test ensures that many outgoing arcs cannot evade this bound by consuming memory before the next settlement. The experiment uses the same one-request admission slot as flexible routing; its mutable candidate/certificate state is request-local, and `finally` releases the admission semaphore on failure.

## Correctness and comparison gates

Applicable tests include deterministic directed/asymmetric/disconnected graphs; zero/same-node routes; optimal ties with different time/distance; multiple snaps on one edge; random QueryGraphs with frozen seeds; duplicated/permuted/subset axes; invalid snaps; cancellation/deadline/settled-state limits; profile fallback; and the adversarial float-witness case. Compare raw candidates with independent flexible Dijkstra and preserve every raw mismatch. Independently assert that every certified public output equals the flexible output, including exact integer times and null placement.

Benchmark the same requests in one JVM with warmup, repeated samples and rotated backend order: validated shared flexible search, certified experimental matrix, separately labelled raw bucket candidate, and strong in-process prepared-CH pairwise routing with minimal metrics/path extraction. Include snapping/QueryGraph construction, metric extraction and output costs consistently; additionally show the internal phases rather than subtracting inconvenient work. Record graph/profile/build/cache fingerprints, threads, heap, units, reachable/null cell counts, preprocessing and memory. HTTP comparisons supplement these measurements and cannot replace the in-process baseline.

No automatic selection threshold is justified by the design alone. A future uncertified accelerated backend requires exact-preserving preprocessing (including witness precision and cache versioning), its own adversarial/random/regional proof gates, complete metric aggregation and a measured crossover. Edge-based CH matrices remain a separate milestone.
