package com.graphhopper.reader.gtfs;

import com.carrotsearch.hppc.IntHashSet;
import com.google.transit.realtime.GtfsRealtime;

import static com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeUpdate.ScheduleRelationship.SKIPPED;

class RealtimeFeed {
    private final IntHashSet blockedEdges;

    private RealtimeFeed(IntHashSet blockedEdges) {
        this.blockedEdges = blockedEdges;
    }

    static RealtimeFeed empty() {
        return new RealtimeFeed(new IntHashSet());
    }

    static RealtimeFeed fromProtobuf(GtfsStorage staticGtfs, GtfsRealtime.FeedMessage feedMessage) {
        final IntHashSet blockedEdges = new IntHashSet();
        feedMessage.getEntityList().stream()
            .filter(GtfsRealtime.FeedEntity::hasTripUpdate)
            .map(GtfsRealtime.FeedEntity::getTripUpdate)
            .forEach(tripUpdate -> {
                final int[] boardEdges = staticGtfs.getBoardEdgesForTrip().get(tripUpdate.getTrip());
                final int[] leaveEdges = staticGtfs.getAlightEdgesForTrip().get(tripUpdate.getTrip());
                tripUpdate.getStopTimeUpdateList().stream()
                        .filter(stopTimeUpdate -> stopTimeUpdate.getScheduleRelationship() == SKIPPED)
                        .mapToInt(stu -> stu.getStopSequence()-1) // stop sequence number is 1-based, not 0-based
                        .forEach(skippedStopSequenceNumber -> {
                            blockedEdges.add(boardEdges[skippedStopSequenceNumber]);
                            blockedEdges.add(leaveEdges[skippedStopSequenceNumber]);
                        });
            });
        return new RealtimeFeed(blockedEdges);
    }

    boolean isBlocked(int edgeId) {
        return blockedEdges.contains(edgeId);
    }
}
