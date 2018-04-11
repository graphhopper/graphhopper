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

import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.GraphExtension;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.EdgeIteratorState;
import java.util.*;
import org.junit.*;
import static org.junit.Assert.assertEquals;

/**
 *
 * @author Peter Karich
 */
public class LocationIndexMatchTest {

    @Test
    public void testFindNClosest() {
        RAMDirectory dir = new RAMDirectory();
        FlagEncoder encoder = new CarFlagEncoder();
        EncodingManager em = new EncodingManager(encoder);
        GraphHopperStorage ghStorage = new GraphHopperStorage(dir, em, false, new GraphExtension.NoOpExtension());
        ghStorage.create(1000);
        // 0---1---2
        // |   |   |
        // |10 |   |
        // | | |   |
        // 3-9-4---5
        // |   |   |
        // 6---7---8
        NodeAccess na = ghStorage.getNodeAccess();
        na.setNode(0, 0.0010, 0.0000);
        na.setNode(1, 0.0010, 0.0005);
        na.setNode(2, 0.0010, 0.0010);
        na.setNode(3, 0.0005, 0.0000);
        na.setNode(4, 0.0005, 0.0005);
        na.setNode(5, 0.0005, 0.0010);
        na.setNode(6, 0.0000, 0.0000);
        na.setNode(7, 0.0000, 0.0005);
        na.setNode(8, 0.0000, 0.0010);
        na.setNode(9, 0.0005, 0.0002);
        na.setNode(10, 0.0007, 0.0002);
        ghStorage.edge(0, 1);
        ghStorage.edge(1, 2);
        ghStorage.edge(0, 3);
        EdgeIteratorState edge1_4 = ghStorage.edge(1, 4);
        ghStorage.edge(2, 5);
        ghStorage.edge(3, 9);
        EdgeIteratorState edge9_4 = ghStorage.edge(9, 4);
        EdgeIteratorState edge4_5 = ghStorage.edge(4, 5);
        ghStorage.edge(10, 9);
        ghStorage.edge(3, 6);
        EdgeIteratorState edge4_7 = ghStorage.edge(4, 7);
        ghStorage.edge(5, 8);
        ghStorage.edge(6, 7);
        ghStorage.edge(7, 8);

        LocationIndexTree tmpIndex = new LocationIndexTree(ghStorage, new RAMDirectory());
        tmpIndex.prepareIndex();
        LocationIndexMatch index = new LocationIndexMatch(ghStorage, tmpIndex);

        // query node 4 => get at least 4-5, 4-7
        List<QueryResult> result = index.findNClosest(0.0004, 0.0006, EdgeFilter.ALL_EDGES, 15);
        List<Integer> ids = new ArrayList<Integer>();
        for (QueryResult qr : result) {
            ids.add(qr.getClosestEdge().getEdge());
        }
        Collections.sort(ids);
        assertEquals("edge ids do not match",
                Arrays.asList(/*edge1_4.getEdge(), edge9_4.getEdge(), */edge4_5.getEdge(), edge4_7.getEdge()),
                ids);
    }

}
