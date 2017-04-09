package com.graphhopper.reader.gtfs;

import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.Fare;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphExtension;
import org.mapdb.*;

import java.io.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.zip.ZipFile;

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
	private Set<String> gtfsFeedIds;
	private Map<String, GTFSFeed> gtfsFeeds = new HashMap<>();
	private HTreeMap<BitSet, Integer> operatingDayPatterns;
	private Map<Integer, BitSet> validities;
	private Atomic.Var<LocalDate> startDate;
	private Map<Integer, String> extra;
	private Map<Integer, Integer> stopSequences;
	private Map<String, Fare> fares;

	enum EdgeType {
		HIGHWAY, ENTER_TIME_EXPANDED_NETWORK, LEAVE_TIME_EXPANDED_NETWORK, ENTER_PT, EXIT_PT, HOP, DWELL, BOARD, ALIGHT, OVERNIGHT, TRANSFER, WAIT
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
		return EdgeType.HIGHWAY.ordinal();
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
		for (String gtfsFeedId : this.gtfsFeedIds) {
			try {
				GTFSFeed feed = new GTFSFeed(dir.getLocation() + "/" + gtfsFeedId);
				Runtime.getRuntime().addShutdownHook(new Thread(feed::close));
				this.gtfsFeeds.put(gtfsFeedId, feed);
			} catch (IOException | ExecutionException e) {
				throw new RuntimeException(e);
			}
		}
		return true;
	}

	@Override
	public GraphExtension create(long byteCount) {
		this.data = DBMaker.newFileDB(new File(dir.getLocation() + "/transit_schedule")).transactionDisable().mmapFileEnable().asyncWriteEnable().closeOnJvmShutdown().make();
		init();
		return this;
	}

	private void init() {
		this.gtfsFeedIds = data.getHashSet("gtfsFeeds");
		this.operatingDayPatterns = data.getHashMap("validities");
		Map<Integer, BitSet> reverseOperatingDayPatterns = new HashMap<>();
		for (Map.Entry<BitSet, Integer> entry : this.operatingDayPatterns.entrySet()) {
			reverseOperatingDayPatterns.put(entry.getValue(), entry.getKey());
		}
		Bind.mapInverse(this.operatingDayPatterns, reverseOperatingDayPatterns);
		this.validities = Collections.unmodifiableMap(reverseOperatingDayPatterns);
		this.startDate = data.getAtomicVar("startDate");
		this.extra = data.getTreeMap("extra");
		this.stopSequences = data.getTreeMap("stopSequences");
		this.fares = data.getTreeMap("fares");
	}

	void loadGtfsFromFile(String id, ZipFile zip) {
		try {
			GTFSFeed feed = new GTFSFeed(dir.getLocation() + "/" + id);
			Runtime.getRuntime().addShutdownHook(new Thread(feed::close));
			feed.loadFromFile(zip);
			this.gtfsFeeds.put(id, feed);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		this.gtfsFeedIds.add(id);
	}

	@Override
	public void flush() {
	}

	@Override
	public void close() {
		data.close();
		for (GTFSFeed feed : gtfsFeeds.values()) {
			feed.close();
		}
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

	Map<Integer, BitSet> getValidities() {
		return validities;
	}

	Map<Integer, String> getExtraStrings() {
		return extra;
	}

	Map<Integer, Integer> getStopSequences() {
		return stopSequences;
	}

	Map<String, Fare> getFares() {
		return fares;
	}

	Map<String, GTFSFeed> getGtfsFeeds() {
		return Collections.unmodifiableMap(gtfsFeeds);
	}

}
