package com.graphhopper.jackson;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.graphhopper.GHRequest;

import java.util.List;

/**
 * With this approach we avoid the jackson annotations dependency in core
 */
interface GHRequestMixIn {

    // a good trick to serialize unknown properties into the HintsMap
    @JsonAnySetter
    void putHint(String fieldName, Object value);

    @JsonProperty("point_hints")
    GHRequest setPointHints(List<String> pointHints);

    @JsonProperty("snap_preventions")
    GHRequest setSnapPreventions(List<String> snapPreventions);

    @JsonProperty("details")
    GHRequest setPathDetails(List<String> pathDetails);

    @JsonProperty("curbsides")
    GHRequest setCurbsides(List<String> curbsides);
}
