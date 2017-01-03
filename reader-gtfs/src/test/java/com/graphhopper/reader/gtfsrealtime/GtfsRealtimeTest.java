package com.graphhopper.reader.gtfsrealtime;

import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.Trip;
import com.google.transit.realtime.GtfsRealtime;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.net.URL;
import java.util.Comparator;

public class GtfsRealtimeTest {

    @Test
    public void testData() throws IOException {
        GTFSFeed gtfsFeed = GTFSFeed.fromFile("files/bart.zip");
        URL url = new URL("file:files/bart.proto");
        GtfsRealtime.FeedMessage feed = GtfsRealtime.FeedMessage.parseFrom(url.openStream());
        GtfsRealtime.FeedEntity tripUpdateWithBiggestDelay = feed.getEntityList().stream()
                .max(Comparator.comparingInt(entity ->
                    entity.getTripUpdate().getStopTimeUpdateList().stream()
                            .max(Comparator.comparingInt(update ->
                                    update.getDeparture().getDelay())).get().getDeparture().getDelay()
        )).get();
        for (GtfsRealtime.FeedEntity entity : feed.getEntityList()) {
            if (entity.hasTripUpdate()) {
                Trip trip = gtfsFeed.trips.get(entity.getTripUpdate().getTrip().getTripId());
                System.out.println(trip);

                System.out.println(entity.getTripUpdate());
            }
        }
        System.out.println("---");
        System.out.println(tripUpdateWithBiggestDelay);
        Assert.assertEquals(tripUpdateWithBiggestDelay.getTripUpdate().getTrip().getTripId(), "20DCM21");
        Trip trip = gtfsFeed.trips.get("20DCM21");
        Assert.assertNotNull(trip);
    }

}
