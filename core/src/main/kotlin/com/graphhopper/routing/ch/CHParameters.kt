package com.graphhopper.routing.ch

import com.graphhopper.util.Parameters

object CHParameters {
    // graph contraction order etc.
    const val PERIODIC_UPDATES: String = Parameters.CH.PREPARE + "updates.periodic"
    const val LAST_LAZY_NODES_UPDATES: String = Parameters.CH.PREPARE + "updates.lazy"
    const val NEIGHBOR_UPDATES: String = Parameters.CH.PREPARE + "updates.neighbor"
    const val NEIGHBOR_UPDATES_MAX: String = Parameters.CH.PREPARE + "updates.neighbor_max"
    const val CONTRACTED_NODES: String = Parameters.CH.PREPARE + "contracted_nodes"
    const val LOG_MESSAGES: String = Parameters.CH.PREPARE + "log_messages"
    // node contraction, node-based
    const val EDGE_DIFFERENCE_WEIGHT: String = Parameters.CH.PREPARE + "node.edge_difference_weight"
    const val ORIGINAL_EDGE_COUNT_WEIGHT: String = Parameters.CH.PREPARE + "node.original_edge_count_weight"
    const val MAX_POLL_FACTOR_HEURISTIC_NODE: String = Parameters.CH.PREPARE + "node.max_poll_factor_heuristic"
    const val MAX_POLL_FACTOR_CONTRACTION_NODE: String = Parameters.CH.PREPARE + "node.max_poll_factor_contraction"
    // node contraction, edge-based
    const val EDGE_QUOTIENT_WEIGHT: String = Parameters.CH.PREPARE + "edge.edge_quotient_weight"
    const val ORIGINAL_EDGE_QUOTIENT_WEIGHT: String = Parameters.CH.PREPARE + "edge.original_edge_quotient_weight"
    const val HIERARCHY_DEPTH_WEIGHT: String = Parameters.CH.PREPARE + "edge.hierarchy_depth_weight"
    const val MAX_POLL_FACTOR_HEURISTIC_EDGE: String = Parameters.CH.PREPARE + "edge.max_poll_factor_heuristic"
    const val MAX_POLL_FACTOR_CONTRACTION_EDGE: String = Parameters.CH.PREPARE + "edge.max_poll_factor_contraction"
}
