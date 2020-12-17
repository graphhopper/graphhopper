package com.graphhopper.jackson;

import com.fasterxml.jackson.annotation.JsonProperty;

public interface ClauseMixin {
    @JsonProperty("else if")
    void setElseIf(String elseifClause);
}
