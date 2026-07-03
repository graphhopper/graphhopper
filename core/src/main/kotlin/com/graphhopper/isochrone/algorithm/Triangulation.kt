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

import com.carrotsearch.hppc.IntObjectHashMap
import org.locationtech.jts.triangulate.quadedge.QuadEdge
import org.locationtech.jts.triangulate.quadedge.Vertex
import org.locationtech.jts.util.Assert

class Triangulation {

    internal val edges: MutableMap<String, QuadEdge> = HashMap()

    val vertices: IntObjectHashMap<Vertex> = IntObjectHashMap()

    val vertexQuadEdges: IntObjectHashMap<QuadEdge> = IntObjectHashMap()

    fun getEdge(o: Int, d: Int): QuadEdge? {
        return if (o < d) {
            edges["$o,$d"]
        } else {
            val quadEdge = edges["$d,$o"]
            quadEdge?.sym()
        }
    }

    private fun putEdge(o: Int, d: Int, quadEdge: QuadEdge) {
        vertexQuadEdges.put(o, quadEdge)
        if (o < d) {
            edges["$o,$d"] = quadEdge
        } else {
            edges["$d,$o"] = quadEdge.sym()
        }
    }

    fun makeTriangle(v1: Int, v2: Int, v3: Int) {
        val e1 = getEdge(v1, v2)
        val e2 = getEdge(v2, v3)
        val e3 = getEdge(v3, v1)
        if (e1 == null && e2 != null) {
            makeTriangle(v2, v3, v1, e2, e3, e1)
        } else if (e2 == null && e3 != null) {
            makeTriangle(v3, v1, v2, e3, e1, e2)
        } else {
            makeTriangle(v1, v2, v3, e1, e2, e3)
        }
    }

    private fun makeTriangle(v1: Int, v2: Int, v3: Int, e1First: QuadEdge?, e2First: QuadEdge?, e3First: QuadEdge?) {
        var e1 = e1First
        var e2 = e2First
        var e3 = e3First
        if (e1 == null) {
            e1 = QuadEdge.makeEdge(getVertex(v1), getVertex(v2))
            putEdge(v1, v2, e1)
            putEdge(v2, v1, e1.sym())
        }
        if (e2 == null) {
            e2 = QuadEdge.makeEdge(getVertex(v2), getVertex(v3))
            QuadEdge.splice(e1.lNext(), e2)
            putEdge(v2, v3, e2)
            putEdge(v3, v2, e2.sym())
        }
        if (e3 == null) {
            e3 = if (e1.lNext() === e2) {
                QuadEdge.connect(e2, e1)
            } else if (e2.lNext() === e1) {
                throw RuntimeException()
            } else {
                QuadEdge.splice(e1.lNext(), e2)
                QuadEdge.connect(e2, e1)
            }
            putEdge(v3, v1, e3)
            putEdge(v1, v3, e3.sym())
        } else {
            if (e1.lNext() !== e2) {
                QuadEdge.splice(e1.lNext(), e2)
            }
            if (e2.lNext() !== e3) {
                QuadEdge.splice(e2.lNext(), e3)
            }
            if (e3.lNext() !== e1) {
                QuadEdge.splice(e3.lNext(), e1)
            }
        }
        assertTriangle(e1, e2, e3)
    }

    private fun getVertex(v3: Int): Vertex? = vertices.get(v3)

    fun getEdges(): Collection<ReadableQuadEdge> = edges.values.map { QuadEdgeAsReadableQuadEdge(it) }

    fun assertTriangle(e1: QuadEdge?, e2: QuadEdge?, e3: QuadEdge?) {
        Assert.equals(e2, e1!!.lNext())
        Assert.equals(e3, e2!!.lNext())
        Assert.equals(e1, e3!!.lNext())
    }

    fun assertTriangle(v1: Int, v2: Int, v3: Int) {
        assertTriangle(getEdge(v1, v2), getEdge(v2, v3), getEdge(v3, v1))
    }
}
