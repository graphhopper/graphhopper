/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.graphhopper.reader.gtfs;

import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.Fare;
import com.conveyal.gtfs.model.FareRule;
import com.google.transit.realtime.GtfsRealtime;
import com.graphhopper.gtfs.fare.FixedFareAttributeLoader;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphExtension;
import org.mapdb.Bind;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.zip.ZipFile;

public class GtfsStorage implements GraphExtension, GtfsStorageI {

	public static class Validity implements Serializable {
		final BitSet validity;
		final ZoneId zoneId;
		final LocalDate start;

		Validity(BitSet validity, ZoneId zoneId, LocalDate start) {
			this.validity = validity;
			this.zoneId = zoneId;
			this.start = start;
		}

		@Override
		public boolean equals(Object other) {
			if (! (other instanceof Validity)) return false;
			Validity v = (Validity) other;
			return validity.equals(v.validity) && zoneId.equals(v.zoneId) && start.equals(v.start);
		}

		@Override
		public int hashCode() {
			return Objects.hash(validity, zoneId, start);
		}
	}

	static class FeedIdWithTimezone implements Serializable {
		final String feedId;
		final ZoneId zoneId;

		FeedIdWithTimezone(String feedId, ZoneId zoneId) {
			this.feedId = feedId;
			this.zoneId = zoneId;
		}

		@Override
		public boolean equals(Object other) {
			if (! (other instanceof FeedIdWithTimezone)) return false;
			FeedIdWithTimezone v = (FeedIdWithTimezone) other;
			return feedId.equals(v.feedId) && zoneId.equals(v.zoneId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(feedId, zoneId);
		}

	}

	private boolean isClosed = false;
	private Directory dir;
	private Set<String> gtfsFeedIds;
	private Map<String, GTFSFeed> gtfsFeeds = new HashMap<>();
	private Map<String, Transfers> transfers = new HashMap<>();
	private HTreeMap<Validity, Integer> operatingDayPatterns;
	private Bind.MapWithModificationListener<FeedIdWithTimezone, Integer> timeZones;
	private Map<Integer, FeedIdWithTimezone> readableTimeZones;
	private Map<Integer, byte[]> tripDescriptors;
	private Map<Integer, Integer> stopSequences;

	private Map<Integer, PlatformDescriptor> routes;

	private Map<String, Fare> fares;
	private Map<String, int[]> boardEdgesForTrip;
	private Map<String, int[]> leaveEdgesForTrip;

	private Map<String, Integer> stationNodes;

	public enum EdgeType {
		HIGHWAY, ENTER_TIME_EXPANDED_NETWORK, LEAVE_TIME_EXPANDED_NETWORK, ENTER_PT, EXIT_PT, HOP, DWELL, BOARD, ALIGHT, OVERNIGHT, TRANSFER, WAIT, WAIT_ARRIVAL
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
		this.data = DBMaker.newFileDB(new File(dir.getLocation() + "/transit_schedule")).transactionDisable().mmapFileEnable().readOnly().make();
		init();
		for (String gtfsFeedId : this.gtfsFeedIds) {
			GTFSFeed feed = new GTFSFeed(new File(dir.getLocation() + "/" + gtfsFeedId));
			this.gtfsFeeds.put(gtfsFeedId, feed);
			this.transfers.put(gtfsFeedId, new Transfers(feed));
		}
		return true;
	}

	@Override
	public GraphExtension create(long byteCount) {
		final File file = new File(dir.getLocation() + "/transit_schedule");
		try {
			Files.deleteIfExists(file.toPath());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		this.data = DBMaker.newFileDB(file).transactionDisable().mmapFileEnable().asyncWriteEnable().make();
		init();
		return this;
	}

    private void init() {
		this.gtfsFeedIds = data.getHashSet("gtfsFeeds");
		this.operatingDayPatterns = data.getHashMap("validities");
		this.timeZones = data.getHashMap("timeZones");
		Map<Integer, FeedIdWithTimezone> readableTimeZones = new HashMap<>();
		for (Map.Entry<FeedIdWithTimezone, Integer> entry : this.timeZones.entrySet()) {
			readableTimeZones.put(entry.getValue(), entry.getKey());
		}
		Bind.mapInverse(this.timeZones, readableTimeZones);
		this.readableTimeZones = Collections.unmodifiableMap(readableTimeZones);
		this.tripDescriptors = data.getTreeMap("tripDescriptors");
		this.stopSequences = data.getTreeMap("stopSequences");
		this.fares = data.getTreeMap("fares");
		this.boardEdgesForTrip = data.getHashMap("boardEdgesForTrip");
		this.leaveEdgesForTrip = data.getHashMap("leaveEdgesForTrip");
		this.stationNodes = data.getHashMap("stationNodes");
		this.routes = data.getHashMap("routes");
	}

	void loadGtfsFromFile(String id, ZipFile zip) {
		File file = new File(dir.getLocation() + "/" + id);
		try {
			Files.deleteIfExists(file.toPath());
			GTFSFeed feed = new GTFSFeed(file);
			feed.loadFromFileAndLogErrors(zip);
			fixFares(feed, zip);
			this.gtfsFeeds.put(id, feed);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		this.gtfsFeedIds.add(id);
	}

	private void fixFares(GTFSFeed feed, ZipFile zip) {
		feed.fares.clear();
		Map<String, Fare> fares = new HashMap<>();
		try {
			new FixedFareAttributeLoader(feed, fares).loadTable(zip);
			new FareRule.Loader(feed, fares).loadTable(zip);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		feed.fares.putAll(fares);
	}

	@Override
	public void flush() {
	}

	@Override
	public void close() {
		if (!isClosed) {
			isClosed = true;
			data.close();
			for (GTFSFeed feed : gtfsFeeds.values()) {
				feed.close();
			}
		}
	}

	@Override
	public boolean isClosed() {
		return isClosed;
	}

	@Override
	public long getCapacity() {
		return 0;
	}

    @Override
	public Map<Validity, Integer> getOperatingDayPatterns() {
        return operatingDayPatterns;
    }

	Map<Integer, FeedIdWithTimezone> getTimeZones() {
		return readableTimeZones;
	}

	@Override
	public Map<FeedIdWithTimezone, Integer> getWritableTimeZones() {
		return timeZones;
	}

	@Override
	public Map<Integer, byte[]> getTripDescriptors() {
		return tripDescriptors;
	}

	@Override
	public Map<Integer, Integer> getStopSequences() {
		return stopSequences;
	}

	@Override
	public Map<String, int[]> getBoardEdgesForTrip() {
		return boardEdgesForTrip;
	}

	@Override
	public Map<String, int[]> getAlightEdgesForTrip() {
		return leaveEdgesForTrip;
	}

    @Override
    public Map<Integer, PlatformDescriptor> getRoutes() {
        return routes;
    }

    @Override
	public Map<String, Fare> getFares() {
		return fares;
	}

	public Map<String, GTFSFeed> getGtfsFeeds() {
		return Collections.unmodifiableMap(gtfsFeeds);
	}

	@Override
	public Map<String, Transfers> getTransfers() {
		return transfers;
	}

	@Override
	public Map<String, Integer> getStationNodes() {
		return stationNodes;
	}

	static String tripKey(GtfsRealtime.TripDescriptor tripDescriptor, boolean isFrequencyBased) {
		if (isFrequencyBased) {
			return tripDescriptor.getTripId()+tripDescriptor.getStartTime();
		} else {
			return tripDescriptor.getTripId();
		}
	}

}
