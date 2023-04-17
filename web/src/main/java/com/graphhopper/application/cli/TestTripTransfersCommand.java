package com.graphhopper.application.cli;

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
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class TestTripTransfersCommand extends ConfiguredCommand<GraphHopperServerConfiguration> {

    private Trips trips;

    public TestTripTransfersCommand() {
        super("testtriptransfers", "Test trip transfers");
    }

    public static class Data {
        long beforeMillis;
        long afterMillis;

        public Data(long beforeMillis, long afterMillis) {
            this.beforeMillis = beforeMillis;
            this.afterMillis = afterMillis;
        }

        @Override
        public String toString() {
            return String.format("millis before: %d\t\tmillis after: %d\n", beforeMillis, afterMillis);
        }
    }

    List<Data> data = new ArrayList<>();

    @Override
    protected void run(Bootstrap<GraphHopperServerConfiguration> bootstrap, Namespace namespace, GraphHopperServerConfiguration configuration) throws Exception {
        GraphHopperGtfs graphHopper = (GraphHopperGtfs) new GraphHopperManaged(configuration.getGraphHopperConfiguration()).getGraphHopper();
        graphHopper.importOrLoad();


        Map<Trips.TripAtStopTime, Collection<Trips.TripAtStopTime>> tripTransfers = graphHopper.getGtfsStorage().getTripTransfers(LocalDate.parse("2023-04-05"));

        PtRouterImpl.Factory factory = new PtRouterImpl.Factory(configuration.getGraphHopperConfiguration(), graphHopper.getTranslationMap(), graphHopper.getBaseGraph(), graphHopper.getEncodingManager(), graphHopper.getLocationIndex(), graphHopper.getGtfsStorage());
        PtRouter ptRouter = factory.createWithoutRealtimeFeed();
        PtRouterTripBasedImpl.Factory tripBasedFactory = new PtRouterTripBasedImpl.Factory(configuration.getGraphHopperConfiguration(), graphHopper.getTranslationMap(), graphHopper.getBaseGraph(), graphHopper.getEncodingManager(), graphHopper.getLocationIndex(), graphHopper.getGtfsStorage());
        PtRouter tripBasedPtRouter = tripBasedFactory.createWithoutRealtimeFeed();

//        trips = new Trips(graphHopper.getGtfsStorage());
//        trips.setTrafficDay(LocalDate.parse("2023-03-26"));

        for (int i = 0; i < 30; i++) {
            extracted(graphHopper, ptRouter, tripBasedPtRouter, "19TH", "DBRK", "2023-04-05T08:00:00-07:00");
            extracted(graphHopper, ptRouter, tripBasedPtRouter, "SFIA", "COLS", "2023-04-05T08:00:00-07:00");
            extracted(graphHopper, ptRouter, tripBasedPtRouter, "SFIA", "OAKL", "2023-04-05T08:00:00-07:00");
            extracted(graphHopper, ptRouter, tripBasedPtRouter, "SFIA", "OAKL", "2023-04-05T18:30:00-07:00");
            extracted(graphHopper, ptRouter, tripBasedPtRouter, "19TH", "40425", "2023-04-05T08:00:00-07:00");
            extracted(graphHopper, ptRouter, tripBasedPtRouter, "MONT", "WDUB", "2023-04-05T08:00:00-07:00");
        }

        for (Data datum : data) {
            System.out.println(datum);
        }


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

    private void extracted(GraphHopperGtfs graphHopper, PtRouter ptRouter, PtRouter tripBasedPtRouter, String origin, String destination, String time) {
        Request request = new Request(Arrays.asList(
                new GHStationLocation(origin),
                new GHStationLocation(destination)), ZonedDateTime.parse(time).toInstant());
        request.setIgnoreTransfers(true);
        request.setLimitStreetTime(Duration.ofMinutes(50));
        request.setProfileQuery(true);
        request.setMaxProfileDuration(Duration.ofMinutes(5));
        long start1 = System.currentTimeMillis();
        GHResponse response = ptRouter.route(request);
        extracted(response);
//        System.out.println(response.getHints().getInt("visited_nodes.sum", -1));
        long stop1 = System.currentTimeMillis();

        long start2 = System.currentTimeMillis();
        response = tripBasedPtRouter.route(request);
        extracted(response);
        long stop2 = System.currentTimeMillis();
        data.add(new Data(stop1 - start1, stop2 - start2));
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
