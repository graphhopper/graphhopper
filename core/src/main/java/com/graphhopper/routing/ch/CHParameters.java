package com.graphhopper.routing.ch;

import com.graphhopper.util.Parameters;

public final class CHParameters {
    static final String PERIODIC_UPDATES = Parameters.CH.PREPARE + "updates.periodic";
    static final String LAST_LAZY_NODES_UPDATES = Parameters.CH.PREPARE + "updates.lazy";
    static final String NEIGHBOR_UPDATES = Parameters.CH.PREPARE + "updates.neighbor";
    static final String CONTRACTED_NODES = Parameters.CH.PREPARE + "contracted_nodes";
    static final String LOG_MESSAGES = Parameters.CH.PREPARE + "log_messages";
    static final String EDGE_DIFFERENCE_WEIGHT = Parameters.CH.PREPARE + "node.edge_difference_weight";
    static final String ORIGINAL_EDGE_COUNT_WEIGHT = Parameters.CH.PREPARE + "node.original_edge_count_weight";
    static final String CONTRACTED_NEIGHBORS_WEIGHT = Parameters.CH.PREPARE + "node.contracted_neighbors_weight";
    static final String EDGE_QUOTIENT_WEIGHT = Parameters.CH.PREPARE + "edge.edge_quotient_weight";
    static final String ORIGINAL_EDGE_QUOTIENT_WEIGHT = Parameters.CH.PREPARE + "edge.original_edge_quotient_weight";
    static final String HIERARCHY_DEPTH_WEIGHT = Parameters.CH.PREPARE + "edge.hierarchy_depth_weight";

    private CHParameters() {
    }
}
