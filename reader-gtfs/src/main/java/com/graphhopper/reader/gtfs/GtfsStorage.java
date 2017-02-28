package com.graphhopper.reader.gtfs;

import com.conveyal.gtfs.model.Fare;
import com.graphhopper.routing.VirtualEdgeIteratorState;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphExtension;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import gnu.trove.procedure.TObjectIntProcedure;
import org.mapdb.*;

import java.io.File;
import java.time.LocalDate;
import java.util.*;

public class GtfsStorage implements GraphExtension {

	static long traveltime(int edgeTimeValue, long earliestStartTime) {
        int timeOfDay = (int) (earliestStartTime % (24*60*60));
        if (timeOfDay <= edgeTimeValue) {
            return (edgeTimeValue - timeOfDay);
        } else {
            throw new RuntimeException();
        }
    }

	static long traveltimeReverse(int edgeTimeValue, long latestExitTime) {
		int timeOfDay = (int) (latestExitTime % (24*60*60));
		if (timeOfDay >= edgeTimeValue) {
			return (timeOfDay - edgeTimeValue);
		} else {
			throw new RuntimeException();
		}
	}

    private Directory dir;
    private TIntObjectHashMap<BitSet> reverseOperatingDayPatterns;
    private final TObjectIntMap<BitSet> operatingDayPatterns = new TObjectIntHashMap<>();
	private boolean flushed = false;
	private LocalDate startDate;
	private Map<Integer, String> extra;
	private Map<String, Fare> fares;

	enum EdgeType {
        UNSPECIFIED, ENTER_TIME_EXPANDED_NETWORK, LEAVE_TIME_EXPANDED_NETWORK, ENTER_PT, EXIT_PT, HOP, DWELL_EDGE, BOARD, OVERNIGHT_EDGE, TRANSFER, WAIT_IN_STATION_EDGE, TIME_PASSES;
    }

	private DB data;

	@Override
	public boolean isRequireNodeField() {
		return true;
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
        reverseOperatingDayPatterns = new TIntObjectHashMap<BitSet>();
        BTreeMap<Integer, BitSet> operatingDayPatterns = data.getTreeMap("validities");
		for (Map.Entry<Integer, BitSet> entry : operatingDayPatterns.entrySet()) {
			this.reverseOperatingDayPatterns.put(entry.getKey(), entry.getValue());
		}
		this.startDate = (LocalDate) data.getAtomicVar("startDate").get();
		this.extra = data.getTreeMap("extra");
		this.fares = data.getTreeMap("fares");
		flushed = true;
		return true;
	}

	@Override
	public GraphExtension create(long byteCount) {
		this.data = DBMaker.newFileDB(new File(dir.getLocation() + "/transit_schedule")).transactionDisable().mmapFileEnable().asyncWriteEnable().make();
		this.extra = data.getTreeMap("extra");
		this.fares = data.getTreeMap("fares");
		return this;
	}

    TIntObjectHashMap<BitSet> getReverseOperatingDayPatterns() {
        return reverseOperatingDayPatterns;
    }

    @Override
	public void flush() {
		if (!flushed) {
            reverseOperatingDayPatterns = new TIntObjectHashMap<BitSet>();
            operatingDayPatterns.forEachEntry(new TObjectIntProcedure<BitSet>() {
                @Override
                public boolean execute(BitSet bitSet, int i) {
                    reverseOperatingDayPatterns.put(i, bitSet);
                    return true;
                }
            });
			TreeSet<Integer> wurst = new TreeSet<>();
			for (int i : reverseOperatingDayPatterns.keys()) {
				wurst.add(i);
			}
			data.createTreeMap("validities").pumpSource(wurst.descendingSet().iterator(), new Fun.Function1<Object, Integer>() {
				@Override
				public Object run(Integer i) {
					return reverseOperatingDayPatterns.get(i);
				}
			}).make();
			data.getAtomicVar("startDate").set(startDate);
			flushed = true;
		}
	}

	@Override
	public void close() {
		data.close();
	}

	@Override
	public boolean isClosed() {
		return data.isClosed();
	}

	@Override
	public long getCapacity() {
		return 0;
	}

	void setStartDate(LocalDate startDate) {
		this.startDate = startDate;
	}

	LocalDate getStartDate() {
		return startDate;
	}

    TObjectIntMap<BitSet> getOperatingDayPatterns() {
        return operatingDayPatterns;
    }

    void setExtraString(EdgeIteratorState edge, String string) {
		extra.put(edge.getEdge(), string);
	}

	String getExtraString(EdgeIteratorState edge) {
		return extra.get(edge.getEdge());
	}

	Map<String, Fare> getFares() {
		return fares;
	}

}
