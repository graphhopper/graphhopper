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

import com.graphhopper.coll.GHBitSet;
import com.graphhopper.coll.GHTBitSet;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIteratorState;
import gnu.trove.procedure.TIntProcedure;
import gnu.trove.set.hash.TIntHashSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 *
 * @author Peter Karich
 */
public class LocationIndexMatch extends LocationIndexTree
{
    private static final Comparator<QueryResult> QR_COMPARATOR = new Comparator<QueryResult>()
    {
        @Override
        public int compare( QueryResult o1, QueryResult o2 )
        {
            return Double.compare(o1.getQueryDistance(), o2.getQueryDistance());
        }
    };

    public LocationIndexMatch( Graph g, Directory dir )
    {
        super(g, dir);

        // apply settings good for most map matching cases, let them be customizable afterwards too
        setMaxRegionSearch(2);
        setMinResolutionInMeter(20);
    }

    public List<QueryResult> findNClosest( final int maxResults, final double queryLat, final double queryLon, final EdgeFilter edgeFilter )
    {
        // implement a cheap priority queue via List, sublist and Collections.sort
        final List<QueryResult> queryResults = new ArrayList<QueryResult>(maxResults);
        TIntHashSet set = super.findNetworkEntries(queryLat, queryLon, 2);

        // try bigger area
        // if (set.size() < maxResults)
        //   set.addAll(super.findNetworkEntries(queryLat, queryLon, 2));
        //
        final GHBitSet checkBitset = new GHTBitSet();
        final EdgeExplorer explorer = graph.createEdgeExplorer(getEdgeFilter());

        set.forEach(new TIntProcedure()
        {
            double maxQueryDistance = Double.MAX_VALUE;

            @Override
            public boolean execute( int node )
            {
                new XFirstSearchCheck(queryLat, queryLon, checkBitset, edgeFilter)
                {
                    @Override
                    protected double getQueryDistance()
                    {
                        return maxQueryDistance;
                    }

                    @Override
                    protected boolean check( int node, double normedDist, int wayIndex, EdgeIteratorState edge, QueryResult.Position pos )
                    {
                        // skip TOWER matches as it does not help us to identify which of the connected edges should be preferred
                        if (normedDist >= maxQueryDistance || pos == QueryResult.Position.TOWER)
                            return false;

                        if (queryResults.size() >= maxResults * 5)
                        {
                            Collections.sort(queryResults, QR_COMPARATOR);
                            queryResults.subList(maxResults, queryResults.size()).clear();
                            maxQueryDistance = queryResults.get(queryResults.size() - 1).getQueryDistance();
                        }

                        QueryResult qr = new QueryResult(queryLat, queryLon);
                        qr.setQueryDistance(normedDist);
                        qr.setClosestNode(node);
                        qr.setClosestEdge(edge.detach(false));
                        qr.setWayIndex(wayIndex);
                        qr.setSnappedPosition(pos);
                        queryResults.add(qr);
                        return true;
                    }
                }.start(explorer, node);
                return true;
            }
        });

        if (queryResults.size() > maxResults)
        {
            Collections.sort(queryResults, QR_COMPARATOR);
            queryResults.subList(maxResults, queryResults.size()).clear();
        }

        for (QueryResult qr : queryResults)
        {
            if (qr.isValid())
            {
                // denormalize distance            
                qr.setQueryDistance(distCalc.calcDenormalizedDist(qr.getQueryDistance()));
                qr.calcSnappedPoint(distCalc);
            } else
                throw new IllegalStateException("invalid query result should not happen here: " + qr);
        }

        return queryResults;
    }

}
