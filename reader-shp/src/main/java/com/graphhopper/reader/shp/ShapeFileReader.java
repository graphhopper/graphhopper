package com.graphhopper.reader.shp;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.FeatureSource;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;

import com.graphhopper.reader.DataReader;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.storage.NodeAccess;
import com.vividsolutions.jts.geom.Coordinate;

/**
 * ShapeFileReader takes care of reading a shape file and writing it to a road network graph
 *
 * @author Vikas Veshishth
 */
public abstract class ShapeFileReader implements DataReader {

	private final GraphStorage graphStorage;
	private final NodeAccess nodeAccess;
	protected final Graph graph;
	protected EncodingManager encodingManager;

	public ShapeFileReader(GraphHopperStorage ghStorage) {
		this.graphStorage = ghStorage;
		this.graph = ghStorage;
		this.nodeAccess = graph.getNodeAccess();
	}

	@Override
	public void readGraph() throws IOException {
		// TODO why 1000?
		graphStorage.create(1000);
		processJunctions();
		processRoads();
	}

	abstract void processJunctions() throws IOException;

	abstract void processRoads() throws IOException;

	protected FeatureIterator<SimpleFeature> getFeatureIerator(DataStore dataStore) throws IOException {
		String typeName = dataStore.getTypeNames()[0];
		FeatureSource<SimpleFeatureType, SimpleFeature> source = dataStore.getFeatureSource(typeName);
		Filter filter = Filter.INCLUDE;
		FeatureCollection<SimpleFeatureType, SimpleFeature> collection = source.getFeatures(filter);

		FeatureIterator<SimpleFeature> features = collection.features();
		return features;
	}

	protected DataStore openShapefileDataStore(File file) throws IOException {
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("url", file.toURI().toURL());
		return DataStoreFinder.getDataStore(map);
	}

	/*
	 * Get longitude using the current long-lat order convention
	 */
	protected double lng(Coordinate coordinate) {
		return coordinate.getOrdinate(0);
	}

	/*
	 * Get latitude using the current long-lat order convention
	 */
	protected double lat(Coordinate coordinate) {
		return coordinate.getOrdinate(1);
	}

	protected void saveTowerPosition(int nodeId, Coordinate point) {
		nodeAccess.setNode(nodeId, lat(point), lng(point));
	}

	@Override
	public ShapeFileReader setEncodingManager(EncodingManager encodingManager) {
		this.encodingManager = encodingManager;
		return this;
	}
}
