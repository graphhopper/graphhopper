package com.graphhopper.reader.gtfs;

import com.conveyal.gtfs.GTFSFeed;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;

class GtfsStorage {
	private GTFSFeed feed;
	private final TIntObjectMap<PatternHopEdge> edges = new TIntObjectHashMap<>();
	private int realEdgesSize;

	void setFeed(GTFSFeed feed) {
		this.feed = feed;
	}

	GTFSFeed getFeed() {
		return feed;
	}

	public TIntObjectMap<PatternHopEdge> getEdges() {
		return edges;
	}

	public void setRealEdgesSize(int realEdgesSize) {
		this.realEdgesSize = realEdgesSize;
	}

	public int getRealEdgesSize() {
		return realEdgesSize;
	}
}
