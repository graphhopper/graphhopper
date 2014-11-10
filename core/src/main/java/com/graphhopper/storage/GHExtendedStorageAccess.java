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

public class GHExtendedStorageAccess implements ExtendedStorageAccess
{
    GraphHopperStorage graph;

    public GHExtendedStorageAccess( GraphHopperStorage graph )
    {
        this.graph = graph;
    }

    @Override
    public void writeToExtendedNodeStorage( String storageName, int nodeId, int value )
    {
        if (graph.extStorage instanceof ExtendedStorageManager)
        {
            ExtendedStorageManager manager = (ExtendedStorageManager) graph.extStorage;
            int refIndex = manager.setNodeReference(storageName, nodeId, value);
            long tmp = (long) nodeId * graph.nodeEntryBytes;
            graph.ensureNodeIndex(nodeId);
            graph.nodes.setInt(tmp + graph.N_ADDITIONAL, refIndex);
        } else
        {
            graph.ensureNodeIndex(nodeId);
            long tmp = (long) nodeId * graph.nodeEntryBytes;
            graph.nodes.setInt(tmp + graph.N_ADDITIONAL, value);
        }
    }

    @Override
    public int readFromExtendedNodeStorage( String storageName, int nodeId )
    {
        int refIndex = graph.nodes.getInt((long) nodeId * graph.nodeEntryBytes + graph.N_ADDITIONAL);
        if (graph.extStorage instanceof ExtendedStorageManager)
        {
            ExtendedStorageManager manager = (ExtendedStorageManager) graph.extStorage;
            return manager.getNodeReference(storageName, refIndex);
        }
        return refIndex;
    }

    @Override
    public void writeToExtendedEdgeStorage( String storageName, int edgeId, int value )
    {
        if (graph.extStorage.isRequireEdgeField() && graph.E_ADDITIONAL >= 0)
        {
            if (graph.extStorage instanceof ExtendedStorageManager)
            {
                ExtendedStorageManager manager = (ExtendedStorageManager) graph.extStorage;
                int refIndex = manager.setEdgeReference(storageName, edgeId, value);
                long tmp = (long) edgeId * graph.edgeEntryBytes;
                graph.edges.setInt(tmp + graph.E_ADDITIONAL, refIndex);
            } else
            {
                long tmp = (long) edgeId * graph.edgeEntryBytes;
                graph.edges.setInt(tmp + graph.E_ADDITIONAL, value);
            }
        } else
            throw new AssertionError("This graph does not support an additional edge field.");
    }

    @Override
    public int readFromExtendedEdgeStorage( String storageName, int edgeId )
    {
        int refIndex = graph.edges.getInt((long) edgeId * graph.edgeEntryBytes + graph.E_ADDITIONAL);
        if (graph.extStorage instanceof ExtendedStorageManager)
        {
            ExtendedStorageManager manager = (ExtendedStorageManager) graph.extStorage;
            return manager.getEdgeReference(storageName, refIndex);
        }
        return refIndex;
    }
}
