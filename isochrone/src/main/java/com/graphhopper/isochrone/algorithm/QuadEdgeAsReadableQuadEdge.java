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

import org.locationtech.jts.triangulate.quadedge.Vertex;

import java.util.Objects;

public class QuadEdgeAsReadableQuadEdge implements ReadableQuadEdge {

    private final org.locationtech.jts.triangulate.quadedge.QuadEdge delegate;

    QuadEdgeAsReadableQuadEdge(org.locationtech.jts.triangulate.quadedge.QuadEdge startEdge) {
        if (startEdge == null)
            throw new NullPointerException();
        this.delegate = startEdge;
    }

    public ReadableQuadEdge getPrimary() {
        return new QuadEdgeAsReadableQuadEdge(delegate.getPrimary());
    }

    public Vertex orig() {
        return delegate.orig();
    }

    public Vertex dest() {
        return delegate.dest();
    }

    public ReadableQuadEdge oNext() {
        return new QuadEdgeAsReadableQuadEdge(delegate.oNext());
    }

    public ReadableQuadEdge oPrev() {
        return new QuadEdgeAsReadableQuadEdge(delegate.oPrev());
    }

    public ReadableQuadEdge dPrev() {
        return new QuadEdgeAsReadableQuadEdge(delegate.dPrev());
    }

    public ReadableQuadEdge dNext() {
        return new QuadEdgeAsReadableQuadEdge(delegate.dNext());
    }

    @Override
    public ReadableQuadEdge lNext() {
        return new QuadEdgeAsReadableQuadEdge(delegate.lNext());
    }

    @Override
    public ReadableQuadEdge sym() {
        return new QuadEdgeAsReadableQuadEdge(delegate.sym());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        QuadEdgeAsReadableQuadEdge that = (QuadEdgeAsReadableQuadEdge) o;
        return Objects.equals(delegate, that.delegate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(delegate);
    }
}
