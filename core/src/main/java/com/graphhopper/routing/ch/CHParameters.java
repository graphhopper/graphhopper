package com.graphhopper.routing.ch;

import com.graphhopper.core.util.Parameters;

public final class CHParameters {
    // graph contraction order etc. 
    public static final String PERIODIC_UPDATES = Parameters.CH.PREPARE + "updates.periodic";
    public static final String LAST_LAZY_NODES_UPDATES = Parameters.CH.PREPARE + "updates.lazy";
    public static final String NEIGHBOR_UPDATES = Parameters.CH.PREPARE + "updates.neighbor";
    public static final String NEIGHBOR_UPDATES_MAX = Parameters.CH.PREPARE + "updates.neighbor_max";
    public static final String CONTRACTED_NODES = Parameters.CH.PREPARE + "contracted_nodes";
    public static final String LOG_MESSAGES = Parameters.CH.PREPARE + "log_messages";
    // node contraction, node-based
    public static final String EDGE_DIFFERENCE_WEIGHT = Parameters.CH.PREPARE + "node.edge_difference_weight";
    public static final String ORIGINAL_EDGE_COUNT_WEIGHT = Parameters.CH.PREPARE + "node.original_edge_count_weight";
    public static final String MAX_POLL_FACTOR_HEURISTIC_NODE = Parameters.CH.PREPARE + "node.max_poll_factor_heuristic";
    public static final String MAX_POLL_FACTOR_CONTRACTION_NODE = Parameters.CH.PREPARE + "node.max_poll_factor_contraction";
    // node contraction, edge-based
    public static final String EDGE_QUOTIENT_WEIGHT = Parameters.CH.PREPARE + "edge.edge_quotient_weight";
    public static final String ORIGINAL_EDGE_QUOTIENT_WEIGHT = Parameters.CH.PREPARE + "edge.original_edge_quotient_weight";
    public static final String HIERARCHY_DEPTH_WEIGHT = Parameters.CH.PREPARE + "edge.hierarchy_depth_weight";
    public static final String MAX_POLL_FACTOR_HEURISTIC_EDGE = Parameters.CH.PREPARE + "edge.max_poll_factor_heuristic";
    public static final String MAX_POLL_FACTOR_CONTRACTION_EDGE = Parameters.CH.PREPARE + "edge.max_poll_factor_contraction";

    private CHParameters() {
    }
}
