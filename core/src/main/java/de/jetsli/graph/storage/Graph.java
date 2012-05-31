/*
 *  Copyright 2012 Peter Karich info@jetsli.de
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
package de.jetsli.graph.storage;

import de.jetsli.graph.util.MyIteratorable;

/**
 *
 * @author Peter Karich, info@jetsli.de
 */
public interface Graph {

    void ensureCapacity(int cap);
    
    int getLocations();
    
    int addLocation(float lat, float lon);

    float getLatitude(int index);

    float getLongitude(int index);

    void edge(int a, int b, float distance, boolean bothDirections);

    MyIteratorable<DistEntry> getEdges(int index);

    MyIteratorable<DistEntry> getIncoming(int index);

    MyIteratorable<DistEntry> getOutgoing(int index);
    
    Graph clone();
    
    /**
     * @return the id of the closest node to the specified parameters.
     */
    int getNodeId(float lat, float lon, int minEdges);
}
