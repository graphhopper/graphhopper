package com.graphhopper.routing.weighting;

import com.graphhopper.routing.querygraph.QueryGraph;

/**
 * Marker interface for a Weighting that requires the QueryGraph
 */
public interface QueryGraphRequired {

    QueryGraphRequired setQueryGraph(QueryGraph queryGraph);
}
