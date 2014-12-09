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
package com.graphhopper.matching;

import java.util.List;

/**
 *
 * @author Peter Karich
 */
public class MatchResult
{
    private final List<EdgeMatch> edgeMatches;
    private final double matchLength;
    private final long matchMillis;
    private final double gpxEntriesLength;
    private final long gpxEntriesMillis;

    public MatchResult( List<EdgeMatch> edgeMatches, 
            double matchLength, long matchMillis, 
            double gpxEntriesLength, long gpxEntriesMillis )
    {
        this.edgeMatches = edgeMatches;
        this.matchLength = matchLength;
        this.matchMillis = matchMillis;
        this.gpxEntriesLength = gpxEntriesLength;
        this.gpxEntriesMillis = gpxEntriesMillis;
    }

    public List<EdgeMatch> getEdgeMatches()
    {
        return edgeMatches;
    }

    public double getGpxEntriesLength()
    {
        return gpxEntriesLength;
    }

    public long getGpxEntriesMillis()
    {
        return gpxEntriesMillis;
    }

    public double getMatchLength()
    {
        return matchLength;
    }

    public long getMatchMillis()
    {
        return matchMillis;
    }
}
