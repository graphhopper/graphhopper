package com.graphhopper.reader;

import com.graphhopper.coll.GHLongIntBTree;
import com.graphhopper.coll.LongIntMap;

/**
 * Wrapper class around the osmID -> GH type/index value map to make it extensible.
 * Author: Nop
 */
public class OsmNodeTranslation2D implements OsmNodeTranslation
{
    // Using the correct Map<Long, Integer> is hard. We need a memory efficient and fast solution for big data sets!
    //
    // very slow: new SparseLongLongArray
    // only append and update possible (no unordered storage like with this doubleParse): new OSMIDMap
    // same here: not applicable as ways introduces the nodes in 'wrong' order: new OSMIDSegmentedMap
    // memory overhead due to open addressing and full rehash:
    //        nodeOsmIdToIndexMap = new BigLongIntMap(expectedNodes, EMPTY);
    // smaller memory overhead for bigger data sets because of avoiding a "rehash"
    // remember how many times a node was used to identify tower nodes
    private LongIntMap osmIDIndex;

    public OsmNodeTranslation2D()
    {
        osmIDIndex = new GHLongIntBTree(200);
    }

    /**
     * Return GH index or usage type for OSM node
     * @param nodeId
     * @return
     */
    @Override
    public int getIndex( long nodeId )
    {
        return osmIDIndex.get(nodeId);
    }

    @Override
    public void putIndex( long nodeId, int index )
    {
        osmIDIndex.put(nodeId, index);
    }

    @Override
    public void optimize()
    {
        osmIDIndex.optimize();
    }

    @Override
    public long getSize()
    {
        return osmIDIndex.getSize();
    }

    @Override
    public String toString()
    {
        return osmIDIndex.toString();
    }

    @Override
    public Object getMemoryUsage()
    {
        return osmIDIndex.getMemoryUsage();
    }

    @Override
    public int getElevation( long osmId )
    {
        return 0;
    }

    @Override
    public void copyNode( long nodeId, long id, int towerNode )
    {
        osmIDIndex.put( id, towerNode );
    }
}
