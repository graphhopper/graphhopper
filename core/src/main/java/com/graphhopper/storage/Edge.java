/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
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
package com.graphhopper.storage;

/**
 * 'Edges' do not exist as separate objects in GraphHopper for the storage as this would be too
 * memory intensive. Look into EdgeIterator and Graph.getEdges(index) instead. But it is used as
 * base class in all algorithms except the native BidirectionalDijkstra.
 * <p/>
 * @see EdgeEntry
 * @author Peter Karich
 */
public class Edge implements Comparable<Edge>
{
    public int edge;
    public int adjNode;
    public double weight;

    public Edge( int edgeId, int endNode, double distance )
    {
        this.edge = edgeId;
        this.adjNode = endNode;
        this.weight = distance;
    }

    @Override
    public int compareTo( Edge o )
    {
        return Double.compare(weight, o.weight);
    }

    @Override
    public String toString()
    {
        return adjNode + " (" + edge + ") weight: " + weight;
    }
}
