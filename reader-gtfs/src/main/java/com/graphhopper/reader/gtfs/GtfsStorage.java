package com.graphhopper.reader.gtfs;

import com.conveyal.gtfs.model.Fare;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphExtension;
import org.mapdb.*;

import java.io.File;
import java.time.LocalDate;
import java.util.BitSet;
import java.util.Collections;
import java.util.Map;

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
    private HTreeMap<BitSet, Integer> operatingDayPatterns;
	private Map<Integer, BitSet> reverseOperatingDayPatterns;
	private boolean flushed = false;
	private Atomic.Var<LocalDate> startDate;
	private Map<Integer, String> extra;
	private Map<String, Fare> fares;

	enum EdgeType {
        UNSPECIFIED, ENTER_TIME_EXPANDED_NETWORK, LEAVE_TIME_EXPANDED_NETWORK, ENTER_PT, EXIT_PT, HOP, DWELL_EDGE, BOARD, OVERNIGHT_EDGE, TRANSFER, WAIT_IN_STATION_EDGE, TIME_PASSES
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
		init();
		return true;
	}

	@Override
	public GraphExtension create(long byteCount) {
		this.data = DBMaker.newFileDB(new File(dir.getLocation() + "/transit_schedule")).transactionDisable().mmapFileEnable().asyncWriteEnable().closeOnJvmShutdown().make();
		init();
		return this;
	}

	private void init() {
		this.operatingDayPatterns = data.getHashMap("validities");
		this.reverseOperatingDayPatterns = data.getTreeMap("reverseValidities");
		Bind.mapInverse(this.operatingDayPatterns, this.reverseOperatingDayPatterns);
		this.startDate = data.getAtomicVar("startDate");
		this.extra = data.getTreeMap("extra");
		this.fares = data.getTreeMap("fares");
	}

	@Override
	public void flush() {
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
		this.startDate.set(startDate);
	}

	LocalDate getStartDate() {
		return startDate.get();
	}

    Map<BitSet, Integer> getOperatingDayPatterns() {
        return operatingDayPatterns;
    }

	Map<Integer, BitSet> getReverseOperatingDayPatterns() {
		return Collections.unmodifiableMap(reverseOperatingDayPatterns);
	}

	Map<Integer, String> getExtraStrings() {
		return extra;
	}

	Map<String, Fare> getFares() {
		return fares;
	}

}
