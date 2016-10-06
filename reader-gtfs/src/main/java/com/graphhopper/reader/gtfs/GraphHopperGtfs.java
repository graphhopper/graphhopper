package com.graphhopper.reader.gtfs;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.reader.DataReader;
import com.graphhopper.routing.AlgorithmOptions;
import com.graphhopper.routing.Dijkstra;
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

public final class GraphHopperGtfs extends GraphHopper {

	public static final String EARLIEST_DEPARTURE_TIME_HINT = "earliestDepartureTime";

	public GraphHopperGtfs() {
		super();
		super.setCHEnabled(false);
		super.setEncodingManager(new EncodingManager(new PtFlagEncoder()));
	}

	@Override
	protected DataReader createReader(GraphHopperStorage ghStorage) {
		return initDataReader(new GtfsReader(ghStorage));
	}

	public GraphHopperGtfs setGtfsFile(String gtfs) {
		super.setDataReaderFile(gtfs);
		return this;
	}

	@Override
	public Weighting createWeighting(HintsMap weightingMap, FlagEncoder encoder) {
		return new PtTravelTimeWeighting(encoder, (GtfsStorage) getGraphHopperStorage().getExtension());
	}

	@Override
	public RoutingAlgorithmFactory getAlgorithmFactory(HintsMap map) {
		return new RoutingAlgorithmFactory() {
			@Override
			public RoutingAlgorithm createAlgo(Graph g, AlgorithmOptions opts) {
				Dijkstra ra = new Dijkstra(g, opts.getFlagEncoder(), opts.getWeighting(), opts.getTraversalMode());
				ra.setMaxVisitedNodes(opts.getMaxVisitedNodes());
				return ra;
			}
		};
	}

	@Override
	protected RoutingTemplate createRoutingTemplate(String algoStr, GHRequest request, GHResponse ghRsp) {
		return new PtRoutingTemplate(request, ghRsp, getLocationIndex(), (GtfsStorage) getGraphHopperStorage().getExtension());
	}

	@Override
	public GraphHopper setEncodingManager(EncodingManager em) {
		throw new UnsupportedOperationException();
	}

	@Override
	public GraphHopper setCHEnabled(boolean enable) {
		throw new UnsupportedOperationException();
	}

	@Override
	protected GraphExtension createGraphExtension() {
		return new GtfsStorage();
	}
}
