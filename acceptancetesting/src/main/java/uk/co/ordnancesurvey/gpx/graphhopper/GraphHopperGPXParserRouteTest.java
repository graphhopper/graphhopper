package uk.co.ordnancesurvey.gpx.graphhopper;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;

import org.alternativevision.gpx.GPXParser;
import org.alternativevision.gpx.beans.GPX;
import org.alternativevision.gpx.beans.Route;
import org.alternativevision.gpx.beans.Track;
import org.alternativevision.gpx.beans.Waypoint;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
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

	private GPXParser gpxParser;
	private GPX gpx;
	private final Map<String, String> customHeaders = new HashMap<>();

	public static GraphHopperGPXParserRouteTest getParserForgpxString(
			String gpxString) {

		GraphHopperGPXParserRouteTest instance = new GraphHopperGPXParserRouteTest();
		instance.parseGPXFromString(gpxString);

		return instance;
	}

	public static GraphHopperGPXParserRouteTest getParserForgpxFileName(
			String gpxFileName) {
		GraphHopperGPXParserRouteTest instance = new GraphHopperGPXParserRouteTest();
		instance.parseGPXFromFile(gpxFileName);

		return instance;
	}

	public GraphHopperGPXParserRouteTest() {
		init();
	}

	private void parseGPXFromString(String gpxString) {
		if (gpxString != null && gpxString.length() > 0) {
			try {
				gpx = gpxParser.parseGPX(new ByteArrayInputStream(gpxString
						.getBytes()));
			} catch (Exception e) {
				LOG.info("Invalid File supplied for parsing " + e.getMessage());
			}
		}
	}

	private void parseGPXFromFile(String gpxFileName) {
		if (gpxFileName != null && gpxFileName.length() > 0) {
			try {
				gpx = gpxParser.parseGPX(new FileInputStream(gpxFileName));

			} catch (Exception e) {
				LOG.info("Invalid File supplied for parsing " + e.getMessage());
			}
		}
	}

	private void addCustomHeaders(HttpGet httpget) {
		for (Entry<String, String> header : customHeaders.entrySet()) {
			httpget.addHeader(header.getKey(), header.getValue());
		}
	}

	CloseableHttpResponse doSendAndGetResponse(String serviceUrl)
			throws IOException, ClientProtocolException {
		CloseableHttpClient httpClient = HttpClientUtils.createClient();
		HttpGet httpget = new HttpGet(serviceUrl);
		addCustomHeaders(httpget);

		return httpClient.execute(httpget);
	}

	public String parseRoute(String routeType, String avoidance,String vehicle, String[] points) {
		LOG.debug("Here we are");
		// Set up the URL
		String xmlResponse = "";
		String coordinateString = "";
		String graphHopperUrl;

		for (int i = 0; i < points.length; i++) {

			coordinateString = coordinateString + "&point=" + points[i];

		}

		
		if (IntegrationTestProperties.getTestPropertyBool("viaApigee"))
		{
			graphHopperUrl = IntegrationTestProperties.getTestProperty("graphHopperWebUrlViaApigee");
		}
		else
		{
			graphHopperUrl = IntegrationTestProperties.getTestProperty("graphHopperWebUrl");
		}
		
				

		StringBuilder sb = new StringBuilder();
		sb.append(graphHopperUrl);
		sb.append("route?");
		if (routeType != null) {
			sb.append("type=");
			sb.append(routeType);
		}
		sb.append("&vehicle=");
		sb.append(vehicle);
		sb.append(coordinateString);
		if(!avoidance.equals(""))
		{
		sb.append("&weighting=fastavoid");
		sb.append("&avoidances="+avoidance);
		}

		try {
			CloseableHttpResponse httpResponse = sendAndGetResponse(sb
					.toString());
			xmlResponse = IOUtils.toString(httpResponse.getEntity()
					.getContent(), "UTF-8");

		} catch (IOException e) {
			LOG.info("Exception raised whilst attempting to call graphhopper server "
					+ e.getMessage());
		}

		if (xmlResponse != null && xmlResponse.length() > 0) {
			parseGPXFromString(xmlResponse);
		}

		return xmlResponse;
	}

	public HashSet<Route> getRoutes() {
		return gpx.getRoutes();
	}

	public HashSet<Track> getTracks() {
		return gpx.getTracks();
	}

	public long getTotalRouteTime() {

		long totalTimeInSceonds = 0;
		HashSet<Track> tracks = gpx.getTracks();
		Track track = (Track) tracks.toArray()[0];
		ArrayList<Waypoint> trackPoints = track.getTrackPoints();

		if (trackPoints.size() > 0) {
			Waypoint sttp = trackPoints.get(0);
			Waypoint endtp = trackPoints.get(trackPoints.size() - 1);
			LOG.info("Start Time is " + sttp.getTime().toString());
			LOG.info("End Time is " + endtp.getTime().toString());
			totalTimeInSceonds = endtp.getTime().getTime()
					- sttp.getTime().getTime();
		}

		return totalTimeInSceonds;
	}

	private void init() {

		gpxParser = new GPXParser();
		RoutePointDistanceParser rPDEP = new RoutePointDistanceParser();
		RoutePointTimeParser rPTEP = new RoutePointTimeParser();
		RoutePointAzimuthParser rPAEP = new RoutePointAzimuthParser();
		RoutePointDirectionParser rPDIEP = new RoutePointDirectionParser();
		gpxParser.addExtensionParser(rPDEP);
		gpxParser.addExtensionParser(rPTEP);
		gpxParser.addExtensionParser(rPAEP);
		gpxParser.addExtensionParser(rPDIEP);
	}

	public boolean isWayPointOnRoute(Waypoint aWayPoint, Route aRoute) {
		System.out.println(aWayPoint.getExtensionData().toString());
		boolean isWayPointOnRoute = false;
		System.out.println(aRoute.getRoutePoints());
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

	public boolean isWayPointOnTrack(Waypoint aWayPoint, Track aTrack) {

		boolean isWayPointOnTrack = false;
		System.out.println(aTrack.getTrackPoints());
		for (Waypoint aWaypointInaTrack : aTrack.getTrackPoints()) {
			if (new RouteWayPoint(aWaypointInaTrack).equals(new RouteWayPoint(
					aWayPoint))) {

				isWayPointOnTrack = true;
				LOG.info("WayPoint " + aWayPoint + " Found In a Track" + aTrack);
				break;
			}

		}

		return isWayPointOnTrack;
	}

	public boolean routeContainsTurn(String turnDescription, Route aRoute) {
		System.out.println(aRoute);
		boolean routeContainsTurn = false;
		turnDescription = turnDescription.toUpperCase();

		for (Waypoint aWaypointInaRoute : aRoute.getRoutePoints()) {
			if (aWaypointInaRoute.getDescription() != null
					&& aWaypointInaRoute.getDescription().toUpperCase()
							.equals(turnDescription)) {
				routeContainsTurn = true;

				LOG.info("WayPoint " + aWaypointInaRoute
						+ " contains route instruction" + turnDescription);
				break;
			}
		}

		return routeContainsTurn;
	}

	CloseableHttpResponse sendAndGetResponse(String requestUrl)
			throws IOException {
		String serviceUrl = requestUrl;
		if (IntegrationTestProperties.getTestPropertyBool("viaApigee")) {
			serviceUrl += "&apikey="
					+ IntegrationTestProperties.getTestProperty("apiKey");
			LOG.debug("APPLYING KEY:");
		}

		return doSendAndGetResponse(serviceUrl);
	}

	public boolean isWayPointOnGPXRoutes(Waypoint wp) {

		boolean isWayPointOnRoute = false;

		for (Route route : getRoutes()) {
			isWayPointOnRoute = isWayPointOnRoute(wp, route);

			if (isWayPointOnRoute(wp, route)) {
				break;
			}

		}
		return isWayPointOnRoute;
	}

}
