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
package com.graphhopper.matching;

import com.graphhopper.routing.Path;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;

import java.util.List;

/**
 *
 * @author Peter Karich
 */
public class MatchResult {

    private List<EdgeMatch> edgeMatches;
    private Path mergedPath;
    private Weighting weighting;
    private Graph graph;
    private double matchLength;
    private long matchMillis;
    private double gpxEntriesLength;
    private long gpxEntriesMillis;

    public MatchResult(List<EdgeMatch> edgeMatches) {
        setEdgeMatches(edgeMatches);
    }

    public void setEdgeMatches(List<EdgeMatch> edgeMatches) {
        if (edgeMatches == null) {
            throw new IllegalStateException("edgeMatches cannot be null");
        }

        this.edgeMatches = edgeMatches;
    }

    public void setGPXEntriesLength(double gpxEntriesLength) {
        this.gpxEntriesLength = gpxEntriesLength;
    }

    public void setGPXEntriesMillis(long gpxEntriesMillis) {
        this.gpxEntriesMillis = gpxEntriesMillis;
    }

    public void setMatchLength(double matchLength) {
        this.matchLength = matchLength;
    }

    public void setMatchMillis(long matchMillis) {
        this.matchMillis = matchMillis;
    }

    public void setMergedPath(Path mergedPath) {
        this.mergedPath = mergedPath;
    }


    /**
     * All possible assigned edges.
     */
    public List<EdgeMatch> getEdgeMatches() {
        return edgeMatches;
    }

    /**
     * Length of the original GPX track in meters
     */
    public double getGpxEntriesLength() {
        return gpxEntriesLength;
    }

    /**
     * Length of the map-matched route in meters
     */
    public double getMatchLength() {
        return matchLength;
    }

    /**
     * Duration of the map-matched route in milliseconds
     */
    public long getMatchMillis() {
        return matchMillis;
    }

    public Path getMergedPath() {
        return mergedPath;
    }

    @Override
    public String toString() {
        return "length:" + matchLength + ", seconds:" + matchMillis / 1000f + ", matches:" + edgeMatches.toString();
    }

    public Weighting getWeighting() {
        return weighting;
    }

    public void setWeighting(Weighting weighting) {
        this.weighting = weighting;
    }

    public Graph getGraph() {
        return graph;
    }

    public void setGraph(Graph graph) {
        this.graph = graph;
    }
}
