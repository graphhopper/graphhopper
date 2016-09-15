package com.graphhopper.reader.gtfs;

import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.Pattern;
import com.conveyal.gtfs.model.Stop;
import com.conveyal.gtfs.model.StopTime;
import com.graphhopper.reader.DataReader;
import com.graphhopper.reader.dem.ElevationProvider;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Helper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

class GtfsReader implements DataReader {

	private static final Logger LOGGER = LoggerFactory.getLogger(GtfsReader.class);

	private final GraphHopperStorage ghStorage;
	private final GtfsStorage gtfsStorage;
	private File file;

	private final DistanceCalc distCalc = Helper.DIST_EARTH;
	private EncodingManager em;

	GtfsReader(GraphHopperStorage ghStorage, GtfsStorage gtfsStorage) {
		this.ghStorage = ghStorage;
		this.ghStorage.create(1000);
		this.gtfsStorage = gtfsStorage;
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
		this.em = em;
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
		int i=0;
		int j=0;
		Map<String,Integer> stops = new HashMap<>();
 		for (Stop stop : feed.stops.values()) {
 			stops.put(stop.stop_id, i);
			LOGGER.info("Node "+i+": "+stop.stop_id);
			nodeAccess.setNode(i++, stop.stop_lat, stop.stop_lon);
			ghStorage.edge(i, i);
			gtfsStorage.getEdges().put(j, new StopLoopEdge());
			j++;
		}
		LOGGER.info("Created " + i + " nodes from GTFS stops.");
		feed.findPatterns();
		gtfsStorage.setFeed(feed);
		for (Pattern pattern : feed.patterns.values()) {
			try {
				Iterable<StopTime> stopTimes = feed.getInterpolatedStopTimesForTrip(pattern.associatedTrips.get(0));
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
						edge.setName(prev.stop_id + "-" + orderedStop.stop_id + "(" + pattern.name + ")");

						double travelTime = (orderedStop.arrival_time - prev.departure_time);
						LOGGER.info("Distance: "+distance+" -- travel time: "+travelTime);

						long flags = 0;
						double speed = 3.6 * distance / travelTime;
						flags = em.getEncoder("pt").setSpeed(flags, speed);
						edge.setFlags(flags);
						LOGGER.info("Speed: "+speed+" Stored Speed: "+em.getEncoder("pt").getSpeed(flags));
						gtfsStorage.getEdges().put(edge.getEdge(), new PatternHopEdge(prev, orderedStop));
						j++;
					}
					prev = orderedStop;
				}

			} catch (GTFSFeed.FirstAndLastStopsDoNotHaveTimes e) {
				throw new RuntimeException(e);
			}
		}
		gtfsStorage.setRealEdgesSize(j);
		LOGGER.info("Created " + j + " edges from GTFS pattern hops.");
	}

	@Override
	public Date getDataDate() {
		return null;
	}
}
