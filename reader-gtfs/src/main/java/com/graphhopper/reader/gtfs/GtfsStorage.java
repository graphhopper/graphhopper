package com.graphhopper.reader.gtfs;

import com.graphhopper.routing.VirtualEdgeIteratorState;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphExtension;
import com.graphhopper.util.EdgeIterator;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import org.mapdb.*;

import java.io.File;
import java.time.LocalDate;
import java.util.*;

class GtfsStorage implements GraphExtension {
	private Directory dir;
	private final TIntObjectMap<AbstractPtEdge> edges = new TIntObjectHashMap<>();
	private boolean flushed = false;
	private LocalDate startDate;

    static EdgeType getEdgeType(EdgeIterator iter) {
        return EdgeType.values()[iter.getAdditionalField()];
    }

    enum EdgeType {
        UNSPECIFIED, ENTER_TIME_EXPANDED_NETWORK, LEAVE_TIME_EXPANDED_NETWORK, STOP_NODE_MARKER_EDGE, STOP_EXIT_NODE_MARKER_EDGE, HOP_EDGE, DWELL_EDGE, BOARD_EDGE, OVERNIGHT_EDGE, TRANSFER_EDGE, WAIT_IN_STATION_EDGE, TIME_PASSES_PT_EDGE;
    }

	public TIntObjectMap<AbstractPtEdge> getEdges() {
		return edges;
	}



	private DB data;

	@Override
	public boolean isRequireNodeField() {
		return false;
	}

	@Override
	public boolean isRequireEdgeField() {
		return false;
	}

	@Override
	public int getDefaultNodeFieldValue() {
		return 0;
	}

	@Override
	public int getDefaultEdgeFieldValue() {
		return EdgeType.UNSPECIFIED.ordinal();
	}

	@Override
	public void init(Graph graph, Directory dir) {
		this.dir = dir;
	}

	@Override
	public void setSegmentSize(int bytes) {

	}

	@Override
	public GraphExtension copyTo(GraphExtension extStorage) {
		throw new UnsupportedOperationException("copyTo not yet supported");
	}

	@Override
	public boolean loadExisting() {
		this.data = DBMaker.newFileDB(new File(dir.getLocation() + "/transit_schedule")).readOnly().make();
		BTreeMap<Integer, AbstractPtEdge> edges = data.getTreeMap("edges");
		for (Map.Entry<Integer, AbstractPtEdge> entry : edges.entrySet()) {
			this.edges.put(entry.getKey(), entry.getValue());
		}
		this.startDate = (LocalDate) data.getAtomicVar("startDate").get();
		flushed = true;
		return true;
	}

	@Override
	public GraphExtension create(long byteCount) {
		this.data = DBMaker.newFileDB(new File(dir.getLocation() + "/transit_schedule")).transactionDisable().mmapFileEnable().asyncWriteEnable().make();
		return this;
	}

	@Override
	public void flush() {
		if (!flushed) {
			TreeSet<Integer> wurst = new TreeSet<>();
			for (int i : edges.keys()) {
				wurst.add(i);
			}
			data.createTreeMap("edges").pumpSource(wurst.descendingSet().iterator(), new Fun.Function1<Object, Integer>() {
				@Override
				public Object run(Integer i) {
					return edges.get(i);
				}
			}).make();
			data.getAtomicVar("startDate").set(startDate);
			data.close();
			flushed = true;
		}
	}

	@Override
	public void close() {
	}

	@Override
	public boolean isClosed() {
		// Flushing and closing is the same for us.
		// We use the data store only for dumping to file, not as a live data structure.
		return flushed;
	}

	@Override
	public long getCapacity() {
		return 0;
	}

	public void setEdges(final TreeMap<Integer, AbstractPtEdge> edges) {
		for (Integer integer : edges.descendingKeySet()) {
			this.edges.put(integer, edges.get(integer));
		}
	}

	public void setStartDate(LocalDate startDate) {
		this.startDate = startDate;
	}

	public LocalDate getStartDate() {
		return startDate;
	}
}
