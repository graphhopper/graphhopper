package com.graphhopper.application.cli;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
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

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class TestTripTransfersCommand extends ConfiguredCommand<GraphHopperServerConfiguration> {
    public TestTripTransfersCommand() {
        super("testtriptransfers", "Test trip transfers");
    }

    @Override
    protected void run(Bootstrap<GraphHopperServerConfiguration> bootstrap, Namespace namespace, GraphHopperServerConfiguration configuration) throws Exception {
        GraphHopperGtfs graphHopper = (GraphHopperGtfs) new GraphHopperManaged(configuration.getGraphHopperConfiguration()).getGraphHopper();
        graphHopper.importOrLoad();

        Map<Trips.TripAtStopTime, Collection<Trips.TripAtStopTime>> tripTransfers = graphHopper.getGtfsStorage().getTripTransfers();

        Collection<Trips.TripAtStopTime> tr = tripTransfers.get(new Trips.TripAtStopTime("gtfs_0", GtfsRealtime.TripDescriptor.newBuilder().setTripId("BA:1376224").build(), 12));
        for (Trips.TripAtStopTime tripAtStopTime : tr) {
            System.out.println(tripAtStopTime);
        }


        PtRouterImpl.Factory factory = new PtRouterImpl.Factory(configuration.getGraphHopperConfiguration(), graphHopper.getTranslationMap(), graphHopper.getBaseGraph(), graphHopper.getEncodingManager(), graphHopper.getLocationIndex(), graphHopper.getGtfsStorage());
        PtRouter ptRouter = factory.createWithoutRealtimeFeed();


        extracted(graphHopper, ptRouter, "19TH", "DBRK", "2023-03-26T08:00:00-07:00");
        extracted(graphHopper, ptRouter, "SFIA", "COLS", "2023-03-26T08:00:00-07:00");
        extracted(graphHopper, ptRouter, "SFIA", "OAKL", "2023-03-26T08:00:00-07:00");
        extracted(graphHopper, ptRouter, "SFIA", "OAKL", "2023-03-26T18:30:00-07:00");
        extracted(graphHopper, ptRouter, "19TH", "40425", "2023-03-26T08:00:00-07:00");


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

    private static void extracted(GraphHopperGtfs graphHopper, PtRouter ptRouter, String origin, String destination, String time) {
        Request request = new Request(Arrays.asList(
                new GHStationLocation(origin),
                new GHStationLocation(destination)), ZonedDateTime.parse(time).toInstant());
        request.setIgnoreTransfers(true);
        request.setLimitStreetTime(Duration.ofMinutes(0));
        long start1 = System.currentTimeMillis();
        GHResponse response = ptRouter.route(request);
        extracted(response);
        System.out.println(response.getHints().getInt("visited_nodes.sum", -1));
        long stop1 = System.currentTimeMillis();
        System.out.printf("millis: %d\n", stop1 - start1);


        long start = System.currentTimeMillis();
        TripBasedRouter tripBasedRouter = new TripBasedRouter(graphHopper.getGtfsStorage(), CacheBuilder.newBuilder().build(new CacheLoader<Trips.TripAtStopTime, Collection<Trips.TripAtStopTime>>() {
            @Override
            public Collection<Trips.TripAtStopTime> load(Trips.TripAtStopTime key) throws Exception {
                return graphHopper.getGtfsStorage().getTripTransfers().get(key);
            }
        }));
        GtfsStorage.FeedIdWithStopId origin1 = new GtfsStorage.FeedIdWithStopId("gtfs_0", ((GHStationLocation) request.getPoints().get(0)).stop_id);
        GtfsStorage.FeedIdWithStopId destination1 = new GtfsStorage.FeedIdWithStopId("gtfs_0", ((GHStationLocation) request.getPoints().get(1)).stop_id);
        List<TripBasedRouter.ResultLabel> route = tripBasedRouter.route(Collections.singletonList(new TripBasedRouter.StopWithTimeDelta(origin1, 0)), Collections.singletonList(new TripBasedRouter.StopWithTimeDelta(destination1, 0)), request.getEarliestDepartureTime());
        for (TripBasedRouter.ResultLabel resultLabel : route) {
            System.out.println(resultLabel.t.tripDescriptor.getTripId() + " " + resultLabel.t.stop_sequence);
        }
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
