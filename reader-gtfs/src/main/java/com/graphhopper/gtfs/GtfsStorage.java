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

import com.carrotsearch.hppc.IntIntHashMap;
import com.carrotsearch.hppc.IntObjectHashMap;
import com.carrotsearch.hppc.cursors.IntIntCursor;
import com.carrotsearch.hppc.cursors.IntObjectCursor;
import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.Fare;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SequenceWriter;
import com.google.common.collect.HashMultimap;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.index.LineIntIndex;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

public class GtfsStorage {

	private static final Logger LOGGER = LoggerFactory.getLogger(GtfsStorage.class);

	static ObjectMapper ionMapper = new ObjectMapper();

	private LineIntIndex stopIndex;
	private PtGraph ptGraph;
	public Trips tripTransfers;

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

	public IntObjectHashMap<int[]> getSkippedEdgesForTransfer() {
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

	public static class FeedIdWithTimezone implements Serializable {
		public final String feedId;
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

		@JsonProperty("feed_id")
		public final String feedId;

		@JsonProperty("stop_id")
		public final String stopId;

		public FeedIdWithStopId(@JsonProperty("feed_id") String feedId,
								@JsonProperty("stop_id") String stopId) {
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
	private IntObjectHashMap<int[]> skippedEdgesForTransfer;

	private IntIntHashMap ptToStreet;
	private IntIntHashMap streetToPt;

	public enum EdgeType {
		HIGHWAY, ENTER_TIME_EXPANDED_NETWORK, LEAVE_TIME_EXPANDED_NETWORK, ENTER_PT, EXIT_PT, HOP, DWELL, BOARD, ALIGHT, OVERNIGHT, TRANSFER, WAIT, WAIT_ARRIVAL
    }

	public DB data;

	public GtfsStorage(Directory dir) {
		this.dir = dir;
	}

	boolean loadExisting() {
		File file = new File(dir.getLocation() + "/transit_schedule");
		if (!file.exists()) {
			return false;
		}
		this.data = DBMaker.newFileDB(file).transactionDisable().mmapFileEnable().readOnly().make();
		init();
        for (int i = 0; i < gtfsFeedIds.size(); i++) {
            String gtfsFeedId = "gtfs_" + i;
            File dbFile = new File(dir.getLocation() + "/" + gtfsFeedId);

            if (!dbFile.exists()) {
                throw new RuntimeException(String.format("The mapping of the gtfsFeeds in the transit_schedule DB does not reflect the files in %s. "
                                + "dbFile %s is missing.",
                        dir.getLocation(), dbFile.getName()));
            }

            GTFSFeed feed = new GTFSFeed(dbFile);
            this.gtfsFeeds.put(gtfsFeedId, feed);
        }
		ptToStreet = deserializeIntoIntIntHashMap("pt_to_street");
		streetToPt = deserializeIntoIntIntHashMap("street_to_pt");
		skippedEdgesForTransfer = deserializeIntoIntObjectHashMap("skipped_edges_for_transfer");
		try (InputStream is = Files.newInputStream(Paths.get(dir.getLocation() + "interpolated_transfers"))) {
			MappingIterator<JsonNode> objectMappingIterator = ionMapper.reader(JsonNode.class).readValues(is);
			objectMappingIterator.forEachRemaining(e -> {
				try {
					FeedIdWithStopId key = ionMapper.treeToValue(e.get(0), FeedIdWithStopId.class);
					for (JsonNode jsonNode : e.get(1)) {
						InterpolatedTransfer interpolatedTransfer = ionMapper.treeToValue(jsonNode, InterpolatedTransfer.class);
						interpolatedTransfers.put(key, interpolatedTransfer);
					}
				} catch (JsonProcessingException ex) {
					throw new RuntimeException(ex);
				}
			});
		} catch (IOException e) {
            throw new RuntimeException(e);
        }
        postInit();
		return true;
	}

	private IntIntHashMap deserializeIntoIntIntHashMap(String filename) {
		try (FileInputStream in = new FileInputStream(dir.getLocation() + filename)) {
			ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(in));
			int size = ois.readInt();
			IntIntHashMap result = new IntIntHashMap();
			for (int i = 0; i < size; i++) {
				result.put(ois.readInt(), ois.readInt());
			}
			return result;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public IntObjectHashMap<int[]> deserializeIntoIntObjectHashMap(String filename) {
		try (FileInputStream in = new FileInputStream(dir.getLocation() + filename)) {
			ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(in));
			int size = ois.readInt();
			IntObjectHashMap<int[]> result = new IntObjectHashMap<>(size);
			for (int i = 0; i < size; i++) {
				int key = ois.readInt();
				int n = ois.readInt();
				int[] ints = new int[n];
				for (int j = 0; j < n; j++) {
					ints[j] = ois.readInt();
				}
				result.put(key, ints);
			}
			return result;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
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
		this.ptToStreet = new IntIntHashMap();
		this.streetToPt = new IntIntHashMap();
		this.skippedEdgesForTransfer = new IntObjectHashMap<>();
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
		tripTransfers = new Trips(this);
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

	public IntIntHashMap getPtToStreet() {
		return ptToStreet;
	}

	public IntIntHashMap getStreetToPt() {
		return streetToPt;
	}

	public Map<String, GTFSFeed> getGtfsFeeds() {
		return Collections.unmodifiableMap(gtfsFeeds);
	}

	public Map<FeedIdWithStopId, Integer> getStationNodes() {
		return stationNodes;
	}

	public void flush() {
		serialize("pt_to_street", ptToStreet);
		serialize("street_to_pt", streetToPt);
		serialize("skipped_edges_for_transfer", skippedEdgesForTransfer);
		try (OutputStream os = Files.newOutputStream(Paths.get(dir.getLocation() + "interpolated_transfers"))) {
			SequenceWriter sequenceWriter = ionMapper.writer().writeValuesAsArray(os);
			for (Map.Entry<FeedIdWithStopId, Collection<InterpolatedTransfer>> e : interpolatedTransfers.asMap().entrySet()) {
				sequenceWriter.write(ionMapper.createArrayNode().addPOJO(e.getKey()).addPOJO(e.getValue()));
			}
			sequenceWriter.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void serializeTripTransfersMap(String filename, Map<Trips.TripAtStopTime, Collection<Trips.TripAtStopTime>> data) {
		try (ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(Files.newOutputStream(Paths.get(dir.getLocation() + filename))))) {
			oos.writeInt(data.size());
			for (Map.Entry<Trips.TripAtStopTime, Collection<Trips.TripAtStopTime>> entry : data.entrySet()) {
				oos.writeInt(entry.getKey().tripIdx);
				oos.writeInt(entry.getKey().stop_sequence);
				oos.writeInt(entry.getValue().size());
				for (Trips.TripAtStopTime tripAtStopTime : entry.getValue()) {
					oos.writeInt(tripAtStopTime.tripIdx);
					oos.writeInt(tripAtStopTime.stop_sequence);
				}
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public Map<Trips.TripAtStopTime, Collection<Trips.TripAtStopTime>> deserializeTripTransfersMap(String filename) {
		try (FileInputStream in = new FileInputStream(dir.getLocation() + filename)) {
			ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(in));
			int size = ois.readInt();
			Map<Trips.TripAtStopTime, Collection<Trips.TripAtStopTime>> result = new TreeMap<>();
			for (int i = 0; i < size; i++) {
				Trips.TripAtStopTime origin = new Trips.TripAtStopTime(ois.readInt(), ois.readInt());
				int nDestinations = ois.readInt();
				List<Trips.TripAtStopTime> destinations = new ArrayList<>(nDestinations);
				for (int j = 0; j < nDestinations; j++) {
					int tripIdxTo = ois.readInt();
					int stop_sequenceTo = ois.readInt();
					destinations.add(new Trips.TripAtStopTime(tripIdxTo, stop_sequenceTo));
				}
				result.put(origin, destinations);
			}
			return result;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void serialize(String filename, IntObjectHashMap<int[]> data) {
		try (ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(Files.newOutputStream(Paths.get(dir.getLocation() + filename))))) {
			oos.writeInt(data.size());
			for (IntObjectCursor<int[]> e : data) {
				oos.writeInt(e.key);
				oos.writeInt(e.value.length);
				for (int v : e.value) {
					oos.writeInt(v);
				}
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void serialize(String filename, IntIntHashMap data) {
		try (ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(Files.newOutputStream(Paths.get(dir.getLocation() + filename))))) {
			oos.writeInt(data.size());
			for (IntIntCursor e : data) {
				oos.writeInt(e.key);
				oos.writeInt(e.value);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
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

	public HashMultimap<FeedIdWithStopId, InterpolatedTransfer> interpolatedTransfers = HashMultimap.create();


	public static class InterpolatedTransfer {

		@JsonProperty("to_stop")
		public final FeedIdWithStopId toPlatformDescriptor;

		@JsonProperty("street_time")
		public final int streetTime;

		@JsonProperty("skipped_edges")
		public final int[] skippedEdgesForTransfer;

		public InterpolatedTransfer(@JsonProperty("to_stop") FeedIdWithStopId toPlatformDescriptor,
									@JsonProperty("street_time") int streetTime,
									@JsonProperty("skipped_edges") int[] skippedEdgesForTransfer) {
			this.toPlatformDescriptor = toPlatformDescriptor;
			this.streetTime = streetTime;
			this.skippedEdgesForTransfer = skippedEdgesForTransfer;
		}
	}

}
