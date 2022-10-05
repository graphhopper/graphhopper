package com.graphhopper.reader.osm;

import static com.graphhopper.reader.osm.OSMNodeData.CONNECTION_NODE;
import static com.graphhopper.reader.osm.OSMNodeData.END_NODE;
import static com.graphhopper.reader.osm.OSMNodeData.INTERMEDIATE_NODE;
import static com.graphhopper.reader.osm.OSMNodeData.JUNCTION_NODE;
import static com.graphhopper.util.Helper.nf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.carrotsearch.hppc.cursors.LongCursor;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.util.OSMParsers;
import com.graphhopper.util.Helper;

public class WayPreprocessor extends WayHandlerBase {
    private static final Logger LOGGER = LoggerFactory.getLogger(WayHandler.class);
	
    private long wayCounter = 1;
    private long acceptedWays = 0;

    private OSMNodeData nodeData;
    private OSMTurnRestrictionData restrictionData;
    
	public WayPreprocessor(OSMParsers osmParsers, OSMNodeData nodeData, OSMTurnRestrictionData restrictionData) {
		super(osmParsers);
		this.nodeData = nodeData;
		this.restrictionData = restrictionData;
	}

    public void preprocessWay(ReaderWay way) {
        if (++wayCounter % 10_000_000 == 0)
            LOGGER.info("pass1 - processed ways: " + nf(wayCounter) + ", accepted ways: " + nf(acceptedWays) +
                    ", way nodes: " + nf(nodeData.getNodeCount()) + ", " + Helper.getMemInfo());

        if (!acceptWay(way))
            return;
        acceptedWays++;

        mapWayIfPartOfViaWayTurnRestriction(way);

        for (LongCursor node : way.getNodes()) {
            final boolean isEnd = node.index == 0 || node.index == way.getNodes().size() - 1;
            final long osmId = node.value;
            nodeData.setOrUpdateNodeType(osmId,
                    isEnd ? END_NODE : INTERMEDIATE_NODE,
                    // connection nodes are those where (only) two OSM ways are connected at their ends
                    prev -> prev == END_NODE && isEnd ? CONNECTION_NODE : JUNCTION_NODE);
        }
    }

	
    protected void mapWayIfPartOfViaWayTurnRestriction(ReaderWay way) {
        if (restrictionData.osmWayMap.containsKey(way.getId())) {
        	restrictionData.osmWayMap.put(way.getId(), way);
        }
    }


}
