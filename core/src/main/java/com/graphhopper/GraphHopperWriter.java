package com.graphhopper;

import com.graphhopper.reader.DataReader;
import com.graphhopper.routing.subnetwork.PrepareRoutingSubnetworks;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.CHProfile;
import com.graphhopper.storage.GHDirectory;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.util.GHUtility;

import java.io.File;
import java.io.IOException;

public final class GraphHopperWriter {
    private GraphState state = GraphState.INIT;
    private final EncodingManager encodingManager;
    private final CHPreparationHandler chPreparationHandler;
    private GraphHopperStorage graphHopperStorage;
    private DataReader dataReader;
    private LocationIndexTree locationIndex;
    private final GraphConfig graphConfig;
    // TODO same for LM

    public GraphHopperWriter(EncodingManager encodingManager, GraphConfig graphConfig) {
        this.encodingManager = encodingManager;
        this.graphConfig = graphConfig;

        GHDirectory dir = new GHDirectory(graphConfig.getGraphCacheFolder(), graphConfig.getDAType());
        graphHopperStorage = new GraphHopperStorage(dir, encodingManager, graphConfig.hasElevation(),
                encodingManager.needsTurnCostsSupport(), graphConfig.getDefaultSegmentSize());
        this.chPreparationHandler = new CHPreparationHandler(graphHopperStorage);
    }

    public EncodingManager getEncodingManager() {
        return encodingManager;
    }

    public CHPreparationHandler getCHPreparationHandler() {
        return chPreparationHandler;
    }

    public GraphHopperWriter setDataReader(DataReader dataReader) {
        if (state != GraphState.INIT)
            throw new IllegalStateException("wrong state: " + state);
        if (this.dataReader != null)
            throw new IllegalStateException("cannot overwrite dataReader");
        this.dataReader = dataReader;
        return this;
    }

    public GraphHopperStorage getGraphHopperStorage() {
        return graphHopperStorage;
    }

    public GraphHopperWriter readData(ReadDataConfig readDataConfig) {
        if (state != GraphState.INIT)
            throw new IllegalStateException("wrong state: " + state);
        if (dataReader == null)
            throw new IllegalStateException("call setDataReader before");
        try {
            dataReader.setFile(new File(readDataConfig.getDataReaderFile()));
            dataReader.readGraph();
            dataReader = null;
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        if (readDataConfig.isCleanUpEnabled()) {
            PrepareRoutingSubnetworks preparation = new PrepareRoutingSubnetworks(graphHopperStorage, encodingManager.fetchEdgeEncoders());
            preparation.setMinNetworkSize(readDataConfig.getMinNetworkSize());
            preparation.setMinOneWayNetworkSize(readDataConfig.getMinOneWayNetworkSize());
            preparation.doWork();
        }

        if (readDataConfig.isSortingEnabled()) {
            GraphHopperStorage newGraph = GHUtility.newStorage(graphHopperStorage);
            GHUtility.sortDFS(graphHopperStorage, newGraph);
            // logger.info("graph sorted (" + getMemInfo() + ")");
            graphHopperStorage = newGraph;
        }

        // NOTE: this is different to the current processing order, but required now
        graphHopperStorage.flush();

        if (graphConfig.hasElevation())
            interpolateBridgesAndOrTunnels();

        state = GraphState.IMPORTED;
        return this;
    }

    public GraphHopperWriter createIndex() {
        if (state != GraphState.IMPORTED)
            throw new IllegalStateException("wrong state: " + state);
        locationIndex = new LocationIndexTree(graphHopperStorage, graphHopperStorage.getDirectory());
        locationIndex.prepareIndex();
        state = GraphState.LOC_INDEXED;
        return this;
    }

    private void interpolateBridgesAndOrTunnels() {
        // TODO NOW
    }

    /**
     * This method adds the specified CHProfile to the task queue. This means that as soon as a configuration of
     * a CHProfile is known we create it.
     */
    public GraphHopperWriter doAsyncPreparation(CHProfile chProfile, CHProfileConfig config) {
        if (state != GraphState.LOC_INDEXED)
            throw new IllegalStateException("Wrong state: " + state + ". Did you call readData and createIndex before?");

        graphHopperStorage.freeze();
        state = GraphState.FROZEN;

        chPreparationHandler.doPreparation(chProfile, config);
        return this;
    }

    public void waitForAsyncPreparations() {
        chPreparationHandler.waitForCompletion();
    }

    // TODO
    // public GraphHopperImport addPreparation(LMProfile profile)

    public LocationIndex getLocationIndex() {
        if (state == GraphState.INIT)
            throw new IllegalStateException("wrong state: " + state);
        return locationIndex;
    }

    private enum GraphState {
        INIT, IMPORTED, LOC_INDEXED, FROZEN
    }
}
