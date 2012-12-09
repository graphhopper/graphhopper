/*
 *  Copyright 2012 Peter Karich 
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
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

import com.graphhopper.util.EdgeSkipIterator;

/**
 * Extended graph interface which supports storing and retrieving the level for a node.
 *
 * @author Peter Karich,
 */
public interface LevelGraph extends Graph {

    void setLevel(int index, int level);

    int getLevel(int index);

    EdgeSkipIterator shortcut(int a, int b, double distance, int flags, int skippedEdge);

    @Override
    EdgeSkipIterator getEdgeProps(int edgeId, int endNode);

    @Override
    EdgeSkipIterator getEdges(int nodeId);

    @Override
    EdgeSkipIterator getIncoming(int nodeId);

    @Override
    EdgeSkipIterator getOutgoing(int nodeId);

    @Override
    EdgeSkipIterator getAllEdges();
}
