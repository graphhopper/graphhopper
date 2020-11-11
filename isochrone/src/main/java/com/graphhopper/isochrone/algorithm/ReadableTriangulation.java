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

package com.graphhopper.isochrone.algorithm;

import org.locationtech.jts.triangulate.quadedge.QuadEdgeSubdivision;

import java.util.Collection;

public interface ReadableTriangulation {

    Collection<ReadableQuadEdge> getEdges();

    static ReadableTriangulation wrap(Triangulation triangulation) {
        return new TriangulationAsReadableTriangulation(triangulation);
    }

    static ReadableTriangulation wrap(QuadEdgeSubdivision quadEdgeSubdivision) {
        return new QuadEdgeSubdivisionAsReadableTriangulation(quadEdgeSubdivision);
    }

    ReadableQuadEdge getEdge(int v1, int v2);

    public ReadableQuadEdge getVertexQuadEdge(int v);
}
