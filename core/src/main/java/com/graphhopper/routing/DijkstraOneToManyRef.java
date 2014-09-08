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
package com.graphhopper.routing;

import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;

import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.util.Weighting;
import com.graphhopper.storage.EdgeEntry;
import com.graphhopper.storage.Graph;

/**
 * A one to many implemented by extending Dijkstra. It is probably slower than DijkstraOneToMany
 * @author Bruno Carle
 *
 */
public class DijkstraOneToManyRef extends Dijkstra 
{

    TIntObjectMap<EdgeEntry> visited=new TIntObjectHashMap<EdgeEntry>();
    public DijkstraOneToManyRef(Graph g, FlagEncoder encoder, Weighting weighting, TraversalMode tMode) 
    {
        super(g, encoder, weighting, tMode);
    }
    
    @Override
    protected void checkAlreadyRun()
    {
    }
    
    @Override
    protected void visited(EdgeEntry edgeEntry) 
    {
        
        if (!visited.containsKey(edgeEntry.adjNode))
        {
            visited.put(edgeEntry.adjNode, edgeEntry);
        } else 
        {
            //ignore
        }
        
    }
    int from=-1;
    
    @Override
    public Path calcPath( int from, int to )
    {
        if (this.from==-1)
        {
            // from was not set, so this is the first run
            this.from=from;
            return super.calcPath(from, to);
        }
        
        // from is already set
        if (this.from!=from)
            throw new IllegalArgumentException(" this.from "+this.from+" from:"+from);

        this.to = to;
        EdgeEntry previouslyFound=visited.get(to);
        if (previouslyFound!=null)
            return extractPath(previouslyFound);
        else {
            if (currEdge==null)
                return createEmptyPath();
            return runAlgo();
        }
    }

    @Override
    public void clear() {
        super.clear();
        from=-1;
        visited.clear();
        
    }

}
