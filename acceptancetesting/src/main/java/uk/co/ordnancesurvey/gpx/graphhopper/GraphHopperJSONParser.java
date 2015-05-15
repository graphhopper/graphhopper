package uk.co.ordnancesurvey.gpx.graphhopper;

import gherkin.JSONParser;
import gherkin.formatter.JSONPrettyFormatter;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;

import org.alternativevision.gpx.beans.Waypoint;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.ordnancesurvey.gpx.beans.RouteWayPoint;
import uk.co.ordnancesurvey.gpx.extensions.ExtensionConstants;
import uk.co.ordnancesurvey.webtests.platforms.BrowserPlatformOptions;

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
		this.jsonString=jsonString;
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

			double distance_rounding = Double.parseDouble(distance.toString());

			distance_rounding = Math.round(distance_rounding * 10) / 10.0;

			JsonPrimitive azimuth = instruction.getAsJsonPrimitive("azimuth");
			JsonPrimitive annotation_text = instruction
					.getAsJsonPrimitive("annotation_text");
			JsonArray interval = instruction.getAsJsonArray("interval");
			int coordinateIndex = Integer.parseInt(interval.get(0).toString());
			JsonElement s = getJSONCoordinates(paths, coordinateIndex);
			Double longitude = Double.parseDouble(s.getAsJsonArray().get(0)
					.toString());
			Double latitude = Double.parseDouble(s.getAsJsonArray().get(1)
					.toString());
			w.setLongitude(longitude);
			w.setLatitude(latitude);
			w.setDescription(description.toString());
			w.addExtensionData(ExtensionConstants.DISTANCE,
					String.valueOf(distance_rounding));
			w.addExtensionData(ExtensionConstants.TIME, time.toString());

			LOG.info("azimuth :" + azimuth);
			LOG.info("descritption: " + description);
			LOG.info("time :" + time);
			LOG.info("distance :" + distance);
			if (null != annotation_text) {
				w.addExtensionData("Annotation_text",annotation_text.getAsString().trim());
				LOG.info("annotation_text: " + annotation_text.getAsString().trim());
			}
			LOG.info("Coordinates : " + w.getLatitude() + ","
					+ w.getLongitude());

			json.addWayPoint(w);
		}

		
		

		return json;

	}

	
	public JSONWayPoints parseCoordinatesFromJson(String jsonString)
	{
		this.jsonString=jsonString;
		JsonParser parser = new JsonParser();
		JsonElement je = parser.parse(jsonString);
		JsonObject jo = je.getAsJsonObject();
		JsonArray paths = jo.getAsJsonArray("paths");
		JsonObject points = paths.get(0).getAsJsonObject()
				.getAsJsonObject("points");
		JsonArray coordinates = points.getAsJsonObject().getAsJsonArray(
				"coordinates");
		

		for (JsonElement jsonElement : coordinates) {
			Waypoint w = new Waypoint();
			Double longitude = Double.parseDouble(jsonElement.getAsJsonArray().get(0)
					.toString());
			Double latitude = Double.parseDouble(jsonElement.getAsJsonArray().get(1)
					.toString());
			w.setLongitude(longitude);
			w.setLatitude(latitude);
			json.addWayPoint(w);
			
			}

		
		return json;
		
	}
	
	
	public HashSet<Waypoint> getJsonCoordinatesAsHashSet()
	{
		
		parseCoordinatesFromJson(jsonString);
		return json.getInstructions();
	}
	
	public JsonElement getJSONCoordinates(JsonArray paths, int coordinateIndex) {

		JsonObject points = paths.get(0).getAsJsonObject()
				.getAsJsonObject("points");
		JsonArray coordinates = points.getAsJsonObject().getAsJsonArray(
				"coordinates");

		return coordinates.get(coordinateIndex);
	}

	
	public void parse(String routeType, String avoidances, String routeOptions,
			String[] string) {

		String vehicle="";
		String routeOption="";
		
		if (routeOptions.split(",").length>1)
		{
		 vehicle=routeOptions.split(",")[0];
		 routeOption=routeOptions.split(",")[1];
		}
		else
		{
			vehicle=routeOptions;
		}
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
					.getTestProperty("graphHopperWebUrl");
		}

		String apikey = IntegrationTestProperties.getTestProperty("apiKey");

		StringBuilder sb = new StringBuilder();
		sb.append(graphHopperUrl);
		sb.append("route?");
		if (routeType != null) {
			sb.append("type=");
			sb.append(routeType);
		}
		sb.append("&vehicle=");
		sb.append(vehicle);
		sb.append("&weighting=");
		if (routeOption.isEmpty())
		{
			routeOption="fastavoid";
		}
		sb.append(routeOption);
		sb.append(coordinateString);
		sb.append("&apikey=");
		sb.append(apikey);
		sb.append("&points_encoded=false");

		if (!avoidances.equals("")) {
			sb.append("&avoidances=" + avoidances);
					}
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
	 * 
	 * @param Waypoint
	 * @return true if Waypoint is found in the JSON string and otherwise false
	 *         is returned
	 */
	public boolean isWayPointinPath(Waypoint w) {
		boolean iswaypointinPath = false;

		for (Waypoint wp : json.getInstructions()) {

			RouteWayPoint k = new RouteWayPoint(wp);
			iswaypointinPath = k.equals(new RouteWayPoint(w));
			if (iswaypointinPath) {
				break;
			}
		}

		return iswaypointinPath;

				
	}

	public boolean isWayPointinPath(Waypoint we,HashSet<Waypoint> wa) {
		boolean iswaypointinPath = false;

		for (Waypoint waypoint : wa) {
			
			if( new RouteWayPoint(we).equals(new RouteWayPoint(waypoint)))
			{
				iswaypointinPath=true;
				LOG.info("WayPoint " + we + " Found In a Path");
			}
			if (iswaypointinPath) {
				break;
			}
		}
		
		return iswaypointinPath;
	}
	/**
	 * Creates a Waypoint with below attributes
	 * 
	 * @param wayPointDescription
	 * @param time
	 * @param distance
	 * @return Waypoint
	 */
	public Waypoint buildWayPointForJson(String wayPoint_Coordinates,
			String wayPointDescription, String time, String distance,String avoidance) {
		Waypoint w = new Waypoint();
		String waypoint[] = wayPoint_Coordinates.split(",");
		w.setLatitude(new Double(waypoint[0]));
		w.setLongitude(new Double(waypoint[1]));
		w.setDescription(wayPointDescription);
		w.addExtensionData(ExtensionConstants.DISTANCE, distance);
		w.addExtensionData(ExtensionConstants.TIME, time);
		w.addExtensionData("Annotation_text", avoidance.trim());
		return w;

	}

	// nearest point
	public String getNearestPoint(String pointA) {

		String nearestpoint = "";
		StringBuffer sb = new StringBuffer();
		if (Boolean.parseBoolean(IntegrationTestProperties
				.getTestProperty("viaApagee"))) {
			sb.append(IntegrationTestProperties
					.getTestProperty("graphHopperWebUrlViaApigee"));
		} else {
			sb.append(IntegrationTestProperties
					.getTestProperty("graphHopperWebUrl"));
		}

		sb.append("nearest?point=");
		sb.append(pointA);
		GraphHopperGPXParserRouteTest GPHService = new GraphHopperGPXParserRouteTest();

		try {
			CloseableHttpResponse httpResponse = GPHService
					.sendAndGetResponse(sb.toString());

			jsonString = IOUtils.toString(httpResponse.getEntity()
					.getContent(), "UTF-8");

			JsonParser jp = new JsonParser();
			JsonElement je = jp.parse(jsonString);
			JsonArray jo = je.getAsJsonObject().getAsJsonArray("coordinates");

			nearestpoint = jo.get(1).getAsString() + ","
					+ jo.get(0).getAsString();

		} catch (IOException e) {
			LOG.info("Exception raised whilst attempting to call graphhopper server "
					+ e.getMessage());
		}

		return nearestpoint;

	}
	
	public String getNearestPointDistance()
	{
		JsonParser jp = new JsonParser();
		JsonElement je = jp.parse(jsonString);
		JsonPrimitive distance = je.getAsJsonObject().getAsJsonPrimitive("distance");
		return distance.toString();
	}

	public long getTotalRouteTime() {
		JsonParser parser = new JsonParser();
		JsonElement je = parser.parse(jsonString);
		JsonObject jo = je.getAsJsonObject();
		JsonArray paths = jo.getAsJsonArray("paths");
		JsonPrimitive totalTime = paths.get(0).getAsJsonObject().getAsJsonPrimitive("time");
		return Long.parseLong(totalTime.toString());
	}





}
