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
package com.graphhopper.routing.matrix;

import com.graphhopper.util.EdgeIterator;


public class MatrixEntry implements Comparable<MatrixEntry> {

    public int edge;
    public int adjNode;
    public double weight;
    public double distance;
    public long time;
    public int incEdge;

    public MatrixEntry(int node, double weight, long time, double distance) {
        this(EdgeIterator.NO_EDGE,EdgeIterator.NO_EDGE, node, weight,time,distance);
    }

    public MatrixEntry(int edge, int incEdge,int adjNode, double weight, long time, double distance) {
        this.edge = edge;
        this.adjNode = adjNode;
        this.weight = weight;
        this.time = time;
        this.distance = distance;
        this.incEdge = incEdge;
    }

    public double getWeightOfVisitedPath() {
        return weight;
    }

    @Override
    public String toString() {
        return adjNode + " (" + edge + ") weight: " + weight + ", incEdge: " + incEdge + " time: " + time + " distance :" + distance;
    }

    @Override
    public int compareTo(MatrixEntry o) {

        if (weight < o.weight)
            return -1;

        // assumption no NaN and no -0
        return weight > o.weight ? 1 : 0;
    }
}
