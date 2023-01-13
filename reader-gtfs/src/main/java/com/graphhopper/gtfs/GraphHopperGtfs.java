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

import com.conveyal.gtfs.model.Transfer;
import com.graphhopper.core.GraphHopper;
import com.graphhopper.core.GraphHopperConfig;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.index.InMemConstructionIndex;
import com.graphhopper.storage.index.IndexStructureInfo;
import com.graphhopper.storage.index.LineIntIndex;
import com.graphhopper.core.util.EdgeIteratorState;
import com.graphhopper.util.PMap;
import com.graphhopper.core.util.shapes.BBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class GraphHopperGtfs extends GraphHopper {

    private static final Logger LOGGER = LoggerFactory.getLogger(GraphHopperGtfs.class);

    private final GraphHopperConfig ghConfig;
    private GtfsStorage gtfsStorage;
    private PtGraph ptGraph;

    public GraphHopperGtfs(GraphHopperConfig ghConfig) {
        this.ghConfig = ghConfig;
    }

    @Override
    protected void importOSM() {
        if (ghConfig.has("datareader.file")) {
            super.importOSM();
        } else {
            createBaseGraphAndProperties();
            writeEncodingManagerToProperties();
        }
    }

    @Override
    protected void importPublicTransit() {
        ptGraph = new PtGraph(getBaseGraph().getDirectory(), 100);
        gtfsStorage = new GtfsStorage(getBaseGraph().getDirectory());
        LineIntIndex stopIndex = new LineIntIndex(new BBox(-180.0, 180.0, -90.0, 90.0), getBaseGraph().getDirectory(), "stop_index");
        if (getGtfsStorage().loadExisting()) {
            ptGraph.loadExisting();
            stopIndex.loadExisting();
        } else {
            ensureWriteAccess();
            getGtfsStorage().create();
            ptGraph.create(100);
            InMemConstructionIndex indexBuilder = new InMemConstructionIndex(IndexStructureInfo.create(
                    new BBox(-180.0, 180.0, -90.0, 90.0), 300));
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
                    GtfsReader gtfsReader = new GtfsReader(id, getBaseGraph(), getEncodingManager(), ptGraph, ptGraph, getGtfsStorage(), getLocationIndex(), transfers, indexBuilder);
                    gtfsReader.connectStopsToStreetNetwork();
                    LOGGER.info("Building transit graph for feed {}", gtfsFeed.feedId);
                    gtfsReader.buildPtNetwork();
                    allReaders.put(id, gtfsReader);
                });
                interpolateTransfers(allReaders, allTransfers);
            } catch (Exception e) {
                throw new RuntimeException("Error while constructing transit network. Is your GTFS file valid? Please check log for possible causes.", e);
            }
            ptGraph.flush();
            getGtfsStorage().flush();
            stopIndex.store(indexBuilder);
            stopIndex.flush();
        }
        gtfsStorage.setStopIndex(stopIndex);
        gtfsStorage.setPtGraph(ptGraph);
    }

    private void interpolateTransfers(HashMap<String, GtfsReader> readers, Map<String, Transfers> allTransfers) {
        LOGGER.info("Looking for transfers");
        final int maxTransferWalkTimeSeconds = ghConfig.getInt("gtfs.max_transfer_interpolation_walk_time_seconds", 120);
        QueryGraph queryGraph = QueryGraph.create(getBaseGraph(), Collections.emptyList());
        Weighting transferWeighting = createWeighting(getProfile("foot"), new PMap());
        final GraphExplorer graphExplorer = new GraphExplorer(queryGraph, ptGraph, transferWeighting, getGtfsStorage(), RealtimeFeed.empty(), true, true, false, 5.0, false, 0);
        getGtfsStorage().getStationNodes().values().stream().distinct().map(n -> new Label.NodeId(gtfsStorage.getPtToStreet().getOrDefault(n, -1), n)).forEach(stationNode -> {
            MultiCriteriaLabelSetting router = new MultiCriteriaLabelSetting(graphExplorer, true, false, false, 0, new ArrayList<>());
            router.setLimitStreetTime(Duration.ofSeconds(maxTransferWalkTimeSeconds).toMillis());
            for (Label label : router.calcLabels(stationNode, Instant.ofEpochMilli(0))) {
                if (label.parent != null) {
                    if (label.edge.getType() == GtfsStorage.EdgeType.EXIT_PT) {
                        GtfsStorage.PlatformDescriptor fromPlatformDescriptor = label.edge.getPlatformDescriptor();
                        Transfers transfers = allTransfers.get(fromPlatformDescriptor.feed_id);
                        for (PtGraph.PtEdge ptEdge : ptGraph.edgesAround(stationNode.ptNode)) {
                            if (ptEdge.getType() == GtfsStorage.EdgeType.ENTER_PT) {
                                GtfsStorage.PlatformDescriptor toPlatformDescriptor = ptEdge.getAttrs().platformDescriptor;
                                LOGGER.debug(fromPlatformDescriptor + " -> " + toPlatformDescriptor);
                                if (!toPlatformDescriptor.feed_id.equals(fromPlatformDescriptor.feed_id)) {
                                    LOGGER.debug(" Different feed. Inserting transfer with " + (int) (label.streetTime / 1000L) + " s.");
                                    insertInterpolatedTransfer(label, toPlatformDescriptor, readers);
                                } else {
                                    List<Transfer> transfersToStop = transfers.getTransfersToStop(toPlatformDescriptor.stop_id, routeIdOrNull(toPlatformDescriptor));
                                    if (transfersToStop.stream().noneMatch(t -> t.from_stop_id.equals(fromPlatformDescriptor.stop_id))) {
                                        LOGGER.debug("  Inserting transfer with " + (int) (label.streetTime / 1000L) + " s.");
                                        insertInterpolatedTransfer(label, toPlatformDescriptor, readers);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        });
    }

    private void insertInterpolatedTransfer(Label label, GtfsStorage.PlatformDescriptor toPlatformDescriptor, HashMap<String, GtfsReader> readers) {
        GtfsReader toFeedReader = readers.get(toPlatformDescriptor.feed_id);
        List<Integer> transferEdgeIds = toFeedReader.insertTransferEdges(label.node.ptNode, (int) (label.streetTime / 1000L), toPlatformDescriptor);
        List<Label.Transition> transitions = Label.getTransitions(label.parent, true);
        int[] skippedEdgesForTransfer = transitions.stream().filter(t -> t.edge != null).mapToInt(t -> {
            Label.NodeId adjNode = t.label.node;
            EdgeIteratorState edgeIteratorState = getBaseGraph().getEdgeIteratorState(t.edge.getId(), adjNode.streetNode);
            return edgeIteratorState.getEdgeKey();
        }).toArray();
        if (skippedEdgesForTransfer.length > 0) { // TODO: Elsewhere, we distinguish empty path ("at" a node) from no path
            assert isValidPath(skippedEdgesForTransfer);
            for (Integer transferEdgeId : transferEdgeIds) {
                gtfsStorage.getSkippedEdgesForTransfer().put(transferEdgeId, skippedEdgesForTransfer);
            }
        }
    }

    private boolean isValidPath(int[] edgeKeys) {
        List<EdgeIteratorState> edges = Arrays.stream(edgeKeys).mapToObj(i -> getBaseGraph().getEdgeIteratorStateForKey(i)).collect(Collectors.toList());
        for (int i = 1; i < edges.size(); i++) {
            if (edges.get(i).getBaseNode() != edges.get(i-1).getAdjNode())
                return false;
        }
        return true;
    }

    private String routeIdOrNull(GtfsStorage.PlatformDescriptor platformDescriptor) {
        if (platformDescriptor instanceof GtfsStorage.RouteTypePlatform) {
            return null;
        } else {
            return ((GtfsStorage.RoutePlatform) platformDescriptor).route_id;
        }
    }

    @Override
    public void close() {
        getGtfsStorage().close();
        super.close();
    }

    public GtfsStorage getGtfsStorage() {
        return gtfsStorage;
    }

    public PtGraph getPtGraph() {
        return ptGraph;
    }
}
