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
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

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
        // if the location index was already created (we are 'loading') we use it. but we must not create the location
        // index object in case the index does not exist yet, because we only can create it once. we are not ready yet,
        // because first we need to import PT.
        if (Files.exists(Paths.get(getGraphHopperLocation()).resolve("location_index"))) {
            LocationIndexTree index = new LocationIndexTree(getGraphHopperStorage(), dir);
            index.loadExisting();
            return index;
        }
        return null;
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
            // temporary location index for the street network that we only use during import
            LocationIndexTree streetNetworkIndex = new LocationIndexTree(getGraphHopperStorage(), new RAMDirectory());
            streetNetworkIndex.prepareIndex();
            try {
                int idx = 0;
                List<String> gtfsFiles = ghConfig.has("gtfs.file") ? Arrays.asList(ghConfig.getString("gtfs.file", "").split(",")) : Collections.emptyList();
                for (String gtfsFile : gtfsFiles) {
                    getGtfsStorage().loadGtfsFromZipFileOrDirectory("gtfs_" + idx++, new File(gtfsFile));
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
            // now we build the final location index
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
        Weighting transferWeighting = createWeighting(getProfile("foot"), new PMap());
        PtEncodedValues ptEncodedValues = PtEncodedValues.fromEncodingManager(graphHopperStorage.getEncodingManager());
        final GraphExplorer graphExplorer = new GraphExplorer(queryGraph, transferWeighting, ptEncodedValues, getGtfsStorage(), RealtimeFeed.empty(getGtfsStorage()), true, true, false, 5.0, false, 0);
        getGtfsStorage().getStationNodes().values().stream().distinct().forEach(stationNode -> {
            MultiCriteriaLabelSetting router = new MultiCriteriaLabelSetting(graphExplorer, ptEncodedValues, true, false, false, 0, new ArrayList<>());
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
                                    LOGGER.debug(" Different feed. Inserting transfer with " + (int) (label.streetTime / 1000L) + " s.");
                                    GtfsReader toFeedReader = readers.get(toPlatformDescriptor.feed_id);
                                    toFeedReader.insertTransferEdges(label.adjNode, (int) (label.streetTime / 1000L), toPlatformDescriptor);
                                } else {
                                    List<Transfer> transfersToStop = transfers.getTransfersToStop(toPlatformDescriptor.stop_id, routeIdOrNull(toPlatformDescriptor));
                                    if (transfersToStop.stream().noneMatch(t -> t.from_stop_id.equals(fromPlatformDescriptor.stop_id))) {
                                        GtfsReader toFeedReader = readers.get(toPlatformDescriptor.feed_id);
                                        toFeedReader.insertTransferEdges(label.adjNode, (int) (label.streetTime / 1000L), toPlatformDescriptor);
                                        LOGGER.debug("  Inserting transfer with " + (int) (label.streetTime / 1000L) + " s.");
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
        Weighting transferWeighting = createWeighting(getProfile("foot"), new PMap());
        return gtfsFeed.transfers.entrySet()
                .parallelStream()
                .filter(e -> e.getValue().transfer_type == 0)
                .map(e -> {
                    final int fromnode = getGtfsStorage().getStationNodes().get(new GtfsStorage.FeedIdWithStopId(id, e.getValue().from_stop_id));
                    final int tonode = getGtfsStorage().getStationNodes().get(new GtfsStorage.FeedIdWithStopId(id, e.getValue().to_stop_id));

                    QueryGraph queryGraph = QueryGraph.create(graphHopperStorage, Collections.emptyList());
                    final GraphExplorer graphExplorer = new GraphExplorer(queryGraph, transferWeighting, ptEncodedValues, getGtfsStorage(), realtimeFeed, false, true, false, 5.0, false, 0);

                    MultiCriteriaLabelSetting router = new MultiCriteriaLabelSetting(graphExplorer, ptEncodedValues, false, false, false, 0, new ArrayList<>());
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
