package com.graphhopper.reader.shp;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.geotools.data.DataStore;
import org.geotools.feature.FeatureIterator;
import org.opengis.feature.simple.SimpleFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.graphhopper.reader.DataReader;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.reader.dem.ElevationProvider;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPoint;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.MultiLineString;

import gnu.trove.map.hash.THashMap;

/**
 * OSMShapeFileReader for files present at : http://download.geofabrik.de/ It extracts the data as per the structure of
 * shape files
 *
 * @author Vikas Veshishth
 */
public class OSMShapeFileReader extends ShapeFileReader {
	private static final String[] DIRECT_COPY_TAGS = new String[] { "name" };
	private File roadsFile;
	private THashMap<Coordinate, Integer> towerCoordIdMap = new THashMap<Coordinate, Integer>();
	private final DistanceCalc distCalc = Helper.DIST_EARTH;
	private static final Logger LOGGER = LoggerFactory.getLogger(OSMShapeFileReader.class);

	public OSMShapeFileReader(GraphHopperStorage ghStorage) {
		super(ghStorage);
	}

	@Override
	void processJunctions() throws IOException {
		{
			DataStore dataStore = null;
			FeatureIterator<SimpleFeature> roads = null;

			try {
				dataStore = openShapefileDataStore(roadsFile);
				roads = getFeatureIerator(dataStore);

				while (roads.hasNext()) {
					SimpleFeature road = roads.next();

					MultiLineString geom = (MultiLineString) road.getDefaultGeometry();
					if (geom.getNumGeometries() != 1) {
						// TODO is this an error? Are roads split if they're too long?
						// Will getting all coordinates as below still lead sensible results,
						// providing the individual linestrings are digitised in a sensible order?
						throw new RuntimeException("Found road with more than one geometry, OSM id " + getOSMId(road));
					}
					
					Coordinate[] points = geom.getCoordinates();

					Coordinate start = points[0];
					Coordinate end = points[points.length - 1];

					checkAndAdd(start);
					checkAndAdd(end);

				}
			} finally {
				if (roads != null) {
					roads.close();
				}
				if (dataStore != null) {
					dataStore.dispose();
				}
			}

			LOGGER.info("Number of junction points : " + towerCoordIdMap.size());

		}
	}

	@Override
	void processRoads() throws IOException {

		DataStore dataStore = null;
		FeatureIterator<SimpleFeature> roads = null;

		try {
			dataStore = openShapefileDataStore(roadsFile);
			roads = getFeatureIerator(dataStore);

			while (roads.hasNext()) {
				SimpleFeature road = roads.next();

				MultiLineString mls = (MultiLineString) road.getDefaultGeometry();

				Coordinate[] points = mls.getCoordinates();

				// Parse all points in the geometry, splitting into individual graphhopper edges
				// whenever we find a node that's a start or end node elsewhere (can happen commonly
				// with main roads that have several side roads coming off them).
				Coordinate startTower = null;
				List<Coordinate> pillars = new ArrayList<Coordinate>();
				for (Coordinate point : points) {
					if (startTower == null) {
						startTower = point;
					} else {
						if (towerCoordIdMap.containsKey(point)) {
							int fromTower = towerCoordIdMap.get(startTower);
							int toTower = towerCoordIdMap.get(point);

							// get distance and estimated centres
							double distance = getWayLength(startTower, pillars, point);
							GHPoint estmCentre = new GHPoint(0.5 * (lat(startTower) + lat(point)), 0.5 * (lng(startTower) + lng(point)));
							PointList pillarNodes = new PointList(pillars.size(), false);

							for (Coordinate pillar : pillars) {
								pillarNodes.add(lat(pillar), lng(pillar));
							}

							addEdge(fromTower, toTower, road, distance, estmCentre,pillarNodes);
							startTower = point;
							pillars.clear();
						} else {
							pillars.add(point);
						}
					}
				}

			}
		} catch (Exception e) {
			LOGGER.error("Exception while processing for roads: ", e);
		} finally {
			if (roads != null) {
				roads.close();
			}

			if (dataStore != null) {
				dataStore.dispose();
			}
		}
	}

	private double getWayLength(Coordinate start, List<Coordinate> pillars, Coordinate end) {
		double distance = 0;

		Coordinate previous = start;
		for (Coordinate point : pillars) {
			distance += distCalc.calcDist(lat(previous), lng(previous), lat(point), lng(point));
			previous = point;
		}
		distance += distCalc.calcDist(lat(previous), lng(previous), lat(end), lng(end));

		return distance;
	}


	private void checkAndAdd(Coordinate point) {
		if (!towerCoordIdMap.containsKey(point)) {
			int nodeId = towerCoordIdMap.size();
			towerCoordIdMap.put(point, nodeId);
			saveTowerPosition(nodeId, point);
		}
	}

	@Override
	public DataReader setFile(File file) {
		this.roadsFile = file;
		return this;
	}

	@Override
	public DataReader setElevationProvider(ElevationProvider ep) {
		// Elevation not supported
		return this;
	}

	@Override
	public DataReader setWorkerThreads(int workerThreads) {
		// Its only single-threaded
		return this;
	}

	@Override
	public DataReader setWayPointMaxDistance(double wayPointMaxDistance) {
		// TODO Auto-generated method stub
		return this;
	}

	@Override
	public Date getDataDate() {
		return null;
	}

	private void addEdge(int fromTower, int toTower, SimpleFeature road, double distance,GHPoint estmCentre , PointList pillarNodes) {
		EdgeIteratorState edge = graph.edge(fromTower, toTower);

		// read the OSM id, should never be null
		long id = getOSMId(road);

		// Make a temporary ReaderWay object with the properties we need so we can use the enocding manager
		// We (hopefully don't need the node structure on here as we're only calling the flag
		// encoders, which don't use this...
		ReaderWay way = new ReaderWay(id);

		way.setTag("estimated_distance", distance);
		way.setTag("estimated_center", estmCentre);

		// read the highway type
		Object type = road.getAttribute("fclass");
		if (type != null) {
			way.setTag("highway", type.toString());
		}
		
		// read maxspeed filtering for 0 which for Geofabrik shapefiles appears to correspond to no tag
		Object maxSpeed = road.getAttribute("maxspeed");
		if(maxSpeed!=null && !maxSpeed.toString().trim().equals("0")){
			way.setTag("maxspeed", maxSpeed.toString());
		}

		for (String tag : DIRECT_COPY_TAGS) {
			Object val = road.getAttribute(tag);
			if (val != null) {
				way.setTag(tag, val.toString());
			}
		}

		// read oneway
		Object oneway = road.getAttribute("oneway");
		if (oneway != null) {
			// Geofabrik is using an odd convention for oneway field in shapefile.
			// We map back to the standard convention so that tag can be dealt with correctly by the flag encoder.
			String val = oneway.toString().trim().toLowerCase();
			if(val.equals("b")){
				// both ways
				val = "no";
			}else if (val.equals("t")){
				// one way against the direction of digitisation
				val = "-1";
			}
			else if (val.equals("f")){
				// one way Forward in the direction of digitisation
				val = "yes";
			}
			else{
				throw new RuntimeException("Unrecognised value of oneway field \"" + val + "\" found in road with OSM id " + id);
			}
			
			way.setTag("oneway", val);
		}

		// Process the flags using the encoders
		long includeWay = encodingManager.acceptWay(way);
		if (includeWay == 0) {
			return;
		}

		// TODO we're not using the relation flags
		long relationFlags = 0;

		long wayFlags = encodingManager.handleWayTags(way, includeWay, relationFlags);
		if (wayFlags == 0)
			return;

		edge.setDistance(distance);
		edge.setFlags(wayFlags);
		edge.setWayGeometry(pillarNodes);
	}

	private long getOSMId(SimpleFeature road) {
		long id = Long.parseLong(road.getAttribute("osm_id").toString());
		return id;
	}

}
