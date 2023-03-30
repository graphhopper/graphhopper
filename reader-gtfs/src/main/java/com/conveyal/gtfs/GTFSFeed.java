/*
 * Copyright (c) 2015, Conveyal
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.conveyal.gtfs;

import com.conveyal.gtfs.error.GTFSError;
import com.conveyal.gtfs.error.GeneralError;
import com.conveyal.gtfs.model.Calendar;
import com.conveyal.gtfs.model.*;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Iterables;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateList;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.mapdb.BTreeMap;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Fun;
import org.mapdb.Fun.Tuple2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * All entities must be from a single feed namespace.
 * Composed of several GTFSTables.
 */
public class GTFSFeed implements Cloneable, Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(GTFSFeed.class);

    public static final double METERS_PER_DEGREE_LATITUDE = 111111.111;

    private DB db;

    public String feedId = null;

    public final Map<String, Agency> agency;
    public final Map<String, FeedInfo> feedInfo;
    // This is how you do a multimap in mapdb: https://github.com/jankotek/MapDB/blob/release-1.0/src/test/java/examples/MultiMap.java
    public final NavigableSet<Tuple2<String, Frequency>> frequencies;
    public final Map<String, Route> routes;
    public final Map<String, Stop> stops;
    public final Map<String, Transfer> transfers;
    public final BTreeMap<String, Trip> trips;

    /** CRC32 of the GTFS file this was loaded from */
    public long checksum;

    /* Map from 2-tuples of (shape_id, shape_pt_sequence) to shape points */
    public final ConcurrentNavigableMap<Tuple2<String, Integer>, ShapePoint> shape_points;

    /* Map from 2-tuples of (trip_id, stop_sequence) to stoptimes. */
    public final BTreeMap<Tuple2, StopTime> stop_times;

    /* A fare is a fare_attribute and all fare_rules that reference that fare_attribute. */
    public final Map<String, Fare> fares;

    /* A service is a calendar entry and all calendar_dates that modify that calendar entry. */
    public final BTreeMap<String, Service> services;

    /* A place to accumulate errors while the feed is loaded. Tolerate as many errors as possible and keep on loading. */
    public final NavigableSet<GTFSError> errors;

    /* Create geometry factory to produce LineString geometries. */
    private GeometryFactory gf = new GeometryFactory();

    private boolean loaded = false;
    private Map<PatternFinder.TripPatternKey, PatternFinder.Pattern> patterns;

    /**
     * The order in which we load the tables is important for two reasons.
     * 1. We must load feed_info first so we know the feed ID before loading any other entities. This could be relaxed
     * by having entities point to the feed object rather than its ID String.
     * 2. Referenced entities must be loaded before any entities that reference them. This is because we check
     * referential integrity while the files are being loaded. This is done on the fly during loading because it allows
     * us to associate a line number with errors in objects that don't have any other clear identifier.
     *
     * Interestingly, all references are resolvable when tables are loaded in alphabetical order.
     */
    public void loadFromZipfileOrDirectory(File zip, String fid) throws IOException {
        if (this.loaded) throw new UnsupportedOperationException("Attempt to load GTFS into existing database");

        new FeedInfo.Loader(this).loadTable(zip);
        // maybe we should just point to the feed object itself instead of its ID, and null out its stoptimes map after loading
        if (fid != null) {
            feedId = fid;
            LOG.info("Feed ID is undefined, pester maintainers to include a feed ID. Using file name {}.", feedId); // TODO log an error, ideally feeds should include a feedID
        }
        else if (feedId == null || feedId.isEmpty()) {
            feedId = new File(zip.getName()).getName().replaceAll("\\.zip$", "");
            LOG.info("Feed ID is undefined, pester maintainers to include a feed ID. Using file name {}.", feedId); // TODO log an error, ideally feeds should include a feedID
        }
        else {
            LOG.info("Feed ID is '{}'.", feedId);
        }

        db.getAtomicString("feed_id").set(feedId);

        new Agency.Loader(this).loadTable(zip);
        if (agency.isEmpty()) {
            errors.add(new GeneralError("agency", 0, "agency_id", "Need at least one agency."));
        }

        // calendars and calendar dates are joined into services. This means a lot of manipulating service objects as
        // they are loaded; since mapdb keys/values are immutable, load them in memory then copy them to MapDB once
        // we're done loading them
        Map<String, Service> serviceTable = new HashMap<>();
        new Calendar.Loader(this, serviceTable).loadTable(zip);
        new CalendarDate.Loader(this, serviceTable).loadTable(zip);
        this.services.putAll(serviceTable);
        serviceTable = null; // free memory

        // Same deal
        Map<String, Fare> fares = new HashMap<>();
        new FareAttribute.Loader(this, fares).loadTable(zip);
        new FareRule.Loader(this, fares).loadTable(zip);
        this.fares.putAll(fares);
        fares = null; // free memory

        new Route.Loader(this).loadTable(zip);
        new ShapePoint.Loader(this).loadTable(zip);
        new Stop.Loader(this).loadTable(zip);
        new Transfer.Loader(this).loadTable(zip);
        new Trip.Loader(this).loadTable(zip);
        new Frequency.Loader(this).loadTable(zip);
        new StopTime.Loader(this).loadTable(zip);
        loaded = true;
    }

    public void loadFromFileAndLogErrors(File zip) throws IOException {
        loadFromZipfileOrDirectory(zip, null);
        for (GTFSError error : errors) {
            LOG.error(error.getMessageWithContext());
        }
    }

    public boolean hasFeedInfo () {
        return !this.feedInfo.isEmpty();
    }

    public FeedInfo getFeedInfo () {
        return this.hasFeedInfo() ? this.feedInfo.values().iterator().next() : null;
    }


    public class StopTimesForTripWithTripPatternKey {
        public StopTimesForTripWithTripPatternKey(List<StopTime> stopTimes, PatternFinder.Pattern pattern) {
            this.stopTimes = stopTimes;
            this.pattern = pattern;
        }

        public List<StopTime> stopTimes;
        public PatternFinder.Pattern pattern;
    }

    public LoadingCache<String, StopTimesForTripWithTripPatternKey> stopTimes = CacheBuilder.newBuilder().maximumSize(200000).build(new CacheLoader<String, StopTimesForTripWithTripPatternKey>() {
        public StopTimesForTripWithTripPatternKey load(String key) {
            PatternFinder.TripPatternKey tripPatternKey = new PatternFinder.TripPatternKey();
            List<StopTime> orderedStopTimesForTrip = new ArrayList<>();
            getInterpolatedStopTimesForTrip(key).forEach(orderedStopTimesForTrip::add);
            orderedStopTimesForTrip.forEach(tripPatternKey::addStopTime);
            PatternFinder.Pattern pattern = findPatterns().get(tripPatternKey);
            return new StopTimesForTripWithTripPatternKey(orderedStopTimesForTrip, pattern);
        }
    });

    /**
     * For the given trip ID, fetch all the stop times in order of increasing stop_sequence.
     * This is an efficient iteration over a tree map.
     */
    public Iterable<StopTime> getOrderedStopTimesForTrip (String trip_id) {
        Map<Fun.Tuple2, StopTime> tripStopTimes =
                stop_times.subMap(
                        Fun.t2(trip_id, null),
                        Fun.t2(trip_id, Fun.HI)
                );
        return tripStopTimes.values();
    }

    /** Get the shape for the given shape ID */
    public Shape getShape (String shape_id) {
        Shape shape = new Shape(this, shape_id);
        return shape.shape_dist_traveled.length > 0 ? shape : null;
    }

    /**
     * For the given trip ID, fetch all the stop times in order, and interpolate stop-to-stop travel times.
     */
    public Iterable<StopTime> getInterpolatedStopTimesForTrip (String trip_id) throws FirstAndLastStopsDoNotHaveTimes {
        // clone stop times so as not to modify base GTFS structures
        StopTime[] stopTimes = StreamSupport.stream(getOrderedStopTimesForTrip(trip_id).spliterator(), false)
                .map(st -> st.clone())
                .toArray(i -> new StopTime[i]);

        // avoid having to make sure that the array has length below.
        if (stopTimes.length == 0) return Collections.emptyList();

        // first pass: set all partially filled stop times
        for (StopTime st : stopTimes) {
            if (st.arrival_time != Entity.INT_MISSING && st.departure_time == Entity.INT_MISSING) {
                st.departure_time = st.arrival_time;
            }

            if (st.arrival_time == Entity.INT_MISSING && st.departure_time != Entity.INT_MISSING) {
                st.arrival_time = st.departure_time;
            }
        }

        // quick check: ensure that first and last stops have times.
        // technically GTFS requires that both arrival_time and departure_time be filled at both the first and last stop,
        // but we are slightly more lenient and only insist that one of them be filled at both the first and last stop.
        // The meaning of the first stop's arrival time is unclear, and same for the last stop's departure time (except
        // in the case of interlining).

        // it's fine to just check departure time, as the above pass ensures that all stop times have either both
        // arrival and departure times, or neither
        if (stopTimes[0].departure_time == Entity.INT_MISSING || stopTimes[stopTimes.length - 1].departure_time == Entity.INT_MISSING) {
            throw new FirstAndLastStopsDoNotHaveTimes();
        }

        // second pass: fill complete stop times
        int startOfInterpolatedBlock = -1;
        for (int stopTime = 0; stopTime < stopTimes.length; stopTime++) {

            if (stopTimes[stopTime].departure_time == Entity.INT_MISSING && startOfInterpolatedBlock == -1) {
                startOfInterpolatedBlock = stopTime;
            }
            else if (stopTimes[stopTime].departure_time != Entity.INT_MISSING && startOfInterpolatedBlock != -1) {
                // we have found the end of the interpolated section
                int nInterpolatedStops = stopTime - startOfInterpolatedBlock;
                double totalLengthOfInterpolatedSection = 0;
                double[] lengthOfInterpolatedSections = new double[nInterpolatedStops];

                for (int stopTimeToInterpolate = startOfInterpolatedBlock, i = 0; stopTimeToInterpolate < stopTime; stopTimeToInterpolate++, i++) {
                    Stop start = stops.get(stopTimes[stopTimeToInterpolate - 1].stop_id);
                    Stop end = stops.get(stopTimes[stopTimeToInterpolate].stop_id);
                    double segLen = fastDistance(start.stop_lat, start.stop_lon, end.stop_lat, end.stop_lon);
                    totalLengthOfInterpolatedSection += segLen;
                    lengthOfInterpolatedSections[i] = segLen;
                }

                // add the segment post-last-interpolated-stop
                Stop start = stops.get(stopTimes[stopTime - 1].stop_id);
                Stop end = stops.get(stopTimes[stopTime].stop_id);
                totalLengthOfInterpolatedSection += fastDistance(start.stop_lat, start.stop_lon, end.stop_lat, end.stop_lon);

                int departureBeforeInterpolation = stopTimes[startOfInterpolatedBlock - 1].departure_time;
                int arrivalAfterInterpolation = stopTimes[stopTime].arrival_time;
                int totalTime = arrivalAfterInterpolation - departureBeforeInterpolation;

                double lengthSoFar = 0;
                for (int stopTimeToInterpolate = startOfInterpolatedBlock, i = 0; stopTimeToInterpolate < stopTime; stopTimeToInterpolate++, i++) {
                    lengthSoFar += lengthOfInterpolatedSections[i];

                    int time = (int) (departureBeforeInterpolation + totalTime * (lengthSoFar / totalLengthOfInterpolatedSection));
                    stopTimes[stopTimeToInterpolate].arrival_time = stopTimes[stopTimeToInterpolate].departure_time = time;
                }

                // we're done with this block
                startOfInterpolatedBlock = -1;
            }
        }

        return Arrays.asList(stopTimes);
    }

    /**
     * @return Equirectangular approximation to distance.
     */
    public static double fastDistance (double lat0, double lon0, double lat1, double lon1) {
        double midLat = (lat0 + lat1) / 2;
        double xscale = Math.cos(Math.toRadians(midLat));
        double dx = xscale * (lon1 - lon0);
        double dy = (lat1 - lat0);
        return Math.sqrt(dx * dx + dy * dy) * METERS_PER_DEGREE_LATITUDE;
    }

    public Collection<Frequency> getFrequencies (String trip_id) {
        // IntelliJ tells me all these casts are unnecessary, and that's also my feeling, but the code won't compile
        // without them
        return (List<Frequency>) frequencies.subSet(new Fun.Tuple2(trip_id, null), new Fun.Tuple2(trip_id, Fun.HI)).stream()
                .map(t2 -> ((Tuple2<String, Frequency>) t2).b)
                .collect(Collectors.toList());
    }

    public LineString getStraightLineForStops(String trip_id) {
        CoordinateList coordinates = new CoordinateList();
        LineString ls = null;
        Trip trip = trips.get(trip_id);

        Iterable<StopTime> stopTimes;
        stopTimes = getOrderedStopTimesForTrip(trip.trip_id);
        if (Iterables.size(stopTimes) > 1) {
            for (StopTime stopTime : stopTimes) {
                Stop stop = stops.get(stopTime.stop_id);
                Double lat = stop.stop_lat;
                Double lon = stop.stop_lon;
                coordinates.add(new Coordinate(lon, lat));
            }
            ls = gf.createLineString(coordinates.toCoordinateArray());
        }
        // set ls equal to null if there is only one stopTime to avoid an exception when creating linestring
        else{
            ls = null;
        }
        return ls;
    }

    /**
     * Returns a trip geometry object (LineString) for a given trip id.
     * If the trip has a shape reference, this will be used for the geometry.
     * Otherwise, the ordered stoptimes will be used.
     *
     * @param   trip_id   trip id of desired trip geometry
     * @return          the LineString representing the trip geometry.
     * @see             LineString
     */
    public LineString getTripGeometry(String trip_id){

        CoordinateList coordinates = new CoordinateList();
        LineString ls = null;
        Trip trip = trips.get(trip_id);

        // If trip has shape_id, use it to generate geometry.
        if (trip.shape_id != null) {
            Shape shape = getShape(trip.shape_id);
            if (shape != null) ls = shape.geometry;
        }

        // Use the ordered stoptimes.
        if (ls == null) {
            ls = getStraightLineForStops(trip_id);
        }

        return ls;
    }

    /**
     * Cloning can be useful when you want to make only a few modifications to an existing feed.
     * Keep in mind that this is a shallow copy, so you'll have to create new maps in the clone for tables you want
     * to modify.
     */
    @Override
    public GTFSFeed clone() {
        try {
            return (GTFSFeed) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    public void close () {
        db.close();
    }

    /** Thrown when we cannot interpolate stop times because the first or last stops do not have times */
    public class FirstAndLastStopsDoNotHaveTimes extends RuntimeException {
        /** do nothing */
    }

    /** Create a GTFS feed in a temp file */
    public GTFSFeed () {
        this(DBMaker.newTempFileDB()
                .transactionDisable()
                .mmapFileEnable()
                .asyncWriteEnable()
                .deleteFilesAfterClose()
                .closeOnJvmShutdown()
                .compressionEnable()
                .make());
    }

    /** Create a GTFS feed connected to a particular DB, which will be created if it does not exist. */
    public GTFSFeed(File file) {
        this(constructDB(file));
    }

    private static DB constructDB(File file) {
        DBMaker<?> dbMaker = DBMaker.newFileDB(file)
                .transactionDisable()
                .mmapFileEnable()
                .asyncWriteEnable()
                .compressionEnable();
        if (file.exists()) {
            dbMaker.readOnly();
        }
        return dbMaker.make();
    }

    private GTFSFeed (DB db) {
        this.db = db;
        agency = db.getTreeMap("agency");
        feedInfo = db.getTreeMap("feed_info");
        routes = db.getTreeMap("routes");
        trips = db.getTreeMap("trips");
        stop_times = db.getTreeMap("stop_times");
        frequencies = db.getTreeSet("frequencies");
        transfers = db.getTreeMap("transfers");
        stops = db.getTreeMap("stops");
        fares = db.getTreeMap("fares");
        services = db.getTreeMap("services");
        shape_points = db.getTreeMap("shape_points");
        feedId = db.getAtomicString("feed_id").get();
        errors = db.getTreeSet("errors");
    }

    public LocalDate getStartDate() {
        LocalDate startDate = getCalendarServiceRangeStart();
        if (startDate == null) startDate = getCalendarDateStart();

        return startDate;
    }

    public LocalDate getCalendarServiceRangeStart() {

        int startDate = 0;
        for (Service service : services.values()) {
            if (service.calendar == null)
                continue;
            if (startDate == 0 || service.calendar.start_date < startDate) {
                startDate = service.calendar.start_date;
            }
        }
        if (startDate == 0)
            return null;

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.getDefault());
        return LocalDate.parse(String.valueOf(startDate), formatter);
    }


    public LocalDate getCalendarDateStart() {
        LocalDate startDate = null;
        for (Service service : services.values()) {
            for (LocalDate date : service.calendar_dates.keySet()) {
                if (startDate == null
                        || date.isBefore(startDate))
                    startDate = date;
            }
        }
        return startDate;
    }

    public LocalDate getCalendarServiceRangeEnd() {

        int endDate = 0;

        for (Service service : services.values()) {
            if (service.calendar == null)
                continue;

            if (endDate == 0 || service.calendar.end_date > endDate) {
                endDate = service.calendar.end_date;
            }
        }
        if (endDate == 0)
            return null;

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.getDefault());
        return LocalDate.parse(String.valueOf(endDate), formatter);
    }

    public LocalDate getEndDate() {
        LocalDate endDate = getCalendarServiceRangeEnd();
        if (endDate == null) endDate = getCalendarDateEnd();

        return endDate;
    }

    public LocalDate getCalendarDateEnd() {
        LocalDate endDate = null;
        for (Service service : services.values()) {
            for (LocalDate date : service.calendar_dates.keySet()) {
                if (endDate == null
                        || date.isAfter(endDate))
                    endDate = date;
            }
        }
        return endDate;
    }

    public Map<PatternFinder.TripPatternKey, PatternFinder.Pattern> findPatterns () {
        if (patterns == null) {
            PatternFinder patternFinder = new PatternFinder();
            // Iterate over trips and process each trip and its stop times.
            for (Trip trip : this.trips.values()) {
                Iterable<StopTime> orderedStopTimesForTrip = this.getOrderedStopTimesForTrip(trip.trip_id);
                patternFinder.processTrip(trip, orderedStopTimesForTrip);
            }
            patterns = patternFinder.createPatternObjects();
        }
        return patterns;
    }

}
