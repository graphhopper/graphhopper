package com.graphhopper.reader;

import com.graphhopper.coll.GHLongIntBTree;
import com.graphhopper.coll.GHLongLongBTree;
import com.graphhopper.coll.LongIntMap;
import com.graphhopper.coll.LongLongMap;

/**
 * This class stores data for an OSM node
 * - GH node type or index
 * - elevation value
 * Author: Nop
 */
public class OsmNodeTranslation3D implements OsmNodeTranslation
{
    public static final long ELE_OFFSET = 10000L;
    public static final long INDEX_OFFSET = (long) Integer.MAX_VALUE;
    public static final long INDEX_MASK = 0xFFFFFFFF;

    // Using the correct Map<Long, Integer> is hard. We need a memory efficient and fast solution for big data sets!
    //
    // very slow: new SparseLongLongArray
    // only append and update possible (no unordered storage like with this doubleParse): new OSMIDMap
    // same here: not applicable as ways introduces the nodes in 'wrong' order: new OSMIDSegmentedMap
    // memory overhead due to open addressing and full rehash:
    //        nodeOsmIdToIndexMap = new BigLongIntMap(expectedNodes, EMPTY);
    // smaller memory overhead for bigger data sets because of avoiding a "rehash"
    // remember how many times a node was used to identify tower nodes
    private GHLongLongBTree osmNodeData;

    private long lastId;
    private int index;
    private int ele;

    public OsmNodeTranslation3D()
    {
        osmNodeData = new GHLongLongBTree(200);
        lastId = 0;
    }

    private boolean find( long nodeId )
    {
        // data is still stored in members
        if( nodeId == lastId )
            return true;

        long data = osmNodeData.get(nodeId);
        if (data == -1)
        {
            lastId = 0;
            return false;
        }

        index = (int) ((data & INDEX_MASK) - INDEX_OFFSET);
        ele = (int) ((data >> 32) - ELE_OFFSET);
        lastId = nodeId;

        return true;
    }

    private void store( long nodeId, int index, int ele )
    {
        this.index = index;
        this.ele = ele;
        lastId = nodeId;

        long indexData = (long) index + INDEX_OFFSET;
        long eleData = ((long) ele + ELE_OFFSET) << 32;

        osmNodeData.put(nodeId, indexData | eleData);
    }

    /**
     * Return GH index or usage type for OSM node
     *
     * @param nodeId
     * @return
     */
    @Override
    public int getIndex( long nodeId )
    {
        if( find(nodeId) )
            return index;

        return -1;
    }

    @Override
    public void putIndex( long nodeId, int index )
    {
        if( find( nodeId ))
        {
            store( nodeId, index, ele );
        }
        else
            store( nodeId, index, 0 );
    }

    @Override
    public void optimize()
    {
        osmNodeData.optimize();
    }

    @Override
    public long getSize()
    {
        return osmNodeData.getSize();
    }

    @Override
    public String toString()
    {
        return osmNodeData.toString();
    }

    @Override
    public Object getMemoryUsage()
    {
        return osmNodeData.getMemoryUsage();
    }


    public void vistKeys( GHLongLongBTree.Visitor visitor )
    {
        osmNodeData.apply( visitor );
    }

    @Override
    public int getElevation( long nodeId )
    {
        if( find(nodeId) )
            return ele;

        throw new IllegalArgumentException( "Elevation request for unknown node " + nodeId );
    }

    @Override
    public void copyNode( long nodeId, long id, int towerNode )
    {
        if( find( nodeId ) )
        {
            store( id, towerNode, ele );
        }
        else
            throw new IllegalArgumentException("Invalid source node for copy operation.");
    }

    /**
     * Update existing entry with 3D elevation value
     * @param osmId
     * @param ele
     */
    public void setElevation( long osmId, int ele )
    {
        if( find(osmId))
            store(osmId, index, ele );
        else
            throw new IllegalArgumentException("Cannot set elevation for unknown OSM ID " + osmId );
    }
}
