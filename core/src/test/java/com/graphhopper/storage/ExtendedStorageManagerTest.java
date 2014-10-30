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

import com.graphhopper.routing.util.EncodingManager;
import java.util.TreeMap;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class ExtendedStorageManagerTest
{
    private ExtendedStorageManager manager;

    protected GraphStorage newGraph( Directory dir )
    {
        TreeMap extStorages = new TreeMap();
        extStorages.put(TurnCostStorage.IDENTIFIER, new TurnCostStorage());
        manager = new ExtendedStorageManager(extStorages);
        GraphStorage graph = new GraphHopperStorage(dir, new EncodingManager("CAR"), false, manager);
        graph.create(0);
        graph.getNodeAccess().setNode(0, 0, 0);
        graph.getNodeAccess().setNode(1, 1, 4);
        graph.getNodeAccess().setNode(2, 2, 3);
        graph.getNodeAccess().setNode(3, 3, 2);
        graph.getNodeAccess().setNode(4, 4, 1);
        graph.edge(0, 1, 1, true);
        graph.edge(1, 2, 1, true);
        graph.edge(0, 2, 1, true);
        graph.edge(2, 3, 1, true);
        graph.edge(3, 4, 1, true);

        return graph;
    }

    @Test
    public void testSingleExtendedStorage()
    {
        String defaultGraphLoc = "./target/graphstorage/default";
        Directory dir = new RAMDirectory(defaultGraphLoc, false);
        GraphStorage graph = newGraph(dir);

        ExtendedStorageAccess access = graph.getExtendedStorageAccess();
        access.writeToExtendedNodeStorage(TurnCostStorage.IDENTIFIER, 0, 3);
        access.writeToExtendedNodeStorage(TurnCostStorage.IDENTIFIER, 1, 2);
        access.writeToExtendedNodeStorage(TurnCostStorage.IDENTIFIER, 2, 1);
        access.writeToExtendedNodeStorage(TurnCostStorage.IDENTIFIER, 3, 0);

        assertEquals(3, access.readFromExtendedNodeStorage(TurnCostStorage.IDENTIFIER, 0));
        assertEquals(2, access.readFromExtendedNodeStorage(TurnCostStorage.IDENTIFIER, 1));
        assertEquals(1, access.readFromExtendedNodeStorage(TurnCostStorage.IDENTIFIER, 2));
        assertEquals(0, access.readFromExtendedNodeStorage(TurnCostStorage.IDENTIFIER, 3));
    }

    @Test
    public void testReloadFromDist()
    {
        String defaultGraphLoc = "./target/graphstorage/default";
        Directory dir = new RAMDirectory(defaultGraphLoc, true);
        GraphStorage graph = newGraph(dir);

        ExtendedStorageAccess access = graph.getExtendedStorageAccess();
        access.writeToExtendedNodeStorage(TurnCostStorage.IDENTIFIER, 0, 3);
        access.writeToExtendedNodeStorage(TurnCostStorage.IDENTIFIER, 1, 2);
        access.writeToExtendedNodeStorage(TurnCostStorage.IDENTIFIER, 2, 1);
        access.writeToExtendedNodeStorage(TurnCostStorage.IDENTIFIER, 3, 0);

        graph.flush();
        graph.close();

        dir = new RAMDirectory(defaultGraphLoc, true);
        graph = new GraphHopperStorage(dir, new EncodingManager("CAR"), false, manager);
        graph.loadExisting();
        access = graph.getExtendedStorageAccess();
        assertEquals(3, access.readFromExtendedNodeStorage(TurnCostStorage.IDENTIFIER, 0));
        assertEquals(2, access.readFromExtendedNodeStorage(TurnCostStorage.IDENTIFIER, 1));
        assertEquals(1, access.readFromExtendedNodeStorage(TurnCostStorage.IDENTIFIER, 2));
        assertEquals(0, access.readFromExtendedNodeStorage(TurnCostStorage.IDENTIFIER, 3));

    }
}
