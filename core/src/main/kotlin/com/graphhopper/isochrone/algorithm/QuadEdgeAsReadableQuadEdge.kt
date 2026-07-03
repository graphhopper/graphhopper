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
package com.graphhopper.isochrone.algorithm

import org.locationtech.jts.triangulate.quadedge.QuadEdge
import org.locationtech.jts.triangulate.quadedge.Vertex
import java.util.Objects

class QuadEdgeAsReadableQuadEdge internal constructor(startEdge: QuadEdge?) : ReadableQuadEdge {

    private val delegate: QuadEdge = startEdge ?: throw NullPointerException()

    override fun getPrimary(): ReadableQuadEdge = QuadEdgeAsReadableQuadEdge(delegate.primary)

    override fun orig(): Vertex = delegate.orig()

    override fun dest(): Vertex = delegate.dest()

    override fun oNext(): ReadableQuadEdge = QuadEdgeAsReadableQuadEdge(delegate.oNext())

    override fun oPrev(): ReadableQuadEdge = QuadEdgeAsReadableQuadEdge(delegate.oPrev())

    override fun dPrev(): ReadableQuadEdge = QuadEdgeAsReadableQuadEdge(delegate.dPrev())

    override fun dNext(): ReadableQuadEdge = QuadEdgeAsReadableQuadEdge(delegate.dNext())

    override fun lNext(): ReadableQuadEdge = QuadEdgeAsReadableQuadEdge(delegate.lNext())

    override fun sym(): ReadableQuadEdge = QuadEdgeAsReadableQuadEdge(delegate.sym())

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        other as QuadEdgeAsReadableQuadEdge
        return delegate == other.delegate
    }

    override fun hashCode(): Int = Objects.hash(delegate)
}
