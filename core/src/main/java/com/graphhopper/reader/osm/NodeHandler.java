package com.graphhopper.reader.osm;

import static com.graphhopper.reader.osm.OSMNodeData.EMPTY_NODE;
import static com.graphhopper.reader.osm.OSMNodeData.JUNCTION_NODE;
import static com.graphhopper.util.Helper.nf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.graphhopper.reader.ReaderNode;
import com.graphhopper.reader.dem.ElevationProvider;
import com.graphhopper.util.Helper;

public class NodeHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(NodeHandler.class);

    private long nodeCounter = -1;
    private long acceptedNodes = 0;
    private long ignoredSplitNodes = 0;
    
    private final ElevationProvider eleProvider;
    private OSMNodeData nodeData;
    
    public NodeHandler(OSMNodeData nodeData, ElevationProvider eleProvider) {
    	this.nodeData = nodeData;
    	this.eleProvider = eleProvider;
    }

    public void handleNode(ReaderNode node) {
        if (++nodeCounter % 10_000_000 == 0)
            LOGGER.info("pass2 - processed nodes: " + nf(nodeCounter) + ", accepted nodes: " + nf(acceptedNodes) +
                    ", " + Helper.getMemInfo());

        int nodeType = nodeData.addCoordinatesIfMapped(node.getId(), node.getLat(), node.getLon(), () -> eleProvider.getEle(node));
        if (nodeType == EMPTY_NODE)
            return;

        acceptedNodes++;

        // we keep node tags for barrier nodes
        if (isBarrierNode(node)) {
            if (nodeType == JUNCTION_NODE) {
                LOGGER.debug("OSM node {} at {},{} is a barrier node at a junction. The barrier will be ignored",
                        node.getId(), Helper.round(node.getLat(), 7), Helper.round(node.getLon(), 7));
                ignoredSplitNodes++;
            } else
                nodeData.setTags(node);
        }
    }

    /**
     * @return true if the given node should be duplicated to create an artificial edge. If the node turns out to be a
     * junction between different ways this will be ignored and no artificial edge will be created.
     */
    protected boolean isBarrierNode(ReaderNode node) {
        return node.getTags().containsKey("barrier") || node.getTags().containsKey("ford");
    }
    
}
