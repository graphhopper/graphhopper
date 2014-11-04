package uk.co.ordnancesurvey.gpx.graphhopper;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashSet;

import org.alternativevision.gpx.GPXParser;
import org.alternativevision.gpx.beans.GPX;
import org.alternativevision.gpx.beans.Route;
import org.alternativevision.gpx.beans.Track;
import org.alternativevision.gpx.beans.Waypoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.ordnancesurvey.gpx.beans.RouteWayPoint;
import uk.co.ordnancesurvey.gpx.extensions.RoutePointAzimuthParser;
import uk.co.ordnancesurvey.gpx.extensions.RoutePointDirectionParser;
import uk.co.ordnancesurvey.gpx.extensions.RoutePointDistanceParser;
import uk.co.ordnancesurvey.gpx.extensions.RoutePointTimeParser;

public class GraphHopperGPXParserRouteTest {

	private static final Logger LOG = LoggerFactory
			.getLogger(GraphHopperGPXParserRouteTest.class);

	private FileOutputStream gpxFileOutputStream;
	private GPX gpx;

	public GraphHopperGPXParserRouteTest() {
		init(null);
	}

	public GraphHopperGPXParserRouteTest(String gpxFileName) {
		init(gpxFileName);
	}

	public HashSet<Route> getRoutes() {
		return gpx.getRoutes();
	}

	public boolean isWayPointOnRoute(Waypoint aWayPoint, Route aRoute) {

		boolean isWayPointOnRoute = false;

		for (Waypoint aWaypointInaRoute : aRoute.getRoutePoints()) {
			if (new RouteWayPoint(aWaypointInaRoute).equals(new RouteWayPoint(
					aWayPoint))) {
				isWayPointOnRoute = true;
				LOG.info("WayPoint " + aWayPoint + " Found In a Route" + aRoute);
				break;
			}
		}

		return isWayPointOnRoute;
	}

	public boolean routeContainsTurn(String turnDescription, Route aRoute) {

		boolean routeContainsTurn = false;

		for (Waypoint aWaypointInaRoute : aRoute.getRoutePoints()) {

			if (aWaypointInaRoute.getDescription() != null
					&& aWaypointInaRoute.getDescription().equals(
							turnDescription)) {
				routeContainsTurn = true;
				LOG.info("WayPoint " + aWaypointInaRoute + " contains turn"
						+ turnDescription);
				break;
			}
		}

		return routeContainsTurn;
	}
	
	public long getTotalRouteTime(){
		
		long totalTimeInSceonds = 0;
		HashSet<Track> tracks = gpx.getTracks();
		Track track= (Track)tracks.toArray()[0];
		ArrayList<Waypoint> trackPoints = track.getTrackPoints();
		
		if (trackPoints.size() > 0) {
			Waypoint sttp = trackPoints.get(0);
			Waypoint endtp = trackPoints.get(trackPoints.size()-1);
			LOG.info("Start Time is " +sttp.getTime().toString());
			LOG.info("End Time is " + endtp.getTime().toString());
			totalTimeInSceonds = endtp.getTime().getTime() - sttp.getTime().getTime();
		}
		
		return totalTimeInSceonds;
	}

	private void init(String gpxFileName) {

		GPXParser gpxParser = new GPXParser();
		RoutePointDistanceParser rPDEP = new RoutePointDistanceParser();
		RoutePointTimeParser rPTEP = new RoutePointTimeParser();
		RoutePointAzimuthParser rPAEP = new RoutePointAzimuthParser();
		RoutePointDirectionParser rPDIEP = new RoutePointDirectionParser();
		gpxParser.addExtensionParser(rPDEP);
		gpxParser.addExtensionParser(rPTEP);
		gpxParser.addExtensionParser(rPAEP);
		gpxParser.addExtensionParser(rPDIEP);

		if (gpxFileName != null && gpxFileName.length() > 0) {
			try {
				gpx = gpxParser.parseGPX(new FileInputStream(gpxFileName));
				/*
				 * for (Route aRoute : gpx.getRoutes()) { for (Waypoint
				 * aWayPoint : aRoute.getRoutePoints()) {
				 * LOG.info("A RoutePoint with data " //+ aWayPoint +
				 * " Direction=" +
				 * aWayPoint.getExtensionData("routePointDirectionExtension") +
				 * " Time=" +
				 * aWayPoint.getExtensionData("routePointTimeExtension") +
				 * " Distance=" +
				 * aWayPoint.getExtensionData("routePointDistanceExtension") +
				 * " Azimuth=" +
				 * aWayPoint.getExtensionData("routePointAzimuthExtension"));
				 * 
				 * } }
				 */} catch (Exception e) {
				LOG.info("Invalid File supplied for parsing " + e.getMessage());
			}
		}
	}
}
