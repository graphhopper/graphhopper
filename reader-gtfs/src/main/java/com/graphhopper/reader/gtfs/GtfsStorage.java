package com.graphhopper.reader.gtfs;

import com.graphhopper.storage.Directory;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphExtension;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import org.mapdb.*;

import java.io.File;
import java.util.*;

class GtfsStorage implements GraphExtension{
	private int realEdgesSize;
	private Directory dir;
	private final TIntObjectMap<AbstractPtEdge> edges = new TIntObjectHashMap<>();
	private boolean flushed = false;

	public TIntObjectMap<AbstractPtEdge> getEdges() {
		return edges;
	}

	public void setRealEdgesSize(int realEdgesSize) {
		this.realEdgesSize = realEdgesSize;
	}

	public int getRealEdgesSize() {
		return realEdgesSize;
	}



	private DB data;

	@Override
	public boolean isRequireNodeField() {
		return false;
	}

	@Override
	public boolean isRequireEdgeField() {
		return true;
	}

	@Override
	public int getDefaultNodeFieldValue() {
		return 0;
	}

	@Override
	public int getDefaultEdgeFieldValue() {
		return 0;
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
		this.realEdgesSize = data.getAtomicInteger("realEdgesSize").get();
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
			data.getAtomicInteger("realEdgesSize").set(realEdgesSize);
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
}
