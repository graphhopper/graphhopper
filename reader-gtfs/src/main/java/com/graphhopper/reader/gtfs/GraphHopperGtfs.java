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

package com.graphhopper.reader.gtfs;

import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.Transfer;
import com.graphhopper.reader.DataReader;
import com.graphhopper.reader.dem.ElevationProvider;
import com.graphhopper.reader.osm.GraphHopperOSM;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.PointList;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

public class GraphHopperGtfs extends GraphHopperOSM {

    private final CmdArgs cmdArgs;
    private GtfsStorage gtfsStorage;

    public GraphHopperGtfs(CmdArgs cmdArgs) {
        this.cmdArgs = cmdArgs;
    }

    @Override
    protected void registerCustomEncodedValues(EncodingManager.Builder emBuilder) {
        PtEncodedValues.createAndAddEncodedValues(emBuilder);
    }

    @Override
    protected DataReader importData() throws IOException {
        if (cmdArgs.has("datareader.file")) {
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
                public DataReader setSmoothElevation(boolean smoothElevation) {
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
        if (getGraphHopperStorage().getNodes() > 0) {
            return new LocationIndexTree(getGraphHopperStorage(), new RAMDirectory()).prepareIndex();
        } else {
            return new EmptyLocationIndex();
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
            getGtfsStorage().create();
            GraphHopperStorage graphHopperStorage = getGraphHopperStorage();
            int idx = 0;
            List<String> gtfsFiles = cmdArgs.has("gtfs.file") ? Arrays.asList(cmdArgs.get("gtfs.file", "").split(",")) : Collections.emptyList();
            for (String gtfsFile : gtfsFiles) {
                try {
                    getGtfsStorage().loadGtfsFromFile("gtfs_" + idx++, new ZipFile(gtfsFile));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            LocationIndex streetNetworkIndex = getLocationIndex();
            getGtfsStorage().getGtfsFeeds().forEach((id, gtfsFeed) -> {
                GtfsReader gtfsReader = new GtfsReader(id, graphHopperStorage, graphHopperStorage.getEncodingManager(), getGtfsStorage(), streetNetworkIndex);
                gtfsReader.connectStopsToStreetNetwork();
                getType0TransferWithTimes(gtfsFeed)
                        .forEach(t -> {
                            t.transfer.transfer_type = 2;
                            t.transfer.min_transfer_time = (int) (t.time / 1000L);
                            gtfsFeed.transfers.put(t.id, t.transfer);
                        });
                try {
                    gtfsReader.buildPtNetwork();
                } catch (Exception e) {
                    throw new RuntimeException("Error while constructing transit network. Is your GTFS file valid? Please check log for possible causes.", e);
                }
            });
            streetNetworkIndex.close();
            LocationIndex locationIndex = createLocationIndex(graphHopperStorage.getDirectory());
            setLocationIndex(locationIndex);
        }
    }

    private Stream<TransferWithTime> getType0TransferWithTimes(GTFSFeed gtfsFeed) {
        GraphHopperStorage graphHopperStorage = getGraphHopperStorage();
        RealtimeFeed realtimeFeed = RealtimeFeed.empty(getGtfsStorage());
        PtEncodedValues ptEncodedValues = PtEncodedValues.fromEncodingManager(graphHopperStorage.getEncodingManager());
        FastestWeighting accessEgressWeighting = new FastestWeighting(graphHopperStorage.getEncodingManager().getEncoder("foot"));
        return gtfsFeed.transfers.entrySet()
                .parallelStream()
                .filter(e -> e.getValue().transfer_type == 0)
                .map(e -> {
                    PointList points = new PointList(2, false);
                    final int fromnode = getGtfsStorage().getStationNodes().get(e.getValue().from_stop_id);
                    final QueryResult fromstation = new QueryResult(graphHopperStorage.getNodeAccess().getLat(fromnode), graphHopperStorage.getNodeAccess().getLon(fromnode));
                    fromstation.setClosestNode(fromnode);
                    points.add(graphHopperStorage.getNodeAccess().getLat(fromnode), graphHopperStorage.getNodeAccess().getLon(fromnode));

                    final int tonode = getGtfsStorage().getStationNodes().get(e.getValue().to_stop_id);
                    final QueryResult tostation = new QueryResult(graphHopperStorage.getNodeAccess().getLat(tonode), graphHopperStorage.getNodeAccess().getLon(tonode));
                    tostation.setClosestNode(tonode);
                    points.add(graphHopperStorage.getNodeAccess().getLat(tonode), graphHopperStorage.getNodeAccess().getLon(tonode));

                    QueryGraph queryGraph = QueryGraph.lookup(graphHopperStorage, Collections.emptyList());
                    final GraphExplorer graphExplorer = new GraphExplorer(queryGraph, accessEgressWeighting, ptEncodedValues, getGtfsStorage(), realtimeFeed, false, true, 5.0, false);

                    MultiCriteriaLabelSetting router = new MultiCriteriaLabelSetting(graphExplorer, ptEncodedValues, false, false, false, false, Integer.MAX_VALUE, new ArrayList<>());
                    Iterator<Label> iterator = router.calcLabels(fromnode, Instant.ofEpochMilli(0), 0).iterator();
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
