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

import com.graphhopper.routing.util.AllEdgesSkipIterator;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.util.EdgeBase;
import com.graphhopper.util.EdgeSkipExplorer;

/**
 * Extended graph interface which supports storing and retrieving the level for a node.
 * <p/>
 * @author Peter Karich
 */
public interface LevelGraph extends Graph
{
    void setLevel( int index, int level );

    int getLevel( int index );

    @Override
    EdgeSkipExplorer edge( int a, int b, double distance, int flags );

    @Override
    EdgeSkipExplorer edge( int a, int b, double distance, boolean bothDirections );

    @Override
    EdgeBase getEdgeProps( int edgeId, int endNode );

    @Override
    EdgeSkipExplorer createEdgeExplorer();

    @Override
    EdgeSkipExplorer createEdgeExplorer( EdgeFilter filter );

    @Override
    AllEdgesSkipIterator getAllEdges();
}
