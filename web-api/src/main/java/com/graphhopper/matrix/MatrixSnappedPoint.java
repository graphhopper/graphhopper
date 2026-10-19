package com.graphhopper.matrix;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Explicit nulls are part of the snap contract, even when server defaults omit null properties. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record MatrixSnappedPoint(List<Double> coordinates,
                                 @JsonProperty("snap_distance_m") Double snapDistanceM) {
}
