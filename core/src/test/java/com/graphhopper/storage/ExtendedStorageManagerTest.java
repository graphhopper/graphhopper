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

class DummyExtendedStorage implements ExtendedStorage
{
    private String filename;
    private boolean requiresNode;
    private boolean requiresEdge;

    public DummyExtendedStorage( String filename, boolean requiresNode, boolean requiresEdge )
    {
        this.filename = filename;
        this.requiresNode = requiresNode;
        this.requiresEdge = requiresEdge;
    }

    @Override
    public boolean isRequireNodeField()
    {
        return requiresNode;
    }

    @Override
    public boolean isRequireEdgeField()
    {
        return requiresEdge;
    }

    @Override
    public int getDefaultNodeFieldValue()
    {
        return -1;
    }

    @Override
    public int getDefaultEdgeFieldValue()
    {
        return -1;
    }

    @Override
    public void init( GraphStorage graph )
    {
    }

    @Override
    public void create( long initSize )
    {
    }

    @Override
    public boolean loadExisting()
    {
        return true;
    }

    @Override
    public void setSegmentSize( int bytes )
    {
    }

    @Override
    public void flush( StorableProperties properties )
    {
    }

    @Override
    public void close()
    {
    }

    @Override
    public long getCapacity()
    {
        return 0;
    }

    @Override
    public ExtendedStorage copyTo( ExtendedStorage extStorage )
    {
        return this;
    }
}

public class ExtendedStorageManagerTest
{
    private ExtendedStorageManager manager;

    protected GraphStorage newGraph( Directory dir, int numStorages )
    {
        TreeMap extStorages = new TreeMap();
        for (int i = 0; i < numStorages; ++i)
        {
            extStorages.put(String.valueOf(i), new DummyExtendedStorage(String.valueOf(i), true, true));
        }
        manager = new ExtendedStorageManager(extStorages);
        GraphStorage graph = new GraphHopperStorage(dir, new EncodingManager("CAR"), false, manager);
        graph.create(0);
        graph.getNodeAccess().setNode(0, 0, 0);
        graph.getNodeAccess().setNode(1, 1, 0);
        graph.getNodeAccess().setNode(2, 2, 0);
        graph.getNodeAccess().setNode(3, 3, 0);
        graph.getNodeAccess().setNode(4, 4, 0);
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
        GraphStorage graph = newGraph(dir, 1);

        ExtendedStorageAccess access = graph.getExtendedStorageAccess();
        access.writeToExtendedNodeStorage("0", 0, 3);
        access.writeToExtendedNodeStorage("0", 1, 2);
        access.writeToExtendedNodeStorage("0", 2, 1);
        access.writeToExtendedNodeStorage("0", 3, 0);

        access.writeToExtendedEdgeStorage("0", 0, 3);
        access.writeToExtendedEdgeStorage("0", 1, 2);
        access.writeToExtendedEdgeStorage("0", 2, 1);

        assertEquals(3, access.readFromExtendedNodeStorage("0", 0));
        assertEquals(2, access.readFromExtendedNodeStorage("0", 1));
        assertEquals(1, access.readFromExtendedNodeStorage("0", 2));
        assertEquals(0, access.readFromExtendedNodeStorage("0", 3));

        assertEquals(3, access.readFromExtendedEdgeStorage("0", 0));
        assertEquals(2, access.readFromExtendedEdgeStorage("0", 1));
        assertEquals(1, access.readFromExtendedEdgeStorage("0", 2));
    }

    @Test
    public void testReloadFromDisk()
    {
        String defaultGraphLoc = "./target/graphstorage/default";
        Directory dir = new RAMDirectory(defaultGraphLoc, true);
        GraphStorage graph = newGraph(dir, 1);

        ExtendedStorageAccess access = graph.getExtendedStorageAccess();
        access.writeToExtendedNodeStorage("0", 0, 3);
        access.writeToExtendedNodeStorage("0", 1, 2);
        access.writeToExtendedNodeStorage("0", 2, 1);
        access.writeToExtendedNodeStorage("0", 3, 0);

        graph.flush();
        graph.close();

        dir = new RAMDirectory(defaultGraphLoc, true);
        graph = new GraphHopperStorage(dir, new EncodingManager("CAR"), false, manager);
        graph.loadExisting();
        access = graph.getExtendedStorageAccess();
        assertEquals(3, access.readFromExtendedNodeStorage("0", 0));
        assertEquals(2, access.readFromExtendedNodeStorage("0", 1));
        assertEquals(1, access.readFromExtendedNodeStorage("0", 2));
        assertEquals(0, access.readFromExtendedNodeStorage("0", 3));

    }

    @Test
    public void testReloadMultipleFromDisk()
    {
        String defaultGraphLoc = "./target/graphstorage/default";
        Directory dir = new RAMDirectory(defaultGraphLoc, true);
        GraphStorage graph = newGraph(dir, 2);

        ExtendedStorageAccess access = graph.getExtendedStorageAccess();
        access.writeToExtendedNodeStorage("0", 0, 1);
        access.writeToExtendedNodeStorage("0", 1, 2);
        access.writeToExtendedNodeStorage("1", 0, 3);
        access.writeToExtendedNodeStorage("1", 1, 4);

        access.writeToExtendedEdgeStorage("0", 0, 5);
        access.writeToExtendedEdgeStorage("0", 1, 6);
        access.writeToExtendedEdgeStorage("1", 0, 7);
        access.writeToExtendedEdgeStorage("1", 1, 8);

        graph.flush();
        graph.close();

        dir = new RAMDirectory(defaultGraphLoc, true);
        graph = new GraphHopperStorage(dir, new EncodingManager("CAR"), false, manager);
        graph.loadExisting();
        access = graph.getExtendedStorageAccess();
        assertEquals(1, access.readFromExtendedNodeStorage("0", 0));
        assertEquals(2, access.readFromExtendedNodeStorage("0", 1));
        assertEquals(3, access.readFromExtendedNodeStorage("1", 0));
        assertEquals(4, access.readFromExtendedNodeStorage("1", 1));

        assertEquals(5, access.readFromExtendedEdgeStorage("0", 0));
        assertEquals(6, access.readFromExtendedEdgeStorage("0", 1));
        assertEquals(7, access.readFromExtendedEdgeStorage("1", 0));
        assertEquals(8, access.readFromExtendedEdgeStorage("1", 1));
    }

    @Test
    public void testWriteMultipleReloadSingleFromDisk()
    {
        String defaultGraphLoc = "./target/graphstorage/default";
        Directory dir = new RAMDirectory(defaultGraphLoc, true);
        GraphStorage graph = newGraph(dir, 2);

        ExtendedStorageAccess access = graph.getExtendedStorageAccess();
        access.writeToExtendedNodeStorage("0", 0, 1);
        access.writeToExtendedNodeStorage("0", 1, 2);
        access.writeToExtendedNodeStorage("1", 0, 3);
        access.writeToExtendedNodeStorage("1", 1, 4);

        access.writeToExtendedEdgeStorage("0", 0, 5);
        access.writeToExtendedEdgeStorage("0", 1, 6);
        access.writeToExtendedEdgeStorage("1", 0, 7);
        access.writeToExtendedEdgeStorage("1", 1, 8);

        graph.flush();
        graph.close();

        // test access with only loading "0"
        TreeMap extStorages = new TreeMap();
        extStorages.put("0", new DummyExtendedStorage("0", true, true));
        manager = new ExtendedStorageManager(extStorages);

        dir = new RAMDirectory(defaultGraphLoc, true);
        graph = new GraphHopperStorage(dir, new EncodingManager("CAR"), false, manager);
        graph.loadExisting();
        access = graph.getExtendedStorageAccess();
        assertEquals(1, access.readFromExtendedNodeStorage("0", 0));
        assertEquals(2, access.readFromExtendedNodeStorage("0", 1));

        assertEquals(5, access.readFromExtendedEdgeStorage("0", 0));
        assertEquals(6, access.readFromExtendedEdgeStorage("0", 1));

        graph.close();

        // test access with only loading "1"
        extStorages = new TreeMap();
        extStorages.put("1", new DummyExtendedStorage("1", true, true));
        manager = new ExtendedStorageManager(extStorages);

        dir = new RAMDirectory(defaultGraphLoc, true);
        graph = new GraphHopperStorage(dir, new EncodingManager("CAR"), false, manager);
        graph.loadExisting();
        access = graph.getExtendedStorageAccess();

        assertEquals(3, access.readFromExtendedNodeStorage("1", 0));
        assertEquals(4, access.readFromExtendedNodeStorage("1", 1));

        assertEquals(7, access.readFromExtendedEdgeStorage("1", 0));
        assertEquals(8, access.readFromExtendedEdgeStorage("1", 1));

    }
}
