package com.graphhopper.reader;

import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.*;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PointList;
import com.vividsolutions.jts.geom.Coordinate;
import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.FeatureSource;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;


/**
 * ShapeFileReader takes care of reading a shape file and
 * writing it to a road network graph
 *
 * @author Vikas Veshishth
 * */
public abstract class ShapeFileReader implements DataReader {

    protected String shapeFileDir;

    private final GraphStorage graphStorage;
    private final Graph graph;
    private final NodeAccess nodeAccess;
    private EncodingManager encodingManager;

    private static final Logger logger = LoggerFactory.getLogger(ShapeFileReader.class);

    public ShapeFileReader(GraphHopperStorage ghStorage)
    {
        this.graphStorage = ghStorage;
        this.graph = ghStorage;
        this.nodeAccess = graph.getNodeAccess();
    }

    @Override
    public void readGraph() throws IOException {
        graphStorage.create(1000);
        processJunctions();
        processRoads();
    }


    abstract void processJunctions() throws IOException;

    abstract void processRoads() throws IOException;

    protected void addEdge(int fromTower, int toTower, double speed, boolean bothWay, double distance, PointList pillarNodes){
        EdgeIteratorState edge =  graph.edge(fromTower, toTower);
        FlagEncoder encoder = encodingManager.getEncoder("CAR");
        edge.setDistance(distance);
        edge.setFlags( encoder.setProperties(speed, true, bothWay) );
        edge.setWayGeometry(pillarNodes);
    }

    /*
    Basic logic to read any shape file
     */
    protected FeatureIterator<SimpleFeature> readShape(DataStore dataStore) throws IOException{
        String typeName = dataStore.getTypeNames()[0];
        FeatureSource<SimpleFeatureType, SimpleFeature> source = dataStore.getFeatureSource(typeName);
        Filter filter = Filter.INCLUDE;
        FeatureCollection<SimpleFeatureType, SimpleFeature> collection = source.getFeatures(filter);

        FeatureIterator<SimpleFeature> features = collection.features();
        return features;
    }

    protected DataStore createDataStore(String fileName) throws IOException{
        File file = new File(fileName);

        Map<String, Object> map = new HashMap<String, Object>();
        map.put("url", file.toURI().toURL());

        return DataStoreFinder.getDataStore(map);
    }

    protected void saveTowerPosition(int nodeId, Coordinate point){
        double lat = point.getOrdinate(1);
        double lng = point.getOrdinate(0);
        nodeAccess.setNode(nodeId, lat, lng);
    }

    public ShapeFileReader setShapeFileDir(String shapeFileDir) {
        this.shapeFileDir = shapeFileDir;
        return this;
    }

    public ShapeFileReader setEncodingManager(EncodingManager encodingManager) {
        this.encodingManager = encodingManager;
        return this;
    }
}
