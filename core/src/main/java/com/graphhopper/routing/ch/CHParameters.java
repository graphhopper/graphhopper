package com.graphhopper.routing.ch;

import com.graphhopper.util.Parameters;

public final class CHParameters {
    // graph contraction order etc. 
    public static final String PERIODIC_UPDATES = Parameters.CH.PREPARE + "updates.periodic";
    public static final String LAST_LAZY_NODES_UPDATES = Parameters.CH.PREPARE + "updates.lazy";
    public static final String NEIGHBOR_UPDATES = Parameters.CH.PREPARE + "updates.neighbor";
    public static final String CONTRACTED_NODES = Parameters.CH.PREPARE + "contracted_nodes";
    public static final String LOG_MESSAGES = Parameters.CH.PREPARE + "log_messages";
    // node contraction, node-based
    public static final String EDGE_DIFFERENCE_WEIGHT = Parameters.CH.PREPARE + "node.edge_difference_weight";
    public static final String ORIGINAL_EDGE_COUNT_WEIGHT = Parameters.CH.PREPARE + "node.original_edge_count_weight";
    public static final String MAX_VISITED_NODES_HEURISTIC = Parameters.CH.PREPARE + "node.max_visited_nodes_heuristic";
    public static final String MAX_VISITED_NODES_CONTRACTION = Parameters.CH.PREPARE + "node.max_visited_nodes_contraction";
    // node contraction, edge-based
    public static final String EDGE_QUOTIENT_WEIGHT = Parameters.CH.PREPARE + "edge.edge_quotient_weight";
    public static final String ORIGINAL_EDGE_QUOTIENT_WEIGHT = Parameters.CH.PREPARE + "edge.original_edge_quotient_weight";
    public static final String HIERARCHY_DEPTH_WEIGHT = Parameters.CH.PREPARE + "edge.hierarchy_depth_weight";
    public static final String SIGMA_FACTOR = Parameters.CH.PREPARE + "edge.witness_search.sigma_factor";
    public static final String MIN_MAX_SETTLED_EDGES = Parameters.CH.PREPARE + "edge.witness_search.min_max_settled_edges";
    public static final String SETTLED_EDGES_RESET_INTERVAL = Parameters.CH.PREPARE + "edge.witness_search.reset_interval";

    private CHParameters() {
    }
}
