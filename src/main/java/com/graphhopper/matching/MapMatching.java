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

import com.graphhopper.routing.DijkstraBidirectionRef;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.QueryGraph;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.FastestWeighting;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.TurnCostExtension;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.*;
import gnu.trove.map.hash.TIntDoubleHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.procedure.TIntObjectProcedure;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class matches real world GPX entries to the digital road network stored in GraphHopper. The
 * algorithm is a simple 4 phase process:
 * <p>
 * <ol>
 * <li>Lookup Phase: Find N closest edges for every GPX entry</li>
 * <li>Custom Weighting Phase: Create a weighting object where those edges will be preferred</li>
 * <li>Search Phase: Calculate the path and its list of edges from the best start to the best end
 * edge</li>
 * <li>Match Phase: Associate all GPX entries for every edge</li>
 * </ol>
 * <p>
 *
 * Note: currently tested with very close GPX points only. Will fail if best match for start or end
 * node is incorrect. Performance improvements possible if not the full but only partial routes are
 * calculated this will also improve accuracy as currently all loops in a GPX trail are
 * automatically removed.
 * <p>
 * @see http://en.wikipedia.org/wiki/Map_matching
 * @author Peter Karich
 */
public class MapMatching
{
    private final Graph graph;
    private final LocationIndexMatch locationIndex;
    private final FlagEncoder encoder;
    private int maxEdgesPerPoint = 4;
    private TraversalMode traversalMode;
    private DistanceCalc distanceCalc = new DistancePlaneProjection();

    public MapMatching( Graph graph, LocationIndexMatch locationIndex, FlagEncoder encoder )
    {
        this.graph = graph;
        this.locationIndex = locationIndex;
        this.traversalMode = graph.getExtension() instanceof TurnCostExtension ? TraversalMode.EDGE_BASED_2DIR : TraversalMode.NODE_BASED;
        this.encoder = encoder;
    }

    public void setTraversalMode( TraversalMode traversalMode )
    {
        this.traversalMode = traversalMode;
    }

    /**
     * @param maxEdgesPerPoint the maximum number of closest edges which should be searched by for
     * one point
     */
    public void setMaxEdgesPerPoint( int maxEdgesPerPoint )
    {
        if (maxEdgesPerPoint < 1)
            throw new IllegalArgumentException("Specify at least 1");

        this.maxEdgesPerPoint = maxEdgesPerPoint;
    }

    public void setDistanceCalc( DistanceCalc distanceCalc )
    {
        this.distanceCalc = distanceCalc;
    }

    /**
     * This method does the actual map matchting.
     * <p>
     * @param gpxList the input list with GPX points which should match to edges of the graph
     */
    public MatchResult doWork( List<GPXEntry> gpxList )
    {
        QueryResult startQueryResult = null, endQueryResult = null;
        final TIntObjectHashMap<List<GPXExtension>> extensionMap
                = new TIntObjectHashMap<List<GPXExtension>>(gpxList.size() * maxEdgesPerPoint, 0.5f, -1);
        final TIntDoubleHashMap minWeightMap = new TIntDoubleHashMap(gpxList.size() * maxEdgesPerPoint, 0.5f, -1, -1);
        EdgeFilter edgeFilter = new DefaultEdgeFilter(encoder);

        //////// Lookup Phase (1) ////////
        for (int gpxIndex = 0; gpxIndex < gpxList.size(); gpxIndex++)
        {
            GPXEntry entry = gpxList.get(gpxIndex);
            List<QueryResult> qResults = locationIndex.findNClosest(maxEdgesPerPoint, entry.lat, entry.lon, edgeFilter);

            if (qResults.isEmpty())
            {
                // throw new IllegalStateException("no match found for " + entry);
                continue;
            }

            QueryResult bestQR = qResults.get(0);
            if (startQueryResult == null)
                startQueryResult = bestQR;

            endQueryResult = bestQR;
            for (int matchIndex = 0; matchIndex < qResults.size(); matchIndex++)
            {
                QueryResult qr = qResults.get(matchIndex);
                int edge = qr.getClosestEdge().getEdge();
                List<GPXExtension> extensionList = extensionMap.get(edge);
                if (extensionList == null)
                {
                    extensionList = new ArrayList(5);
                    extensionMap.put(edge, extensionList);
                }

                extensionList.add(new GPXExtension(entry, qr.getQueryDistance(), gpxIndex, matchIndex));
            }
        }

        //////// Custom Weighting Phase (2) ////////
        final AtomicInteger maxWeight = new AtomicInteger(0);
        extensionMap.forEachEntry(new TIntObjectProcedure<List<GPXExtension>>()
        {
            @Override
            public boolean execute( int edge, List<GPXExtension> list )
            {
                int minimumMatchIndex = maxEdgesPerPoint;
                double minimumDist = Double.MAX_VALUE;
                int goodMatches = 1;
                for (GPXExtension ext : list)
                {
                    if (ext.match == 0)
                        goodMatches++;

                    if (ext.match < minimumMatchIndex)
                        minimumMatchIndex = ext.match;

                    if (ext.queryDistance < minimumDist)
                        minimumDist = ext.queryDistance;
                }

                // Prefer close match, prefer direct match (small minimumMatchIndex) and many GPX points.
                // And '+0.5' to avoid extreme decrease in case of a match close to a tower node
                double weight = minimumDist * (minimumMatchIndex * .1 + 1) / goodMatches + .5;
                if (weight > maxWeight.get())
                    maxWeight.set((int) weight);
                minWeightMap.put(edge, weight);
                return true;
            }
        });
        // System.out.println("MAX weight " + maxWeight);

        if (startQueryResult == null || endQueryResult == null)
        {
            throw new IllegalArgumentException("Input GPX list does not contain valid points!? " + gpxList.size() + ", " + gpxList);
        }

        FastestWeighting customWeighting = new FastestWeighting(encoder)
        {
            @Override
            public double calcWeight( EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId )
            {
                double matchWeight = minWeightMap.get(edge.getEdge());
                double weight = super.calcWeight(edge, reverse, prevOrNextEdgeId);
                if (matchWeight < 0)
                {
                    return maxWeight.get() * weight;
                }

                return matchWeight * weight;
            }
        };

        //////// Search Phase (3) ////////        
        QueryGraph queryGraph = new QueryGraph(graph);
        queryGraph.lookup(startQueryResult, endQueryResult);
        EdgeExplorer explorer = queryGraph.createEdgeExplorer(edgeFilter);

        // make new virtual edges from QueryGraph also available in minDistanceMap and prefer them
        fillVirtualEdges(minWeightMap, explorer.setBaseNode(startQueryResult.getClosestNode()), startQueryResult);
        fillVirtualEdges(minWeightMap, explorer.setBaseNode(endQueryResult.getClosestNode()), endQueryResult);

        // TODO limit search to maximum 5 times of the GPX length to avoid heavy memory consumption for large graphs        
        DijkstraBidirectionRef algo = new DijkstraBidirectionRef(queryGraph, encoder, customWeighting, traversalMode);
        Path path = algo.calcPath(startQueryResult.getClosestNode(), endQueryResult.getClosestNode());
        // GPXFile.write(path, "inner-test.gpx", new TranslationMap().doImport().get("de"));
        List<EdgeIteratorState> pathEdgeList = path.calcEdges();

        //////// Match Phase (4) ////////
        List<EdgeMatch> edgeMatches = new ArrayList<EdgeMatch>();
        // TODO stats: calculate missing GPX matches
        // TODO stats: calculate mean query distance
        int minGPXIndex = -1;
        for (EdgeIteratorState edge : pathEdgeList)
        {
            List<GPXExtension> gpxExtensionList = extensionMap.get(edge.getEdge());
            if (gpxExtensionList == null)
            {
                edgeMatches.add(new EdgeMatch(edge, Collections.<GPXExtension>emptyList()));
                continue;
            }

            List<GPXExtension> clonedList = new ArrayList<GPXExtension>(gpxExtensionList.size());
            // skip GPXExtensions with too small index otherwise EdgeMatch could go into past
            int newMinGPXIndex = minGPXIndex;
            for (GPXExtension ext : gpxExtensionList)
            {
                if (ext.gpxListIndex > minGPXIndex)
                {
                    clonedList.add(ext);
                    if (newMinGPXIndex < ext.gpxListIndex)
                        newMinGPXIndex = ext.gpxListIndex;
                }
            }
            minGPXIndex = newMinGPXIndex;
            EdgeMatch edgeMatch = new EdgeMatch(edge, clonedList);
            edgeMatches.add(edgeMatch);
        }

        //////// Calculate stats //////// 
        double gpxLength = 0;
        GPXEntry prevEntry = gpxList.get(0);
        for (int i = 1; i < gpxList.size(); i++)
        {
            GPXEntry entry = gpxList.get(i);
            gpxLength += distanceCalc.calcDist(prevEntry.lat, prevEntry.lon, entry.lat, entry.lon);
            prevEntry = entry;
        }

        long gpxMillis = gpxList.get(gpxList.size() - 1).getMillis() - gpxList.get(0).getMillis();
        MatchResult matchResult = new MatchResult(edgeMatches,
                path.getDistance(), path.getMillis(),
                gpxLength, gpxMillis);
        return matchResult;
    }

    private void fillVirtualEdges( TIntDoubleHashMap minDistanceMap, EdgeIterator iter, QueryResult qr )
    {
        while (iter.next())
        {
            minDistanceMap.put(iter.getEdge(), qr.getQueryDistance());
        }
    }
}
