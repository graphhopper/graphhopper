package com.graphhopper.reader.gtfs;

import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.*;
import com.graphhopper.reader.DataReader;
import com.graphhopper.reader.dem.ElevationProvider;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Helper;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;

class GtfsReader implements DataReader {

	private static final Logger LOGGER = LoggerFactory.getLogger(GtfsReader.class);

	private final GraphHopperStorage ghStorage;
	private final GtfsStorage gtfsStorage;
	private File file;

	private final DistanceCalc distCalc = Helper.DIST_EARTH;

	GtfsReader(GraphHopperStorage ghStorage) {
		this.ghStorage = ghStorage;
		this.ghStorage.create(1000);
		this.gtfsStorage = (GtfsStorage) ghStorage.getExtension();
	}

	@Override
	public DataReader setFile(File file) {
		this.file = file;
		return this;
	}

	@Override
	public DataReader setElevationProvider(ElevationProvider ep) {
		return this;
	}

	@Override
	public DataReader setWorkerThreads(int workerThreads) {
		return this;
	}

	@Override
	public DataReader setEncodingManager(EncodingManager em) {
		return this;
	}

	@Override
	public DataReader setWayPointMaxDistance(double wayPointMaxDistance) {
		return this;
	}

	@Override
	public void readGraph() throws IOException {
		GTFSFeed feed = GTFSFeed.fromFile(file.getPath());
		NodeAccess nodeAccess = ghStorage.getNodeAccess();
		TreeMap<Integer, AbstractPtEdge> edges = new TreeMap<>();
		int i=0;
		int j=0;
		Map<String,Integer> stops = new HashMap<>();
 		for (Stop stop : feed.stops.values()) {
 			stops.put(stop.stop_id, i);
			LOGGER.info("Node "+i+": "+stop.stop_id);
			nodeAccess.setNode(i++, stop.stop_lat, stop.stop_lon);
			ghStorage.edge(i, i);
			edges.put(j, new StopLoopEdge());
			j++;
		}
		LOGGER.info("Created " + i + " nodes from GTFS stops.");
		feed.findPatterns();
		for (Pattern pattern : feed.patterns.values()) {
			try {
				for (String tripId : pattern.associatedTrips) {
					Collection<Frequency> frequencies = feed.getFrequencies(tripId);
					if (frequencies.isEmpty()) {
						j = insert(feed, j, stops, pattern, tripId, 0, edges);
					} else {
						for (Frequency frequency : frequencies) {
							for (int time = frequency.start_time; time < frequency.end_time; time += frequency.headway_secs) {
								j = insert(feed, j, stops, pattern, tripId, time - frequency.start_time, edges);
							}
						}
					}
				}
			} catch (GTFSFeed.FirstAndLastStopsDoNotHaveTimes e) {
				throw new RuntimeException(e);
			}
		}
		for (Transfer transfer : feed.transfers.values()) {
			if (transfer.transfer_type == 2 && !transfer.from_stop_id.equals(transfer.to_stop_id)) {
				Stop fromStop = feed.stops.get(transfer.from_stop_id);
				Stop toStop = feed.stops.get(transfer.to_stop_id);
				double distance = distCalc.calcDist(
						fromStop.stop_lat,
						fromStop.stop_lon,
						toStop.stop_lat,
						toStop.stop_lon);
				EdgeIteratorState edge = ghStorage.edge(
						stops.get(transfer.from_stop_id),
						stops.get(transfer.to_stop_id),
						distance,
						false);
				edge.setName("Transfer: "+fromStop.stop_name + " -> " + toStop.stop_name);
				edges.put(edge.getEdge(), new GtfsTransferEdge(transfer));
				j++;
			}
		}
		gtfsStorage.setEdges(edges);
		gtfsStorage.setRealEdgesSize(j);
		LOGGER.info("Created " + j + " edges from GTFS trip hops and transfers.");
	}

	private int insert(GTFSFeed feed, int j, Map<String, Integer> stops, Pattern pattern, String tripId, int time, Map<Integer, AbstractPtEdge> edges) throws GTFSFeed.FirstAndLastStopsDoNotHaveTimes {
		LOGGER.info(tripId);
		Iterable<StopTime> stopTimes = feed.getInterpolatedStopTimesForTrip(tripId);
		StopTime prev = null;
		for (StopTime orderedStop : stopTimes) {
			if (prev != null) {
				double distance = distCalc.calcDist(
						feed.stops.get(prev.stop_id).stop_lat,
						feed.stops.get(prev.stop_id).stop_lon,
						feed.stops.get(orderedStop.stop_id).stop_lat,
						feed.stops.get(orderedStop.stop_id).stop_lon);
				EdgeIteratorState edge = ghStorage.edge(
						stops.get(prev.stop_id),
						stops.get(orderedStop.stop_id),
						distance,
						false);
				edge.setName(prev.stop_id + "-" + orderedStop.stop_id + "(" + feed.routes.get(feed.trips.get(tripId).route_id).route_long_name + ")(" +feed.trips.get(tripId).trip_headsign+")");

				double travelTime = (orderedStop.arrival_time - prev.departure_time);
//				LOGGER.info("Distance: "+distance+" -- travel time: "+travelTime);
				if (time == 0) {
					edges.put(edge.getEdge(), new TripHopEdge(prev, orderedStop));
				} else {
					StopTime from = prev.clone();
					from.departure_time += time;
					from.arrival_time += time;
					StopTime to = orderedStop.clone();
					to.departure_time += time;
					to.arrival_time += time;
//					LOGGER.info(from.stop_id + "-" + to.stop_id + " "+"Arr: "+from.arrival_time+" -- Dep: "+from.departure_time);
					edges.put(edge.getEdge(), new TripHopEdge(from, to));
				}
				j++;
			}
			prev = orderedStop;
		}
		return j;
	}

	@Override
	public Date getDataDate() {
		return null;
	}
}
