package com.graphhopper.api;

/**
 * @author Peter Karich
 */
public class GraphHopperMatrixSyncIT extends AbstractGraphHopperMatrixWebIntegrationTester {

    @Override
    GraphHopperMatrixWeb createMatrixWeb() {
        return new GraphHopperMatrixWeb(new GHMatrixSyncRequester());
    }
}
