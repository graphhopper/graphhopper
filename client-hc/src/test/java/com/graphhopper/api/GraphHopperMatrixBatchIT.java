package com.graphhopper.api;

/**
 * @author Peter Karich
 */
public class GraphHopperMatrixBatchIT extends AbstractGraphHopperMatrixWebIntegrationTester {

    @Override
    GraphHopperMatrixWeb createMatrixWeb() {
        return new GraphHopperMatrixWeb(new GHMatrixBatchRequester());
    }
}
