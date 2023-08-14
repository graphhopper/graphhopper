package com.conveyal.gtfs;

import com.carrotsearch.hppc.IntArrayList;
import com.conveyal.gtfs.model.*;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.transit.realtime.GtfsRealtime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.conveyal.gtfs.model.Entity.Writer.convertToGtfsTime;

/**
 * This abstracts out the logic for finding stop sequences ("journey patterns" in Transmodel parlance) based on trips.
 * Placing this logic in a separate class allows us to use it on GTFS data from multiple sources.
 * Our two specific use cases are finding patterns in stop_times that have already been loaded into an RDBMS, and
 * finding patterns while loading Java objects directly into a MapDB database.
 *
 * Created by abyrd on 2017-10-08
 */
public class PatternFinder {

    private GTFSFeed gtfsFeed;

    public PatternFinder(GTFSFeed gtfsFeed) {
        this.gtfsFeed = gtfsFeed;
    }

    private static final Logger LOG = LoggerFactory.getLogger(PatternFinder.class);

    // A multi-map that groups trips together by their sequence of stops
    private Multimap<TripPatternKey, Trip> tripsForPattern = LinkedHashMultimap.create();

    private int nTripsProcessed = 0;

    public void processTrip(Trip trip, Iterable<StopTime> orderedStopTimes) {
        if (++nTripsProcessed % 100000 == 0) {
            LOG.info("trip {}", nTripsProcessed);
        }
        TripPatternKey key = new TripPatternKey();
        for (StopTime st : orderedStopTimes) {
            key.addStopTime(st);
        }
        // Add the current trip to the map, possibly extending an existing list of trips on this pattern.
        tripsForPattern.put(key, trip);
    }

    /**
     * Once all trips have been processed, call this method to produce the final Pattern objects representing all the
     * unique sequences of stops encountered. Returns map of patterns to their keys so that downstream functions can
     * make use of trip pattern keys for constructing pattern stops or other derivative objects.
     */
    public Map<TripPatternKey, Pattern> createPatternObjects() {
        int nextPatternId = 1;
        Map<TripPatternKey, Pattern> patterns = new LinkedHashMap<>();
        for (TripPatternKey key : tripsForPattern.keySet()) {
            List<GtfsRealtime.TripDescriptor> trips = tripsForPattern.get(key).stream()
                    .flatMap(t -> {
                        GtfsRealtime.TripDescriptor.Builder tdBuilder = GtfsRealtime.TripDescriptor.newBuilder().setTripId(t.trip_id).setRouteId(t.route_id);
                        Collection<Frequency> frequencies = gtfsFeed.getFrequencies(t.trip_id);
                        if (frequencies.isEmpty()) {
                            return Stream.of(tdBuilder.build());
                        } else {
                            Stream.Builder<GtfsRealtime.TripDescriptor> builder = Stream.builder();
                            for (Frequency frequency : frequencies) {
                                for (int time = frequency.start_time; time < frequency.end_time; time += frequency.headway_secs) {
                                    builder.add(tdBuilder.setStartTime(convertToGtfsTime(time)).build());
                                }
                            }
                            return builder.build();
                        }
                    })
                    .collect(Collectors.toList());


            Pattern pattern = new Pattern(key.stops, trips);
            pattern.pattern_id = gtfsFeed.feedId + " " + nextPatternId++;
            patterns.put(key, pattern);
        }
        LOG.info("Total patterns: {}", tripsForPattern.keySet().size());
        return patterns;
    }

    public static class TripPatternKey {

        public List<String> stops = new ArrayList<>();
        public IntArrayList pickupTypes = new IntArrayList();
        public IntArrayList dropoffTypes = new IntArrayList();

        public TripPatternKey () {
        }

        public void addStopTime (StopTime st) {
            stops.add(st.stop_id);
            pickupTypes.add(st.pickup_type);
            dropoffTypes.add(st.drop_off_type);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            TripPatternKey that = (TripPatternKey) o;

            if (!Objects.equals(dropoffTypes, that.dropoffTypes)) return false;
            if (!Objects.equals(pickupTypes, that.pickupTypes)) return false;
            if (!Objects.equals(stops, that.stops)) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = stops != null ? stops.hashCode() : 0;
            result = 31 * result + (pickupTypes != null ? pickupTypes.hashCode() : 0);
            result = 31 * result + (dropoffTypes != null ? dropoffTypes.hashCode() : 0);
            return result;
        }

    }

    /**
     * Represents a collection of trips that all visit the same stops in the same sequence.
     */
    public static class Pattern extends Entity {
        public static final long serialVersionUID = 1L;

        public String getId () {
            return pattern_id;
        }

        public String pattern_id;

        public List<String> orderedStops;
        public List<GtfsRealtime.TripDescriptor> trips;
        public String name;
        public String feed_id;

        public Pattern (List<String> orderedStops, List<GtfsRealtime.TripDescriptor> trips) {

            // Assign ordered list of stop IDs to be the key of this pattern.
            // FIXME what about pickup / dropoff type?
            this.orderedStops = orderedStops;

            this.trips = trips;

        }

    }
}