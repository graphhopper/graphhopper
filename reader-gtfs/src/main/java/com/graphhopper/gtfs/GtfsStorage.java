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
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.index.LineIntIndex;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

public class GtfsStorage {

	private static final Logger LOGGER = LoggerFactory.getLogger(GtfsStorage.class);
	private LineIntIndex stopIndex;
	private PtGraph ptGraph;

	public void setStopIndex(LineIntIndex stopIndex) {
		this.stopIndex = stopIndex;
	}

	public LineIntIndex getStopIndex() {
		return stopIndex;
	}

    public PtGraph getPtGraph() {
        return ptGraph;
    }

    public void setPtGraph(PtGraph ptGraph) {
        this.ptGraph = ptGraph;
    }

	public Map<Integer, int[]> getSkippedEdgesForTransfer() {
		return skippedEdgesForTransfer;
	}

	public static class Validity implements Serializable {
		final BitSet validity;
		final ZoneId zoneId;
		final LocalDate start;

		public Validity(BitSet validity, ZoneId zoneId, LocalDate start) {
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
		public final String feedId;
		public final String stopId;

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

		@Override
		public String toString() {
			return "FeedIdWithStopId{" +
					"feedId='" + feedId + '\'' +
					", stopId='" + stopId + '\'' +
					'}';
		}
	}

	private boolean isClosed = false;
	private Directory dir;
	private Set<String> gtfsFeedIds;
	private Map<String, GTFSFeed> gtfsFeeds = new HashMap<>();
	private Map<String, Map<String, Fare>> faresByFeed;
	private Map<FeedIdWithStopId, Integer> stationNodes;
	private Map<Integer, int[]> skippedEdgesForTransfer;

	private Map<Integer, Integer> ptToStreet;
	private Map<Integer, Integer> streetToPt;

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
		this.stationNodes = data.getHashMap("stationNodes");
		this.ptToStreet = data.getHashMap("ptToStreet");
		this.streetToPt = data.getHashMap("streetToPt");
		this.skippedEdgesForTransfer = data.getHashMap("skippedEdgesForTransfer");
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

	public Map<String, Map<String, Fare>> getFares() {
		return faresByFeed;
	}

	public Map<Integer, Integer> getPtToStreet() {
		return ptToStreet;
	}

	public Map<Integer, Integer> getStreetToPt() {
		return streetToPt;
	}

	public Map<String, GTFSFeed> getGtfsFeeds() {
		return Collections.unmodifiableMap(gtfsFeeds);
	}

	public Map<FeedIdWithStopId, Integer> getStationNodes() {
		return stationNodes;
	}

	public abstract static class PlatformDescriptor implements Serializable {
		public String feed_id;
		public String stop_id;

		public static PlatformDescriptor route(String feed_id, String stop_id, String route_id) {
			RoutePlatform routePlatform = new RoutePlatform();
			routePlatform.feed_id = feed_id;
			routePlatform.stop_id = stop_id;
			routePlatform.route_id = route_id;
			return routePlatform;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			PlatformDescriptor that = (PlatformDescriptor) o;
			return Objects.equals(feed_id, that.feed_id) &&
					Objects.equals(stop_id, that.stop_id);
		}

		@Override
		public int hashCode() {
			return Objects.hash(feed_id, stop_id);
		}

		public static RouteTypePlatform routeType(String feed_id, String stop_id, int route_type) {
			RouteTypePlatform routeTypePlatform = new RouteTypePlatform();
			routeTypePlatform.feed_id = feed_id;
			routeTypePlatform.stop_id = stop_id;
			routeTypePlatform.route_type = route_type;
			return routeTypePlatform;
		}

	}

	public static class RoutePlatform extends PlatformDescriptor {
		String route_id;

		@Override
		public String toString() {
			return "RoutePlatform{" +
					"feed_id='" + feed_id + '\'' +
					", stop_id='" + stop_id + '\'' +
					", route_id='" + route_id + '\'' +
					'}';
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			if (!super.equals(o)) return false;
			RoutePlatform that = (RoutePlatform) o;
			return route_id.equals(that.route_id);
		}

		@Override
		public int hashCode() {
			return Objects.hash(super.hashCode(), route_id);
		}
	}

	public static class RouteTypePlatform extends PlatformDescriptor {
		int route_type;

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			if (!super.equals(o)) return false;
			RouteTypePlatform that = (RouteTypePlatform) o;
			return route_type == that.route_type;
		}

		@Override
		public int hashCode() {
			return Objects.hash(super.hashCode(), route_type);
		}

		@Override
		public String toString() {
			return "RouteTypePlatform{" +
					"feed_id='" + feed_id + '\'' +
					", stop_id='" + stop_id + '\'' +
					", route_type=" + route_type +
					'}';
		}
	}
}
