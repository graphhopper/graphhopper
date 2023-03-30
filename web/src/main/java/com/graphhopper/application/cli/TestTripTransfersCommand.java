package com.graphhopper.application.cli;

import com.conveyal.gtfs.GTFSFeed;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.transit.realtime.GtfsRealtime;
import com.graphhopper.GHResponse;
import com.graphhopper.Trip;
import com.graphhopper.application.GraphHopperServerConfiguration;
import com.graphhopper.gtfs.*;
import com.graphhopper.gtfs.analysis.Trips;
import com.graphhopper.http.GraphHopperManaged;
import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import org.mapdb.BTreeMap;
import org.mapdb.DB;
import org.mapdb.DBMaker;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class TestTripTransfersCommand extends ConfiguredCommand<GraphHopperServerConfiguration> {
    public TestTripTransfersCommand() {
        super("testtriptransfers", "Test trip transfers");
    }

    @Override
    protected void run(Bootstrap<GraphHopperServerConfiguration> bootstrap, Namespace namespace, GraphHopperServerConfiguration configuration) throws Exception {
        GraphHopperGtfs graphHopper = (GraphHopperGtfs) new GraphHopperManaged(configuration.getGraphHopperConfiguration()).getGraphHopper();
        graphHopper.importOrLoad();


        PtRouterImpl.Factory factory = new PtRouterImpl.Factory(configuration.getGraphHopperConfiguration(), graphHopper.getTranslationMap(), graphHopper.getBaseGraph(), graphHopper.getEncodingManager(), graphHopper.getLocationIndex(), graphHopper.getGtfsStorage());
        PtRouter ptRouter = factory.createWithoutRealtimeFeed();

        DB wurst = DBMaker.newFileDB(new File("wurst")).transactionDisable().mmapFileEnable().readOnly().make();
        Map<Trips.TripAtStopTime, Collection<Trips.TripAtStopTime>> pups = wurst.getTreeMap("pups");
        LoadingCache<Trips.TripAtStopTime, Collection<Trips.TripAtStopTime>> tripTransfers = CacheBuilder.newBuilder().maximumSize(20000000).build(new CacheLoader<Trips.TripAtStopTime, Collection<Trips.TripAtStopTime>>() {
            public Collection<Trips.TripAtStopTime> load(Trips.TripAtStopTime key) {
                return pups.get(key);
            }
        });

        extracted(graphHopper, ptRouter, tripTransfers, "SFIA", "OAKL", "2023-03-26T08:00:00-07:00");
        extracted(graphHopper, ptRouter, tripTransfers, "SFIA", "OAKL", "2023-03-26T18:30:00-07:00");

//        request.setFilter(true);
//        response = ptRouter.route(request);
//        extracted(response);
//        System.out.println(response.getHints().getInt("visited_nodes.sum", -1));

//        Collection<Trips.TripAtStopTime> tr = map.get(new Trips.TripAtStopTime("gtfs_0", GtfsRealtime.TripDescriptor.newBuilder().setTripId("1376012").build(), 14));
//        System.out.println(tr);
//
//        Trips.TripAtStopTime origin = new Trips.TripAtStopTime("gtfs_0", GtfsRealtime.TripDescriptor.newBuilder().setTripId("1376012").build(), 14);
//        GTFSFeed gtfsFeed = graphHopper.getGtfsStorage().getGtfsFeeds().get("gtfs_0");
//        System.out.println(Trips.reduceTripTransfers(gtfsFeed, origin.tripDescriptor, "gtfs_0", graphHopper.getPtGraph(), graphHopper.getGtfsStorage(), LocalDate.parse("2023-03-26")));


//        tr = map.get(new Trips.TripAtStopTime("gtfs_0", GtfsRealtime.TripDescriptor.newBuilder().setTripId("1375941").build(), 11));
//        System.out.println(tr);

//        System.out.println("===");
//
//        tr = map.get(new Trips.TripAtStopTime("gtfs_0", GtfsRealtime.TripDescriptor.newBuilder().setTripId("1376011").build(), 13));
//        System.out.println(tr);
//        tr = map.get(new Trips.TripAtStopTime("gtfs_0", GtfsRealtime.TripDescriptor.newBuilder().setTripId("1376239").build(), 12));
//        System.out.println(tr);

//        wurst.close();


        graphHopper.close();
    }

    private static void extracted(GraphHopperGtfs graphHopper, PtRouter ptRouter, LoadingCache<Trips.TripAtStopTime, Collection<Trips.TripAtStopTime>> tripTransfers, String origin, String destination, String time) {
        Request request = new Request(Arrays.asList(
                new GHStationLocation(origin),
                new GHStationLocation(destination)), ZonedDateTime.parse(time).toInstant());
        request.setIgnoreTransfers(true);
        request.setLimitStreetTime(Duration.ofMinutes(0));
        GHResponse response = ptRouter.route(request);

        extracted(response);
        System.out.println(response.getHints().getInt("visited_nodes.sum", -1));


        long start = System.currentTimeMillis();
        TripBasedRouter tripBasedRouter = new TripBasedRouter(tripTransfers, graphHopper.getGtfsStorage());
        tripBasedRouter.route(request);
        long stop = System.currentTimeMillis();
        System.out.printf("millis: %d\n", stop - start);
    }

    private static void extracted(GHResponse response) {
        System.out.println(response.getBest().getLegs().stream().filter(l -> l instanceof Trip.PtLeg).map(l -> {
            StringBuilder dildo = new StringBuilder();
            dildo.append(((Trip.PtLeg) l).trip_id+"\n");
            dildo.append(((Trip.PtLeg) l).stops.stream().map(s -> s.toString()).collect(Collectors.joining("\n")));
            return dildo;
        }).collect(Collectors.toList()));
    }
}
