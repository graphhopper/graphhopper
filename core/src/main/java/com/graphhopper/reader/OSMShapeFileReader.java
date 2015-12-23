package com.graphhopper.reader;

import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PointList;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.MultiLineString;
import gnu.trove.map.hash.THashMap;
import org.geotools.data.DataStore;
import org.geotools.feature.FeatureIterator;
import org.opengis.feature.simple.SimpleFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * OSMShapeFileReader for files present at : http://download.geofabrik.de/
 * It extracts the data as per the structure of shape files
 *
 * @author Vikas Veshishth
 */
public class OSMShapeFileReader extends ShapeFileReader {

    private THashMap<Coordinate, Integer> towerCoordIdMap = new THashMap<Coordinate, Integer>();
    private final DistanceCalc distCalc = Helper.DIST_EARTH;

    private static final Logger logger = LoggerFactory.getLogger(OSMShapeFileReader.class);
    private static final double AVG_SPEED = 10;

    public OSMShapeFileReader(GraphHopperStorage ghStorage) {
        super(ghStorage);
    }

    @Override
    void processJunctions() throws IOException {
        {

            DataStore dataStore = createDataStore(getRoadFile());
            FeatureIterator<SimpleFeature> roads = readShape( dataStore );

            try {

                while ( roads.hasNext() ){
                    SimpleFeature road = roads.next();

                    MultiLineString geom = (MultiLineString)road.getDefaultGeometry();
                    Coordinate[] points = geom.getCoordinates();

                    Coordinate start = points[0];
                    Coordinate end = points[points.length - 1];

                    checkAndAdd(start);
                    checkAndAdd(end);

                }
            }finally {
                roads.close();
                dataStore.dispose();
            }

            logger.info("Number of junction points : " + towerCoordIdMap.size());

        }
    }

    @Override
    void processRoads() throws IOException {

        DataStore dataStore = createDataStore(getRoadFile());
        FeatureIterator<SimpleFeature> roads = readShape( dataStore );

        try {

            while (roads.hasNext()) {
                SimpleFeature road = roads.next();

                Coordinate[] points = ((MultiLineString) road.getDefaultGeometry()).getCoordinates();

                Object speedStr = road.getAttribute("maxspeed");
                double speed = speedStr != null ? Double.parseDouble(speedStr.toString()) : 0.0;
                if (speed == 0.0){
                    speed = AVG_SPEED;
                }

                boolean bothWay = road.getAttribute("oneway").toString().trim().equalsIgnoreCase("0");

                Coordinate startTower = null;
                List<Coordinate> pillars = new ArrayList<Coordinate>();
                for (Coordinate point : points){
                    if (startTower == null){
                        startTower = point;
                    }
                    else{
                        if ( towerCoordIdMap.containsKey(point) ){
                            int fromTower = towerCoordIdMap.get(startTower);
                            int toTower = towerCoordIdMap.get(point);
                            int distance = getWayLength(startTower, pillars, point);

                            PointList pillarNodes = new PointList(pillars.size(), false);

                            for (Coordinate pillar : pillars){
                                double lat = pillar.getOrdinate(1);
                                double lng = pillar.getOrdinate(0);
                                pillarNodes.add(lat, lng);
                            }

                            addEdge(fromTower, toTower, speed, bothWay, distance, pillarNodes);
                            startTower = point;
                            pillars.clear();
                        }else{
                            pillars.add(point);
                        }
                    }
                }

            }
        } catch (Exception e ){
            logger.error("Exception while processing for roads: " ,e);
        }finally {
            roads.close();
            dataStore.dispose();
        }
    }

    private int getWayLength(Coordinate start, List<Coordinate> pillars, Coordinate end){
        int distance = 0;

        Coordinate previous = start;
        for (Coordinate point : pillars){
            distance += distCalc.calcDist(previous.getOrdinate(1), previous.getOrdinate(0),
                    point.getOrdinate(1), point.getOrdinate(0));
            previous = point;
        }
        distance += distCalc.calcDist(previous.getOrdinate(1), previous.getOrdinate(0),
                end.getOrdinate(1), end.getOrdinate(0));

        return distance;
    }

    private void checkAndAdd(Coordinate point){
        if (!towerCoordIdMap.containsKey(point)){
            int nodeId = towerCoordIdMap.size();
            towerCoordIdMap.put(point, nodeId);
            saveTowerPosition(nodeId, point);
        }
    }

    public String getRoadFile(){
        return shapeFileDir + "/" + "roads.shp";
    }

    private String coordinateKey(Coordinate point){
        NumberFormat formatter = new DecimalFormat("###.000000");
        return formatter.format(point.y) + "-" + formatter.format(point.x);
    }

}
