package com.graphhopper.reader.gtfs;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.reader.DataReader;
import com.graphhopper.routing.AlgorithmOptions;
import com.graphhopper.routing.RoutingAlgorithm;
import com.graphhopper.routing.RoutingAlgorithmFactory;
import com.graphhopper.routing.template.RoutingTemplate;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphExtension;
import com.graphhopper.storage.GraphHopperStorage;

import java.util.Arrays;

public final class GraphHopperGtfs extends GraphHopper {

    public static final String EARLIEST_DEPARTURE_TIME_HINT = "earliestDepartureTime";
    public static final String DEFAULT_MINIMUM_TRANSFER_TIME_HINT = "defaultMinimumTransferTime";

    private boolean createWalkNetwork = false;

    public GraphHopperGtfs() {
        super();
        super.setCHEnabled(false);
        super.setEncodingManager(new EncodingManager(Arrays.asList(new PtFlagEncoder()), 8));
    }

    @Override
    protected DataReader createReader(GraphHopperStorage ghStorage) {
        return initDataReader(new GtfsReader(ghStorage, createWalkNetwork));
    }

    public GraphHopperGtfs setGtfsFile(String gtfs) {
        super.setDataReaderFile(gtfs);
        return this;
    }

    @Override
    public Weighting createWeighting(HintsMap weightingMap, FlagEncoder encoder) {
        return new PtTravelTimeWeighting(encoder);
    }

    @Override
    public RoutingAlgorithmFactory getAlgorithmFactory(HintsMap map) {
        return new RoutingAlgorithmFactory() {
            @Override
            public RoutingAlgorithm createAlgo(Graph g, AlgorithmOptions opts) {
                return new MultiCriteriaLabelSetting(g, opts.getWeighting(), opts.getMaxVisitedNodes(), (GtfsStorage) getGraphHopperStorage().getExtension());
            }
        };
    }

    @Override
    protected RoutingTemplate createRoutingTemplate(String algoStr, GHRequest request, GHResponse ghRsp) {
        return new PtRoutingTemplate(request, ghRsp, getLocationIndex());
    }

    @Override
    protected void cleanUp() {
    }

    @Override
    public GraphHopper setEncodingManager(EncodingManager em) {
        throw new UnsupportedOperationException();
    }

    @Override
    public GraphHopper setCHEnabled(boolean enable) {
        throw new UnsupportedOperationException();
    }

    public void setCreateWalkNetwork(boolean createWalkNetwork) {
        this.createWalkNetwork = createWalkNetwork;
    }

    @Override
    protected GraphExtension createGraphExtension() {
        return new GtfsStorage();
    }
}
