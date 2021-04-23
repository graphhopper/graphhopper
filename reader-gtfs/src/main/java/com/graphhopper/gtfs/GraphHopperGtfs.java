/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.graphhopper.gtfs;

import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.Transfer;
import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.AccessFilter;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PointList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

public class GraphHopperGtfs extends GraphHopper {

    private static final Logger LOGGER = LoggerFactory.getLogger(GraphHopperGtfs.class);

    private final GraphHopperConfig ghConfig;
    private GtfsStorage gtfsStorage;

    public GraphHopperGtfs(GraphHopperConfig ghConfig) {
        this.ghConfig = ghConfig;
        PtEncodedValues.createAndAddEncodedValues(getEncodingManagerBuilder());
    }

    @Override
    protected void importOSM() {
        if (ghConfig.has("datareader.file")) {
            super.importOSM();
        } else {
            getGraphHopperStorage().create(1000);
        }
    }

    @Override
    protected LocationIndex createLocationIndex(Directory dir) {
        LocationIndexTree tmpIndex = new LocationIndexTree(getGraphHopperStorage(), dir);
        if (tmpIndex.loadExisting()) {
            return tmpIndex;
        } else {
            LocationIndexTree locationIndexTree = new LocationIndexTree(getGraphHopperStorage(), new RAMDirectory());
            if (!locationIndexTree.loadExisting()) {
                locationIndexTree.prepareIndex();
            }
            return locationIndexTree;
        }
    }

    static class TransferWithTime {
        public String id;
        Transfer transfer;
        long time;
    }

    @Override
    protected void importPublicTransit() {
        gtfsStorage = new GtfsStorage(getGraphHopperStorage().getDirectory());
        if (!getGtfsStorage().loadExisting()) {
            ensureWriteAccess();
            getGtfsStorage().create();
            GraphHopperStorage graphHopperStorage = getGraphHopperStorage();
            LocationIndex streetNetworkIndex = getLocationIndex();
            try {
                int idx = 0;
                List<String> gtfsFiles = ghConfig.has("gtfs.file") ? Arrays.asList(ghConfig.getString("gtfs.file", "").split(",")) : Collections.emptyList();
                for (String gtfsFile : gtfsFiles) {
                    try {
                        getGtfsStorage().loadGtfsFromZipFile("gtfs_" + idx++, new ZipFile(gtfsFile));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                getGtfsStorage().postInit();
                Map<String, Transfers> allTransfers = new HashMap<>();
                HashMap<String, GtfsReader> allReaders = new HashMap<>();
                getGtfsStorage().getGtfsFeeds().forEach((id, gtfsFeed) -> {
                    Transfers transfers = new Transfers(gtfsFeed);
                    allTransfers.put(id, transfers);
                    GtfsReader gtfsReader = new GtfsReader(id, graphHopperStorage, graphHopperStorage.getEncodingManager(), getGtfsStorage(), streetNetworkIndex, transfers);
                    gtfsReader.connectStopsToStreetNetwork();
                    getType0TransferWithTimes(id, gtfsFeed)
                            .forEach(t -> {
                                t.transfer.transfer_type = 2;
                                t.transfer.min_transfer_time = (int) (t.time / 1000L);
                                gtfsFeed.transfers.put(t.id, t.transfer);
                            });
                    LOGGER.info("Building transit graph for feed {}", gtfsFeed.feedId);
                    gtfsReader.buildPtNetwork();
                    allReaders.put(id, gtfsReader);
                });
                interpolateTransfers(allReaders, allTransfers);
            } catch (Exception e) {
                throw new RuntimeException("Error while constructing transit network. Is your GTFS file valid? Please check log for possible causes.", e);
            }
            streetNetworkIndex.close();
            LocationIndexTree locationIndex = new LocationIndexTree(getGraphHopperStorage(), getGraphHopperStorage().getDirectory());
            PtEncodedValues ptEncodedValues = PtEncodedValues.fromEncodingManager(getEncodingManager());
            EnumEncodedValue<GtfsStorage.EdgeType> typeEnc = ptEncodedValues.getTypeEnc();
            locationIndex.prepareIndex(edgeState -> edgeState.get(typeEnc) == GtfsStorage.EdgeType.HIGHWAY);
            setLocationIndex(locationIndex);
        }
    }

    private void interpolateTransfers(HashMap<String, GtfsReader> readers, Map<String, Transfers> allTransfers) {
        LOGGER.info("Looking for transfers");
        final int maxTransferWalkTimeSeconds = ghConfig.getInt("gtfs.max_transfer_interpolation_walk_time_seconds", 120);
        GraphHopperStorage graphHopperStorage = getGraphHopperStorage();
        QueryGraph queryGraph = QueryGraph.create(graphHopperStorage, Collections.emptyList());
        FastestWeighting accessEgressWeighting = new FastestWeighting(graphHopperStorage.getEncodingManager().getEncoder("foot"));
        PtEncodedValues ptEncodedValues = PtEncodedValues.fromEncodingManager(graphHopperStorage.getEncodingManager());
        final GraphExplorer graphExplorer = new GraphExplorer(queryGraph, accessEgressWeighting, ptEncodedValues, getGtfsStorage(), RealtimeFeed.empty(getGtfsStorage()), true, true, false, 5.0, false, 0);
        getGtfsStorage().getStationNodes().values().stream().distinct().forEach(stationNode -> {
            MultiCriteriaLabelSetting router = new MultiCriteriaLabelSetting(graphExplorer, ptEncodedValues, true, false, false, Integer.MAX_VALUE, new ArrayList<>());
            router.setLimitStreetTime(Duration.ofSeconds(maxTransferWalkTimeSeconds).toMillis());
            Iterator<Label> iterator = router.calcLabels(stationNode, Instant.ofEpochMilli(0)).iterator();
            while (iterator.hasNext()) {
                Label label = iterator.next();
                if (label.parent != null) {
                    EdgeIteratorState edgeIteratorState = graphHopperStorage.getEdgeIteratorState(label.edge, label.adjNode);
                    if (edgeIteratorState.get(ptEncodedValues.getTypeEnc()) == GtfsStorage.EdgeType.EXIT_PT) {
                        GtfsStorageI.PlatformDescriptor fromPlatformDescriptor = getGtfsStorage().getPlatformDescriptorByEdge().get(label.edge);
                        Transfers transfers = allTransfers.get(fromPlatformDescriptor.feed_id);
                        AccessFilter filter = AccessFilter.outEdges(ptEncodedValues.getAccessEnc());
                        EdgeExplorer edgeExplorer = graphHopperStorage.createEdgeExplorer(filter);
                        EdgeIterator edgeIterator = edgeExplorer.setBaseNode(stationNode);
                        while (edgeIterator.next()) {
                            if (edgeIterator.get(ptEncodedValues.getTypeEnc()) == GtfsStorage.EdgeType.ENTER_PT) {
                                GtfsStorageI.PlatformDescriptor toPlatformDescriptor = getGtfsStorage().getPlatformDescriptorByEdge().get(edgeIterator.getEdge());
                                LOGGER.debug(fromPlatformDescriptor + " -> " + toPlatformDescriptor);
                                if (!toPlatformDescriptor.feed_id.equals(fromPlatformDescriptor.feed_id)) {
                                    LOGGER.debug(" Different feed. Inserting transfer with " + (int) (label.walkTime / 1000L) + " s.");
                                    GtfsReader toFeedReader = readers.get(toPlatformDescriptor.feed_id);
                                    toFeedReader.insertTransferEdges(label.adjNode, (int) (label.walkTime / 1000L), toPlatformDescriptor);
                                } else {
                                    List<Transfer> transfersToStop = transfers.getTransfersToStop(toPlatformDescriptor.stop_id, routeIdOrNull(toPlatformDescriptor));
                                    if (transfersToStop.stream().noneMatch(t -> t.from_stop_id.equals(fromPlatformDescriptor.stop_id))) {
                                        GtfsReader toFeedReader = readers.get(toPlatformDescriptor.feed_id);
                                        toFeedReader.insertTransferEdges(label.adjNode, (int) (label.walkTime / 1000L), toPlatformDescriptor);
                                        LOGGER.debug("  Inserting transfer with " + (int) (label.walkTime / 1000L) + " s.");
                                    }
                                }
                            }
                        }
                    }
                }
            }
        });
    }

    private String routeIdOrNull(GtfsStorageI.PlatformDescriptor platformDescriptor) {
        if (platformDescriptor instanceof GtfsStorageI.RouteTypePlatform) {
            return null;
        } else {
            return ((GtfsStorageI.RoutePlatform) platformDescriptor).route_id;
        }
    }

    private Stream<TransferWithTime> getType0TransferWithTimes(String id, GTFSFeed gtfsFeed) {
        GraphHopperStorage graphHopperStorage = getGraphHopperStorage();
        RealtimeFeed realtimeFeed = RealtimeFeed.empty(getGtfsStorage());
        PtEncodedValues ptEncodedValues = PtEncodedValues.fromEncodingManager(graphHopperStorage.getEncodingManager());
        FastestWeighting accessEgressWeighting = new FastestWeighting(graphHopperStorage.getEncodingManager().getEncoder("foot"));
        return gtfsFeed.transfers.entrySet()
                .parallelStream()
                .filter(e -> e.getValue().transfer_type == 0)
                .map(e -> {
                    PointList points = new PointList(2, false);
                    final int fromnode = getGtfsStorage().getStationNodes().get(new GtfsStorage.FeedIdWithStopId(id, e.getValue().from_stop_id));
                    final Snap fromstation = new Snap(graphHopperStorage.getNodeAccess().getLat(fromnode), graphHopperStorage.getNodeAccess().getLon(fromnode));
                    fromstation.setClosestNode(fromnode);
                    points.add(graphHopperStorage.getNodeAccess().getLat(fromnode), graphHopperStorage.getNodeAccess().getLon(fromnode));

                    final int tonode = getGtfsStorage().getStationNodes().get(new GtfsStorage.FeedIdWithStopId(id, e.getValue().to_stop_id));
                    final Snap tostation = new Snap(graphHopperStorage.getNodeAccess().getLat(tonode), graphHopperStorage.getNodeAccess().getLon(tonode));
                    tostation.setClosestNode(tonode);
                    points.add(graphHopperStorage.getNodeAccess().getLat(tonode), graphHopperStorage.getNodeAccess().getLon(tonode));

                    QueryGraph queryGraph = QueryGraph.create(graphHopperStorage, Collections.emptyList());
                    final GraphExplorer graphExplorer = new GraphExplorer(queryGraph, accessEgressWeighting, ptEncodedValues, getGtfsStorage(), realtimeFeed, false, true, false, 5.0, false, 0);

                    MultiCriteriaLabelSetting router = new MultiCriteriaLabelSetting(graphExplorer, ptEncodedValues, false, false, false, Integer.MAX_VALUE, new ArrayList<>());
                    Iterator<Label> iterator = router.calcLabels(fromnode, Instant.ofEpochMilli(0)).iterator();
                    Label solution = null;
                    while (iterator.hasNext()) {
                        Label label = iterator.next();
                        if (tonode == label.adjNode) {
                            solution = label;
                            break;
                        }
                    }
                    if (solution == null) {
                        throw new RuntimeException("Can't find a transfer walk route.");
                    }
                    TransferWithTime transferWithTime = new TransferWithTime();
                    transferWithTime.id = e.getKey();
                    transferWithTime.transfer = e.getValue();
                    transferWithTime.time = solution.currentTime;
                    return transferWithTime;
                });
    }

    @Override
    public void close() {
        getGtfsStorage().close();
        super.close();
    }

    public GtfsStorage getGtfsStorage() {
        return gtfsStorage;
    }

}
