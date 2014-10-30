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

import java.util.*;

/**
 * ExtendedStorageManager is an ExtendedStorage by itself and can manage multiple sub-storages. For
 * each ExtendedStorage that is passed to the constructor during graph creation, space will be
 * created to hold references for this extended storage. When reading a graph from disk, the manager
 * will check the properties file to check if it is used and if it is, it will restore its state
 * with the help of the properties file
 * <p>
 * @author Adrian Batzill, Agata
 */
public class ExtendedStorageManager implements ExtendedStorage
{
    private final static String USE = "extendedStorageManager.use";
    private final static String NODE_STORAGES = "extendedStorageManager.nodeStorages";
    private final static String EDGE_STORAGES = "extendedStorageManager.edgeStorages";

    private Directory storageDirectory;
    private DataAccess nodeExtStorageRefs;
    private DataAccess edgeExtStorageRefs;
    private final TreeMap<String, ExtendedStorage> extStorages;
    private final HashMap<String, Integer> storageIndicesNodes = new HashMap<String, Integer>();
    private final HashMap<String, Integer> storageIndicesEdges = new HashMap<String, Integer>();

    private int refNodeEntryBytes = -1;
    private int refEdgeEntryBytes = -1;

    public final static int NO_REFERENCE = -1;

    /**
     * @param extStorages A TreeMap with maps some arbitrary key to an ExtendedStorage. The key will
     * later be used to identify the storage. A TreeMap is required, as the file-layout of the
     * ExtendedStorageManager DataAccess objects will be sorted by the key-string of the
     * sub-storages
     */
    public ExtendedStorageManager( TreeMap<String, ExtendedStorage> extStorages )
    {
        this.extStorages = extStorages;
        int indexNode = 0;
        int indexEdge = 0;
        for (String k : extStorages.keySet())
        {
            if (extStorages.get(k).isRequireNodeField())
            {
                storageIndicesNodes.put(k, indexNode++);
            }
            if (extStorages.get(k).isRequireEdgeField())
            {
                storageIndicesEdges.put(k, indexEdge++);
            }
        }
        refNodeEntryBytes = 4 * storageIndicesNodes.size();
        refEdgeEntryBytes = 4 * storageIndicesEdges.size();
    }

    public static void loadStorageIndicesFromDisk( StorableProperties properties, ExtendedStorage providedStorage )
    {
        String use = properties.get(USE);
        String nodeStorages = properties.get(NODE_STORAGES);
        String edgeStorages = properties.get(EDGE_STORAGES);

        if (use == null || use.equals(""))
        {
            return;
        }

        if (!(providedStorage instanceof ExtendedStorageManager))
        {
            throw new IllegalStateException("A graph that was created with an ExtendedStorageManager can not be loaded without an ExtendedStorageManager");
        }

        ExtendedStorageManager manager = (ExtendedStorageManager) providedStorage;
        String[] nodeIdentifiers = nodeStorages.split(",");
        String[] edgeIdentifiers = edgeStorages.split(",");
        Arrays.sort(nodeIdentifiers);
        Arrays.sort(edgeIdentifiers);
        manager.refNodeEntryBytes = nodeIdentifiers.length * 4;
        manager.refEdgeEntryBytes = edgeIdentifiers.length * 4;
        for (int i = 0; i < nodeIdentifiers.length; ++i)
        {
            if (manager.storageIndicesNodes.containsKey(nodeIdentifiers[i]))
            {
                manager.storageIndicesNodes.put(nodeIdentifiers[i], i);
            }
        }

        for (int i = 0; i < edgeIdentifiers.length; ++i)
        {
            if (manager.storageIndicesEdges.containsKey(edgeIdentifiers[i]))
            {
                manager.storageIndicesEdges.put(edgeIdentifiers[i], i);
            }
        }
    }

    public ExtendedStorage getExtendedStorage( String storageName )
    {
        return extStorages.get(storageName);
    }

    @Override
    public boolean isRequireNodeField()
    {
        return storageIndicesNodes.size() > 0;
    }

    @Override
    public boolean isRequireEdgeField()
    {
        return storageIndicesEdges.size() > 0;
    }

    @Override
    public int getDefaultNodeFieldValue()
    {
        return NO_REFERENCE;
    }

    @Override
    public int getDefaultEdgeFieldValue()
    {
        return NO_REFERENCE;
    }

    @Override
    public void init( GraphStorage graph )
    {
        this.storageDirectory = graph.getDirectory();
        nodeExtStorageRefs = storageDirectory.find("nodeExtStorageRefs");
        edgeExtStorageRefs = storageDirectory.find("edgeExtStorageRefs");

        for (ExtendedStorage extStorage : extStorages.values())
        {
            extStorage.init(graph);
        }
    }

    @Override
    public void create( long initSize )
    {
        for (ExtendedStorage extStorage : extStorages.values())
        {
            extStorage.create(initSize);
        }

        if (isRequireNodeField())
        {
            nodeExtStorageRefs.create(initSize);
        }
        if (isRequireEdgeField())
        {
            edgeExtStorageRefs.create(initSize);
        }
    }

    @Override
    public boolean loadExisting()
    {
        nodeExtStorageRefs.loadExisting();
        edgeExtStorageRefs.loadExisting();

        boolean successAll = true;
        for (ExtendedStorage extStorage : extStorages.values())
        {
            if (!extStorage.loadExisting())
            {
                successAll = false;
            }
        }
        return successAll;
    }

    @Override
    public void setSegmentSize( int bytes )
    {
        nodeExtStorageRefs.setSegmentSize(bytes);
        edgeExtStorageRefs.setSegmentSize(bytes);

        for (ExtendedStorage extStorage : extStorages.values())
        {
            extStorage.setSegmentSize(bytes);
        }
    }

    public void ensureNodeCapacity( int nodeId )
    {
        nodeExtStorageRefs.incCapacity((nodeId + 1) * refNodeEntryBytes);
    }

    public void ensureEdgeCapacity( int edgeId )
    {
        edgeExtStorageRefs.incCapacity((edgeId + 1) * refEdgeEntryBytes);
    }

    public int setNodeReference( String storageName, int nodeId, int value )
    {
        ensureNodeCapacity(nodeId);
        long extIndex = storageIndicesNodes.get(storageName);
        long byteOffset = nodeId * refNodeEntryBytes + extIndex * 4;

        nodeExtStorageRefs.setInt(byteOffset, value);
        return nodeId;
    }

    public int getNodeReference( String storageName, int nodeId )
    {
        if (nodeExtStorageRefs.getCapacity() < refNodeEntryBytes * (nodeId + 1))
        {
            throw new IllegalStateException("Extended storage manager access out of bounds");
        }

        long extIndex = storageIndicesNodes.get(storageName);

        long byteOffset = nodeId * refNodeEntryBytes + extIndex * 4;
        return nodeExtStorageRefs.getInt(byteOffset);
    }

    public int setEdgeReference( String storageName, int edgeId, int value )
    {
        ensureEdgeCapacity(edgeId);
        long extIndex = storageIndicesEdges.get(storageName);
        long byteOffset = edgeId * refEdgeEntryBytes + extIndex * 4;

        edgeExtStorageRefs.setInt(byteOffset, value);
        return edgeId;
    }

    public int getEdgeReference( String storageName, int edgeId )
    {
        if (edgeExtStorageRefs.getCapacity() < refEdgeEntryBytes * (edgeId + 1))
        {
            throw new IllegalStateException("Extended storage manager access out of bounds");
        }

        long extIndex = storageIndicesEdges.get(storageName);

        long byteOffset = edgeId * refEdgeEntryBytes + extIndex * 4;
        return edgeExtStorageRefs.getInt(byteOffset);
    }

    @Override
    public void flush( StorableProperties properties )
    {
        if (isRequireNodeField())
        {
            nodeExtStorageRefs.flush();
        }
        if (isRequireEdgeField())
        {
            edgeExtStorageRefs.flush();
        }

        for (ExtendedStorage extStorage : extStorages.values())
        {
            extStorage.flush(properties);
        }

        properties.put(USE, "true");
        properties.put(NODE_STORAGES, String.join(",", storageIndicesNodes.keySet()));
        properties.put(EDGE_STORAGES, String.join(",", storageIndicesEdges.keySet()));
    }

    @Override
    public void close()
    {
        if (isRequireNodeField())
        {
            nodeExtStorageRefs.close();
        }
        if (isRequireEdgeField())
        {
            edgeExtStorageRefs.close();
        }

        for (ExtendedStorage extStorage : extStorages.values())
        {
            extStorage.close();
        }
    }

    @Override
    public long getCapacity()
    {
        long capacity = nodeExtStorageRefs.getCapacity() + edgeExtStorageRefs.getCapacity();
        for (ExtendedStorage extStorage : extStorages.values())
        {
            capacity += extStorage.getCapacity();
        }
        return capacity;
    }

    @Override
    public ExtendedStorage copyTo( ExtendedStorage extStorage )
    {
        if (!(extStorage instanceof ExtendedStorageManager))
        {
            throw new IllegalStateException("The extended storage to clone must be the same");
        }

        ExtendedStorageManager other = (ExtendedStorageManager) extStorage;

        for (String key : extStorages.keySet())
        {
            if (other.getExtendedStorage(key) == null)
            {
                throw new IllegalStateException("The extended storage manager to clone must have the same extended storages");
            }
            extStorages.get(key).copyTo(other.getExtendedStorage(key));
        }

        if (isRequireNodeField())
        {
            nodeExtStorageRefs.copyTo(other.nodeExtStorageRefs);
        }
        if (isRequireEdgeField())
        {
            edgeExtStorageRefs.copyTo(other.edgeExtStorageRefs);
        }

        return other;
    }

}
