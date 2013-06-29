package com.graphhopper.reader;

import com.graphhopper.coll.GHLongLongBTree;
import com.graphhopper.reader.dem.DemArea;
import com.graphhopper.storage.DataAccess;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.util.Helper;
import com.graphhopper.util.shapes.BBox;

/**
 * Encapsulates the different data sources and classes that handle geometry.
 * Author: Nop
 */
public class GeometryAccess
{
    // tower data
    private GraphStorage graph;

    // pillar data
    private OSMReaderHelper helper;

    // dem data
    private DemArea dem;
    private boolean is3D = false;

    public GeometryAccess(GraphStorage graph, OSMReaderHelper helper)
    {
        this.graph = graph;
        this.helper = helper;
    }

    public void initDem(String demLocation, BBox bounds)
    {
        if(demLocation == null)
        {
            throw new IllegalArgumentException("Path to DEM data cannot be empty.");
        }

        dem = new DemArea(demLocation, 4, bounds.minLon, bounds.minLat, bounds.maxLon, bounds.maxLat);
        dem.load();
        is3D = true;
    }

    /**
     * Get the coordinates of a node without knowing the actual type and storage.
     * Provides lat/lon in the node array. In 3D mode also fills in elevation.
     * @param osmId
     * @param node
     * @return
     */
    public boolean getNode(long osmId, int[] node)
    {
        int index = helper.getNodeIndex(osmId);
        if(index == OSMReaderHelper.EMPTY)
        {
            return false;
        }

        if(index < OSMReaderHelper.TOWER_NODE)
        {
            index = -index - 3;
            graph.getLocation(index, node);
        }
        else
        {
            index = index - 3;
            helper.getLocation(index, node);
        }
        node[2] = helper.getNodeMap().getElevation(osmId);
        return true;
    }

    public int getElevation(long osmId)
    {
        return helper.getNodeMap().getElevation(osmId);
    }

    public int getElevation(double lat, double lon)
    {
        return dem.get(lat, lon);
    }

    /**
     * Add elevation values to all nodes
     * todo: do this by region to limit memory usage
     */
    public void initializeElevations()
    {
        final int[] node = new int[3];

        final OsmNodeTranslation3D nodeTranslation = (OsmNodeTranslation3D) helper.getNodeMap();
        nodeTranslation.vistKeys( new GHLongLongBTree.Visitor()
        {
            @Override
            public void processKey( long osmId )
            {
                int index = nodeTranslation.getIndex(osmId);

                if (index == -1)
                {
                    System.out.println("Node id does not exist??? " + osmId );
                }

                // get coordinates from graph or pillar lists
                if(index < OSMReaderHelper.TOWER_NODE)
                {
                    index = -index - 3;
                    graph.getLocation(index, node);
                }
                else
                {
                    index = index - 3;
                    helper.getLocation(index, node);
                }

                int ele = getElevation(Helper.intToDegree( node[0] ), Helper.intToDegree( node[1]));
                nodeTranslation.setElevation( osmId, ele );
            }
        });

    }
}
