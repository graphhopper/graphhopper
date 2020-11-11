/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.routing;

import java.util.Objects;

import static com.graphhopper.util.EdgeIterator.ANY_EDGE;
import static com.graphhopper.util.EdgeIterator.NO_EDGE;
import static com.graphhopper.util.Parameters.Curbsides.*;
import static com.graphhopper.util.Parameters.Routing.CURBSIDE;

public class DirectionResolverResult {
    private static final DirectionResolverResult UNRESTRICTED = new DirectionResolverResult(ANY_EDGE, ANY_EDGE, ANY_EDGE, ANY_EDGE);
    private static final DirectionResolverResult IMPOSSIBLE = new DirectionResolverResult(NO_EDGE, NO_EDGE, NO_EDGE, NO_EDGE);

    private final int inEdgeRight;
    private final int outEdgeRight;
    private final int inEdgeLeft;
    private final int outEdgeLeft;

    public static DirectionResolverResult onlyLeft(int inEdge, int outEdge) {
        return new DirectionResolverResult(NO_EDGE, NO_EDGE, inEdge, outEdge);
    }

    public static DirectionResolverResult onlyRight(int inEdge, int outEdge) {
        return new DirectionResolverResult(inEdge, outEdge, NO_EDGE, NO_EDGE);
    }

    public static DirectionResolverResult restricted(int inEdgeRight, int outEdgeRight, int inEdgeLeft, int outEdgeLeft) {
        return new DirectionResolverResult(inEdgeRight, outEdgeRight, inEdgeLeft, outEdgeLeft);
    }

    public static DirectionResolverResult unrestricted() {
        return UNRESTRICTED;
    }

    public static DirectionResolverResult impossible() {
        return IMPOSSIBLE;
    }

    private DirectionResolverResult(int inEdgeRight, int outEdgeRight, int inEdgeLeft, int outEdgeLeft) {
        this.inEdgeRight = inEdgeRight;
        this.outEdgeRight = outEdgeRight;
        this.inEdgeLeft = inEdgeLeft;
        this.outEdgeLeft = outEdgeLeft;
    }

    public static int getOutEdge(DirectionResolverResult directionResolverResult, String curbside) {
        if (curbside.trim().isEmpty()) {
            curbside = CURBSIDE_ANY;
        }
        switch (curbside) {
            case CURBSIDE_RIGHT:
                return directionResolverResult.getOutEdgeRight();
            case CURBSIDE_LEFT:
                return directionResolverResult.getOutEdgeLeft();
            case CURBSIDE_ANY:
                return ANY_EDGE;
            default:
                throw new IllegalArgumentException("Unknown value for " + CURBSIDE + " : '" + curbside + "'. allowed: " + CURBSIDE_LEFT + ", " + CURBSIDE_RIGHT + ", " + CURBSIDE_ANY);
        }
    }

    public static int getInEdge(DirectionResolverResult directionResolverResult, String curbside) {
        if (curbside.trim().isEmpty()) {
            curbside = CURBSIDE_ANY;
        }
        switch (curbside) {
            case CURBSIDE_RIGHT:
                return directionResolverResult.getInEdgeRight();
            case CURBSIDE_LEFT:
                return directionResolverResult.getInEdgeLeft();
            case CURBSIDE_ANY:
                return ANY_EDGE;
            default:
                throw new IllegalArgumentException("Unknown value for '" + CURBSIDE + " : " + curbside + "'. allowed: " + CURBSIDE_LEFT + ", " + CURBSIDE_RIGHT + ", " + CURBSIDE_ANY);
        }
    }

    public int getInEdgeRight() {
        return inEdgeRight;
    }

    public int getOutEdgeRight() {
        return outEdgeRight;
    }

    public int getInEdgeLeft() {
        return inEdgeLeft;
    }

    public int getOutEdgeLeft() {
        return outEdgeLeft;
    }

    public boolean isRestricted() {
        return !equals(UNRESTRICTED);
    }

    public boolean isImpossible() {
        return equals(IMPOSSIBLE);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DirectionResolverResult that = (DirectionResolverResult) o;
        return inEdgeRight == that.inEdgeRight &&
                outEdgeRight == that.outEdgeRight &&
                inEdgeLeft == that.inEdgeLeft &&
                outEdgeLeft == that.outEdgeLeft;
    }

    @Override
    public int hashCode() {
        return Objects.hash(inEdgeRight, outEdgeRight, inEdgeLeft, outEdgeLeft);
    }

    @Override
    public String toString() {
        if (!isRestricted()) {
            return "unrestricted";
        } else if (isImpossible()) {
            return "impossible";
        } else {
            return "in-edge-right: " + pretty(inEdgeRight) + ", out-edge-right: " + pretty(outEdgeRight) + ", in-edge-left: " + pretty(inEdgeLeft) + ", out-edge-left: " + pretty(outEdgeLeft);
        }
    }

    private String pretty(int edgeId) {
        if (edgeId == NO_EDGE) {
            return "NO_EDGE";
        } else if (edgeId == ANY_EDGE) {
            return "ANY_EDGE";
        } else {
            return edgeId + "";
        }
    }
}
