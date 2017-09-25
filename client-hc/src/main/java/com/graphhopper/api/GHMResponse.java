package com.graphhopper.api;

import com.graphhopper.GHResponse;

/**
 * @author Peter Karich
 */
public class GHMResponse extends GHResponse {

    private final int fromIndex;
    private final int toIndex;
    private final boolean identicalStartAndEnd;

    public GHMResponse(int fromIndex, int toIndex, boolean identicalStartAndEnd) {
        this.fromIndex = fromIndex;
        this.toIndex = toIndex;
        this.identicalStartAndEnd = identicalStartAndEnd;
    }

    public boolean isIdenticalStartAndEnd() {
        return identicalStartAndEnd;
    }

    public int getFromIndex() {
        return fromIndex;
    }

    public int getToIndex() {
        return toIndex;
    }
}
