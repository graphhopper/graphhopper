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

package com.graphhopper.gtfs;

import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.Fare;
import com.google.transit.realtime.GtfsRealtime;
import com.graphhopper.storage.Directory;
import org.mapdb.Bind;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.zip.ZipFile;

public class GtfsStorage implements GtfsStorageI {

	private static final Logger LOGGER = LoggerFactory.getLogger(GtfsStorage.class);

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

	public static class FeedIdWithStopId implements Serializable {
		final String feedId;
		final String stopId;

		public FeedIdWithStopId(String feedId, String stopId) {
			this.feedId = feedId;
			this.stopId = stopId;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			FeedIdWithStopId that = (FeedIdWithStopId) o;
			return feedId.equals(that.feedId) &&
					stopId.equals(that.stopId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(feedId, stopId);
		}
	}

	private boolean isClosed = false;
	private Directory dir;
	private Set<String> gtfsFeedIds;
	private Map<String, GTFSFeed> gtfsFeeds = new HashMap<>();
	private HTreeMap<Validity, Integer> operatingDayPatterns;
	private Bind.MapWithModificationListener<FeedIdWithTimezone, Integer> timeZones;
	private Map<Integer, FeedIdWithTimezone> readableTimeZones;
	private Map<Integer, byte[]> tripDescriptors;
	private Map<Integer, Integer> stopSequences;

	private Map<Integer, PlatformDescriptor> platformDescriptorsByEdge;

	private Map<String, Map<String, Fare>> faresByFeed;
	private Map<String, int[]> boardEdgesForTrip;
	private Map<String, int[]> leaveEdgesForTrip;

	private Map<FeedIdWithStopId, Integer> stationNodes;

	public enum EdgeType {
		HIGHWAY, ENTER_TIME_EXPANDED_NETWORK, LEAVE_TIME_EXPANDED_NETWORK, ENTER_PT, EXIT_PT, HOP, DWELL, BOARD, ALIGHT, OVERNIGHT, TRANSFER, WAIT, WAIT_ARRIVAL
    }

	private DB data;

	GtfsStorage(Directory dir) {
		this.dir = dir;
	}

	boolean loadExisting() {
		File file = new File(dir.getLocation() + "/transit_schedule");
		if (!file.exists()) {
			return false;
		}
		this.data = DBMaker.newFileDB(file).transactionDisable().mmapFileEnable().readOnly().make();
		init();
		for (String gtfsFeedId : this.gtfsFeedIds) {
			File dbFile = new File(dir.getLocation() + "/" + gtfsFeedId);

			if (!dbFile.exists()) {
				throw new RuntimeException(String.format("The mapping of the gtfsFeeds in the transit_schedule DB does not reflect the files in %s. "
								+ "dbFile %s is missing.",
						dir.getLocation(), dbFile.getName()));
			}

			GTFSFeed feed = new GTFSFeed(dbFile);
			this.gtfsFeeds.put(gtfsFeedId, feed);
		}
		postInit();
		return true;
	}

	void create() {
		this.dir.create();
		final File file = new File(dir.getLocation() + "/transit_schedule");
		try {
			Files.deleteIfExists(file.toPath());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		this.data = DBMaker.newFileDB(file).transactionDisable().mmapFileEnable().asyncWriteEnable().make();
		init();
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
		this.boardEdgesForTrip = data.getHashMap("boardEdgesForTrip");
		this.leaveEdgesForTrip = data.getHashMap("leaveEdgesForTrip");
		this.stationNodes = data.getHashMap("stationNodes");
		this.platformDescriptorsByEdge = data.getHashMap("routes");
	}

	void loadGtfsFromZipFileOrDirectory(String id, File zipFileOrDirectory) {
		File dbFile = new File(dir.getLocation() + "/" + id);
		try {
			Files.deleteIfExists(dbFile.toPath());
			GTFSFeed feed = new GTFSFeed(dbFile);
			feed.loadFromFileAndLogErrors(zipFileOrDirectory);
			this.gtfsFeeds.put(id, feed);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		this.gtfsFeedIds.add(id);
	}

	// TODO: Refactor initialization
	public void postInit() {
		LocalDate latestStartDate = LocalDate.ofEpochDay(this.gtfsFeeds.values().stream().mapToLong(f -> f.getStartDate().toEpochDay()).max().getAsLong());
		LocalDate earliestEndDate = LocalDate.ofEpochDay(this.gtfsFeeds.values().stream().mapToLong(f -> f.getEndDate().toEpochDay()).min().getAsLong());
		LOGGER.info("Calendar range covered by all feeds: {} till {}", latestStartDate, earliestEndDate);
		faresByFeed = new HashMap<>();
		this.gtfsFeeds.forEach((feed_id, feed) -> faresByFeed.put(feed_id, feed.fares));
	}

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
	public Map<Validity, Integer> getOperatingDayPatterns() {
        return operatingDayPatterns;
    }

    @Override
	public Map<Integer, FeedIdWithTimezone> getTimeZones() {
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
    public Map<Integer, PlatformDescriptor> getPlatformDescriptorByEdge() {
        return platformDescriptorsByEdge;
    }

    @Override
	public Map<String, Map<String, Fare>> getFares() {
		return faresByFeed;
	}

	public Map<String, GTFSFeed> getGtfsFeeds() {
		return Collections.unmodifiableMap(gtfsFeeds);
	}

	@Override
	public Map<FeedIdWithStopId, Integer> getStationNodes() {
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
