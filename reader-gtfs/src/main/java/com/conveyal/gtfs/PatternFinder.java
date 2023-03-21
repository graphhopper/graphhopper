package com.conveyal.gtfs;

import com.carrotsearch.hppc.IntArrayList;
import com.conveyal.gtfs.model.*;
import com.google.common.base.Joiner;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import org.locationtech.jts.geom.LineString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * This abstracts out the logic for finding stop sequences ("journey patterns" in Transmodel parlance) based on trips.
 * Placing this logic in a separate class allows us to use it on GTFS data from multiple sources.
 * Our two specific use cases are finding patterns in stop_times that have already been loaded into an RDBMS, and
 * finding patterns while loading Java objects directly into a MapDB database.
 *
 * Created by abyrd on 2017-10-08
 */
public class PatternFinder {

    private static final Logger LOG = LoggerFactory.getLogger(PatternFinder.class);

    // A multi-map that groups trips together by their sequence of stops
    private Multimap<TripPatternKey, Trip> tripsForPattern = LinkedHashMultimap.create();

    private int nTripsProcessed = 0;

    /**
     * Bin all trips by the sequence of stops they visit.
     * @return A map from a list of stop IDs to a list of Trip IDs that visit those stops in that sequence.
     */
//    public void findPatterns(Feed feed) {
//
//        for (Trip trip : trips) {
//        }
//        feed.patterns.stream().forEach(p -> {
//            feed.patterns.put(p.pattern_id, p);
//            p.associatedTrips.stream().forEach(t -> feed.tripPatternMap.put(t, p.pattern_id));
//        });
//
//    }

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
    public Map<TripPatternKey, Pattern> createPatternObjects(Map<String, Stop> stopById) {
        // Make pattern ID one-based to avoid any JS type confusion between an ID of zero vs. null value.
        int nextPatternId = 1;
        // Create an in-memory list of Patterns because we will later rename them before inserting them into storage.
        // Use a LinkedHashMap so we can retrieve the entrySets later in the order of insertion.
        Map<TripPatternKey, Pattern> patterns = new LinkedHashMap<>();
        // TODO assign patterns sequential small integer IDs (may include route)
        for (TripPatternKey key : tripsForPattern.keySet()) {
            Collection<Trip> trips = tripsForPattern.get(key);
            Pattern pattern = new Pattern(key.stops, trips, null);
            // Overwrite long UUID with sequential integer pattern ID
            pattern.pattern_id = Integer.toString(nextPatternId++);
            // FIXME: Should associated shapes be a single entry?
            pattern.associatedShapes = new HashSet<>();
            trips.stream().forEach(trip -> pattern.associatedShapes.add(trip.shape_id));
            if (pattern.associatedShapes.size() > 1) {
                // Store an error if there is more than one shape per pattern. Note: error storage is null if called via
                // MapDB implementation.
                // TODO: Should shape ID be added to trip pattern key?
            }
            patterns.put(key, pattern);
        }
        LOG.info("Total patterns: {}", tripsForPattern.keySet().size());
        return patterns;
    }

    /**
     * Holds information about all pattern names on a particular route,
     * modeled on https://github.com/opentripplanner/OpenTripPlanner/blob/master/src/main/java/org/opentripplanner/routing/edgetype/TripPattern.java#L379
     */
    private static class PatternNamingInfo {
        // These are all maps from ?
        // FIXME For type safety and clarity maybe we should have a parameterized ID type, i.e. EntityId<Stop> stopId.
        Multimap<String, Pattern> fromStops = HashMultimap.create();
        Multimap<String, Pattern> toStops = HashMultimap.create();
        Multimap<String, Pattern> vias = HashMultimap.create();
        List<Pattern> patternsOnRoute = new ArrayList<>();
    }

    /**
     * Used as a map key when grouping trips by stop pattern. Note that this includes the routeId, so the same sequence of
     * stops on two different routes makes two different patterns.
     * These objects are not intended for use outside the grouping process.
     */
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
        private static final Logger LOG = LoggerFactory.getLogger(Pattern.class);

        public String getId () {
            return pattern_id;
        }

        // A unique ID for this journey pattern / stop pattern
        public String pattern_id;

        // The segment of the pattern's geometry (which is always a LineString) on which each stop in the sequence falls.
        public int[] segmentIndex;

        // The percentage in [0..1] along the line segment at which each stop in the sequence falls.
        public double[] segmentFraction;

        public List<String> orderedStops;
        // TODO: change list of trips to set
        public List<String> associatedTrips;
        // TODO: add set of shapes
        public Set<String> associatedShapes;
        public LineString geometry;
        public String name;
        public static Joiner joiner = Joiner.on("-").skipNulls();
        public String feed_id;

        // TODO: Should a Pattern be generated for a single trip or a set of trips that share the same ordered stop list?

        /**
         *
         * @param orderedStops
         * @param trips the first trip will serve as an exemplar for all the others.
         * @param patternGeometry
         */
        public Pattern (List<String> orderedStops, Collection<Trip> trips, LineString patternGeometry){

            // Temporarily make a random ID for the pattern, which might be overwritten in a later step ?
            this.pattern_id = UUID.randomUUID().toString();

            // Assign ordered list of stop IDs to be the key of this pattern.
            // FIXME what about pickup / dropoff type?
            this.orderedStops = orderedStops;

            // Save the string IDs of the trips on this pattern.
            this.associatedTrips = trips.stream().map(t -> t.trip_id).collect(Collectors.toList());

            // In theory all trips could take different paths and be on different routes.
            // Here we're using only the first one as an exemplar.
            String trip_id = associatedTrips.get(0);

            Trip exemplarTrip = trips.iterator().next();
            this.geometry = patternGeometry;

            // feed.getTripGeometry(exemplarTrip.trip_id);


            // A name is assigned to this pattern based on the headsign, short name, direction ID or stop IDs.
            // This is not at all guaranteed to be unique, it's just to help identify the pattern.
            if (exemplarTrip.trip_headsign != null){
                name = exemplarTrip.trip_headsign;
            }
            else if (exemplarTrip.trip_short_name != null) {
                name = exemplarTrip.trip_short_name;
            }
            else if (exemplarTrip.direction_id >= 0){
                name = String.valueOf(exemplarTrip.direction_id);
            }
            else{
                name = joiner.join(orderedStops);
            }

            // TODO: Implement segmentIndex using JTS to segment out LineString by stops.

            // TODO: Implement segmentFraction using JTS to segment out LineString by stops.

        }

    }
}