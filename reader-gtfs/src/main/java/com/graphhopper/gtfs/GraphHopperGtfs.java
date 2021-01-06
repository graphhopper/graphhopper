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
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.reader.DataReader;
import com.graphhopper.reader.dem.ElevationProvider;
import com.graphhopper.reader.osm.GraphHopperOSM;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
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

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

public class GraphHopperGtfs extends GraphHopperOSM {

    private static final Logger LOGGER = LoggerFactory.getLogger(GraphHopperGtfs.class);

    private final GraphHopperConfig ghConfig;
    private GtfsStorage gtfsStorage;

    public GraphHopperGtfs(GraphHopperConfig ghConfig) {
        this.ghConfig = ghConfig;
    }

    @Override
    protected void registerCustomEncodedValues(EncodingManager.Builder emBuilder) {
        PtEncodedValues.createAndAddEncodedValues(emBuilder);
    }

    @Override
    protected DataReader importData() throws IOException {
        if (ghConfig.has("datareader.file")) {
            return super.importData();
        } else {
            getGraphHopperStorage().create(1000);
            return new DataReader() {
                @Override
                public DataReader setFile(File file) {
                    return this;
                }

                @Override
                public DataReader setElevationProvider(ElevationProvider ep) {
                    return this;
                }

                @Override
                public DataReader setWorkerThreads(int workerThreads) {
                    return this;
                }

                @Override
                public DataReader setWayPointMaxDistance(double wayPointMaxDistance) {
                    return this;
                }

                @Override
                public DataReader setWayPointElevationMaxDistance(double elevationWayPointMaxDistance) {
                    return this;
                }

                @Override
                public DataReader setSmoothElevation(boolean smoothElevation) {
                    return this;
                }

                @Override
                public DataReader setLongEdgeSamplingDistance(double longEdgeSamplingDistance) {
                    return this;
                }

                @Override
                public void readGraph() {

                }

                @Override
                public Date getDataDate() {
                    return null;
                }
            };
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

                //When set a transfer edge will be created between stops connected to same OSM node. This is to keep previous behavior before
                //this commit https://github.com/graphhopper/graphhopper/commit/31ae1e1534849099f24e45d53c96340a7c6a5197.
                boolean createTransferStopsConnectSameOsmNode = ghConfig.has("gtfs.create_transfers_stops_same_osm_node") &&
                        ghConfig.getBool("gtfs.create_transfers_stops_same_osm_node", false);

                HashMap<String, GtfsReader> readers = new HashMap<>();
                getGtfsStorage().getGtfsFeeds().forEach((id, gtfsFeed) -> {
                    Transfers transfers = new Transfers(gtfsFeed);
                    GtfsReader gtfsReader = new GtfsReader(id, graphHopperStorage, graphHopperStorage.getEncodingManager(), getGtfsStorage(), streetNetworkIndex, transfers);
                    gtfsReader.setCreateTransferStopsConnectSameOsmNode(createTransferStopsConnectSameOsmNode);
                    gtfsReader.connectStopsToStreetNetwork();
                    getType0TransferWithTimes(id, gtfsFeed)
                            .forEach(t -> {
                                t.transfer.transfer_type = 2;
                                t.transfer.min_transfer_time = (int) (t.time / 1000L);
                                gtfsFeed.transfers.put(t.id, t.transfer);
                            });
                    LOGGER.info("Building transit graph for feed {}", gtfsFeed.feedId);
                    gtfsReader.buildPtNetwork();
                    readers.put(id, gtfsReader);
                });
                insertTransfersBetweenFeeds(readers);
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

    private void insertTransfersBetweenFeeds(HashMap<String, GtfsReader> readers) {
        LOGGER.info("Looking for inter-feed transfers");
        GraphHopperStorage graphHopperStorage = getGraphHopperStorage();
        QueryGraph queryGraph = QueryGraph.create(graphHopperStorage, Collections.emptyList());
        FastestWeighting accessEgressWeighting = new FastestWeighting(graphHopperStorage.getEncodingManager().getEncoder("foot"));
        PtEncodedValues ptEncodedValues = PtEncodedValues.fromEncodingManager(graphHopperStorage.getEncodingManager());
        final GraphExplorer graphExplorer = new GraphExplorer(queryGraph, accessEgressWeighting, ptEncodedValues, getGtfsStorage(), RealtimeFeed.empty(getGtfsStorage()), true, true, false, 5.0, false, 0);
        getGtfsStorage().getStationNodes().values().stream().distinct().forEach(stationNode -> {
            MultiCriteriaLabelSetting router = new MultiCriteriaLabelSetting(graphExplorer, ptEncodedValues, true, false, false, Integer.MAX_VALUE, new ArrayList<>());
            router.setLimitStreetTime(Duration.ofMinutes(2).toMillis());
            Iterator<Label> iterator = router.calcLabels(stationNode, Instant.ofEpochMilli(0)).iterator();
            while (iterator.hasNext()) {
                Label label = iterator.next();
                if (label.parent != null) {
                    EdgeIteratorState edgeIteratorState = graphHopperStorage.getEdgeIteratorState(label.edge, label.adjNode);
                    if (edgeIteratorState.get(ptEncodedValues.getTypeEnc()) == GtfsStorage.EdgeType.EXIT_PT) {
                        GtfsStorageI.PlatformDescriptor fromPlatformDescriptor = getGtfsStorage().getPlatformDescriptorByEdge().get(label.edge);
                        DefaultEdgeFilter filter = DefaultEdgeFilter.outEdges(ptEncodedValues.getAccessEnc());
                        EdgeExplorer edgeExplorer = graphHopperStorage.createEdgeExplorer(filter);
                        EdgeIterator edgeIterator = edgeExplorer.setBaseNode(stationNode);
                        while (edgeIterator.next()) {
                            if (edgeIterator.get(ptEncodedValues.getTypeEnc()) == GtfsStorage.EdgeType.ENTER_PT) {
                                GtfsStorageI.PlatformDescriptor toPlatformDescriptor = getGtfsStorage().getPlatformDescriptorByEdge().get(edgeIterator.getEdge());
                                if (!toPlatformDescriptor.feed_id.equals(fromPlatformDescriptor.feed_id)) {
                                    GtfsReader toFeedReader = readers.get(toPlatformDescriptor.feed_id);
                                    toFeedReader.insertTransferEdges(label.adjNode, (int) Duration.ofMinutes(2).getSeconds(), toPlatformDescriptor);
                                }
                            }
                        }
                    }
                }
            }
        });
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
