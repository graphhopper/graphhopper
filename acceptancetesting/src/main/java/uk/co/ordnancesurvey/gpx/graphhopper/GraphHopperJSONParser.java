package uk.co.ordnancesurvey.gpx.graphhopper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;

import org.alternativevision.gpx.beans.Waypoint;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.ordnancesurvey.gpx.beans.RouteWayPoint;
import uk.co.ordnancesurvey.gpx.extensions.ExtensionConstants;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

public class GraphHopperJSONParser {

	JSONWayPoints json = new JSONWayPoints();
	private static final Logger LOG = LoggerFactory
			.getLogger(GraphHopperJSONParser.class);
	String jsonString;

	public GraphHopperJSONParser(String responseString) {
		jsonString = responseString;
	}


	public GraphHopperJSONParser() {
		// TODO Auto-generated constructor stub
	}

	public JSONWayPoints parseJSONFromString(String jsonString) {

		JsonParser parser = new JsonParser();
		JsonElement je = parser.parse(jsonString);
		JsonObject jo = je.getAsJsonObject();
		JsonArray paths = jo.getAsJsonArray("paths");

		JsonArray instructions = paths.get(0).getAsJsonObject()
				.getAsJsonArray("instructions");

		for (int i = 0; i < instructions.size(); i++) {
			Waypoint w = new Waypoint();

			JsonObject instruction = instructions.get(i).getAsJsonObject();

			JsonPrimitive description = instruction.getAsJsonPrimitive("text");
			JsonPrimitive time = instruction.getAsJsonPrimitive("time");
			JsonPrimitive distance = instruction.getAsJsonPrimitive("distance");
			JsonPrimitive azimuth = instruction.getAsJsonPrimitive("azimuth");
			w.setDescription(description.toString());
			w.addExtensionData(ExtensionConstants.DISTANCE, distance.toString());
			w.addExtensionData(ExtensionConstants.TIME, time.toString());
			// w.addExtensionData("azimuth",azimuth.toString());
			System.out.println("azimuth :" + azimuth);
			System.out.println("descritption: " + description);
			System.out.println("time :" + time);
			System.out.println("distance :" + distance);
			json.addWayPoint(w);
		}

		return json;

	}

	public void parse(String routeType, String vehicle, String[] string) {

		// Set up the URL
		String jsonResponse = "";
		String coordinateString = "";
		String graphHopperUrl;

		for (int i = 0; i < string.length; i++) {

			coordinateString = coordinateString + "&point=" + string[i];

		}

		if (IntegrationTestProperties.getTestPropertyBool("viaApigee")) {
			graphHopperUrl = IntegrationTestProperties
					.getTestProperty("graphHopperWebUrlViaApigee");
		} else {
			graphHopperUrl = IntegrationTestProperties
					.getTestProperty("graphHopperWebUrlViaApigee");
		}

		String apikey= IntegrationTestProperties.getTestProperty("apiKey");
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
		sb.append("&apikey=");
		sb.append(apikey);
		GraphHopperGPXParserRouteTest GPHService = new GraphHopperGPXParserRouteTest();
		try {
			CloseableHttpResponse httpResponse = GPHService
					.sendAndGetResponse(sb.toString());
			jsonResponse = IOUtils.toString(httpResponse.getEntity()
					.getContent(), "UTF-8");

		} catch (IOException e) {
			LOG.info("Exception raised whilst attempting to call graphhopper server "
					+ e.getMessage());
		}

		if (jsonResponse != null && jsonResponse.length() > 0) {
			parseJSONFromString(jsonResponse);
		}

	}


	/**
	 * verifies if the waypoint is present in the JSON string.
	 * @param Waypoint
	 * @return true if Waypoint is found in the JSON string and
	 * otherwise false is returned
	 */
	public boolean isWayPointinPath(Waypoint w) {
		boolean iswaypointinPath = false;
		

		for (Waypoint wp : json.getInstructions()) {
			
			RouteWayPoint k= new RouteWayPoint(wp);
			iswaypointinPath=k.equals(new RouteWayPoint(w));
		if (iswaypointinPath)
		{
			break;
		}
		}

		return iswaypointinPath;

	}

	/**
	 *Creates a Waypoint with  below attributes
	 * @param wayPointDescription
	 * @param time
	 * @param distance
	 * @return Waypoint
	 */
	public Waypoint buildWayPointForJson(String wayPointDescription,
			String time, String distance) {
		Waypoint w = new Waypoint();
		w.setDescription(wayPointDescription);
		w.addExtensionData(ExtensionConstants.DISTANCE, distance);
		w.addExtensionData(ExtensionConstants.TIME, time);
		return w;

	}

}