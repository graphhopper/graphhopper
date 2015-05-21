package uk.co.ordnancesurvey.routing;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;

import uk.co.ordnancesurvey.gpx.graphhopper.IntegrationTestProperties;
import cucumber.api.DataTable;
import cucumber.api.Scenario;
import cucumber.api.java.After;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;

public class GraphHopperHooks {
	GraphHopperUIUtil graphUiUtil;

	String instruction;
	String nearestPoint = "";
	String Distance = "";

	@Given("^I request a route between \"([^\"]*)\" and \"([^\"]*)\" as a \"([^\"]*)\" from RoutingAPI and avoid \"([^\"]*)\"$")
	public void getRouteWithAvoidance(String pointA, String pointB,
			String routeOptions, String avoidances) throws InterruptedException {
		String graphHopperWebUrl;
		avoidances = avoidances.toLowerCase().trim();
		if (IntegrationTestProperties.getTestPropertyBool("viaApigee")) {
			graphHopperWebUrl = IntegrationTestProperties
					.getTestProperty("graphHopperWebUrlViaApigee");
		} else {
			graphHopperWebUrl = IntegrationTestProperties
					.getTestProperty("graphHopperWebUrl");
		}

		graphUiUtil = new GraphHopperUIUtil(graphHopperWebUrl);

		String testON = IntegrationTestProperties.getTestProperty("testON");

		switch (testON.toUpperCase()) {
		case "WEB":

			graphUiUtil
					.getRouteFromUI(routeOptions, avoidances, pointA, pointB);
			break;
		case "SERVICE":
			graphUiUtil.getRouteFromServiceWithAvoidance(routeOptions,
					avoidances, pointA, pointB);
			break;
		default:

			if (pointA.split(",").length == 2) {
				graphUiUtil.getRouteFromServiceWithAvoidance(routeOptions,
						avoidances, pointA, pointB);
				graphUiUtil.getRouteFromUI(routeOptions, avoidances, pointA,
						pointB);
			} else {
				graphUiUtil.getRouteFromUI(routeOptions, avoidances, pointA,
						pointB);
			}

			break;

		}

	}

	@Given("^I request a route between \"([^\"]*)\" and \"([^\"]*)\" as a \"([^\"]*)\" from RoutingAPI and avoid \"([^\"]*)\" via \"([^\"]*)\"$")
	public void getRouteWithAvoidances(String pointA, String pointB,
			String routeOptions, String avoidance, String pointC)
			throws InterruptedException {
		String graphHopperWebUrl;

		if (IntegrationTestProperties.getTestPropertyBool("viaApigee")) {
			graphHopperWebUrl = IntegrationTestProperties
					.getTestProperty("graphHopperWebUrlViaApigee");
		} else {
			graphHopperWebUrl = IntegrationTestProperties
					.getTestProperty("graphHopperWebUrl");
		}

		graphUiUtil = new GraphHopperUIUtil(graphHopperWebUrl);

		String testON = IntegrationTestProperties.getTestProperty("testON");

		switch (testON.toUpperCase()) {
		case "WEB":

			graphUiUtil
					.getRouteFromUI(routeOptions, avoidance, pointA, pointB, pointC);
			break;
		case "SERVICE":
			graphUiUtil.getRouteFromServiceWithAvoidance(routeOptions,
					avoidance, pointA, pointB, pointC);
			break;
		default:

			if (pointA.split(",").length == 2) {
				graphUiUtil.getRouteFromServiceWithAvoidance(routeOptions,
						avoidance, pointA, pointB, pointC);
				graphUiUtil
						.getRouteFromUI(routeOptions, avoidance,pointA, pointB, pointC);
			} else {
				graphUiUtil.getRouteFromUI(routeOptions, avoidance, pointA, pointB,
						pointC);
			}

			break;

		}

	}
/*
	public void getRoute(String pointA, String pointB, String routeOptions,
			String pointC, String pointD) throws InterruptedException {

		graphUiUtil = new GraphHopperUIUtil(
				IntegrationTestProperties.getTestProperty("graphHopperWebUrl"));

		String testON = IntegrationTestProperties.getTestProperty("testON");

		switch (testON.toUpperCase()) {
		case "WEB":

			graphUiUtil.getRouteFromUI(routeOptions, "", pointA, pointB,
					pointC, pointD);
			break;
		case "SERVICE":
			graphUiUtil.getRouteFromService(routeOptions, pointA, pointB,
					pointC, pointD);
			break;
		default:

			if (pointA.split(",").length == 2) {
				graphUiUtil.getRouteFromService(routeOptions, pointA, pointB,
						pointC, pointD);
				graphUiUtil.getRouteFromUI(routeOptions, "", pointA, pointB,
						pointC, pointD);
			} else {
				graphUiUtil.getRouteFromUI(routeOptions, "", pointA, pointB,
						pointC, pointD);
			}

			break;

		}

	}*/

	@Given("^I request a route between \"([^\"]*)\" and \"([^\"]*)\" as a \"([^\"]*)\" from RoutingAPI and avoid \"([^\"]*)\" via \"([^\"]*)\" and \"([^\"]*)\"$")
	public void getRouteWithAvoidances(String pointA, String pointB,
			String routeOptions, String avoidance, String pointC, String pointD)
			throws InterruptedException {

		graphUiUtil = new GraphHopperUIUtil(
				IntegrationTestProperties.getTestProperty("graphHopperWebUrl"));

		String testON = IntegrationTestProperties.getTestProperty("testON");

		switch (testON.toUpperCase()) {
		case "WEB":

			graphUiUtil.getRouteFromUI(routeOptions, "", pointA, pointB,
					pointC, pointD);
			break;
		case "SERVICE":
			graphUiUtil.getRouteFromServiceWithAvoidance(routeOptions,
					avoidance, pointA, pointB, pointC, pointD);
			break;
		default:

			if (pointA.split(",").length == 2) {
				graphUiUtil.getRouteFromServiceWithAvoidance(routeOptions,
						avoidance, pointA, pointB, pointC, pointD);
				graphUiUtil.getRouteFromUI(routeOptions, "", pointA, pointB,
						pointC, pointD);
			} else {
				graphUiUtil.getRouteFromUI(routeOptions, "", pointA, pointB,
						pointC, pointD);
			}

			break;

		}

	}

	@Given("^I request a nearest point from  \"([^\"]*)\" from Nearest Point API$")
	public void I_request_a_nearest_point_from_from_Nearest_Point_API(
			String pointA) {

		graphUiUtil = (IntegrationTestProperties
				.getTestPropertyBool("viaApigee") == true) ? new GraphHopperUIUtil(
				IntegrationTestProperties
						.getTestProperty("graphHopperWebUrlViaApigee"))
				: new GraphHopperUIUtil(
						IntegrationTestProperties
								.getTestProperty("graphHopperWebUrl"));

		nearestPoint = graphUiUtil.nearestPointService(pointA);
		Distance = graphUiUtil.nearestPointDistance();

	}

	@Then("^I should be able to verify the nearest point to be \"([^\"]*)\" at a distance of \"([^\"]*)\"$")
	public void I_should_be_able_to_verify_the_nearest_point_to_be(
			String pointB, String distance) {

		Assert.assertTrue("******Expected nearest Point " + pointB
				+ " is not matching with " + nearestPoint + "********",
				pointB.equals(nearestPoint));
		Assert.assertTrue("******Expected nearest Point distance " + distance
				+ " is not matcching with " + Distance,
				Distance.equals(distance));

	}

	@Then("^I should be able to verify the \"([^\"]*)\" waypoint \"([^\"]*)\" \"([^\"]*)\" \"([^\"]*)\" \"([^\"]*)\" \"([^\"]*)\" \"([^\"]*)\" on the route map$")
	public void I_should_be_able_to_verify_the_waypoint_on_the_route_map(
			String wayPointIndex, String wayPoint_Coordinates,
			String wayPointDescription, String azimuth, String direction,
			String time, String distance) {

		graphUiUtil.isWayPointonRouteMap(wayPointIndex, wayPoint_Coordinates,
				wayPointDescription, azimuth, direction, time, distance, "");

	}

	@Then("^I should be able to verify the waypoints on the route map:")
	public void I_should_be_able_to_verify_the_waypoints_on_the_route_map(
			List<Map<String, String>> wayPointList) {
		Assert.assertTrue(
				"Waypoint not found on the route where it was expected",
				graphUiUtil.isWayPointonRouteMap(wayPointList));

	}

	@Then("^I should be able to verify the waypoints not on the route map:")
	public void I_should_be_able_to_verify_the_not_waypoints_on_the_route_map(
			List<Map<String, String>> wayPointList) {

		Assert.assertFalse(
				"Waypoint found on the route where it was not expected",
				graphUiUtil.isWayPointNotonRouteMap(wayPointList));
		// graphUiUtil.isWayPointNotonRouteMap(wayPointList);

	}

	@Then("^The total route time should be not more than \"([^\"]*)\"$")
	public void The_total_route_time_should_be_not_more_than(
			String totalRouteTime) throws ParseException {
		graphUiUtil.verifyTotalRouteTime(totalRouteTime);

	}

	@Then("^I should be able to verify the trackPoints on the route map:")
	public void I_should_be_able_to_verify_the_trackpoints_on_the_route_map(
			List<Map<String, String>> trackPointsList) throws ParseException {

		graphUiUtil.isTrackPointonRouteMap(trackPointsList);

	}

	@Then("^I should be able to verify the trackPoints not on the route map:")
	public void I_should_be_able_to_verify_the_trackpoints_not_on_the_route_map(
			List<Map<String, String>> trackPointsList) throws ParseException {

		graphUiUtil.isTrackPointNotonRouteMap(trackPointsList);

	}

	@After
	public void closeBrowser(Scenario sc) {

		if (sc.isFailed()) {

			try {
				byte[] screeenshot = graphUiUtil.takescreenAsBiteArray();
				sc.embed(screeenshot, "image/png");

			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		graphUiUtil.logout();
		System.out.println("closed");

	}





@Given("^I request a route between pointA and pointB as a \"([^\"]*)\" from RoutingAPI and avoid \"([^\"]*)\" via$")
public void getRouteWithAvoidancesintermediatepoints(String routeOptions,String avoidances,DataTable dt)
		throws InterruptedException {
	
	List<List<String>> data =dt.raw();
   
   
String[] points= new String[data.get(1).size()];
   points= data.get(1).toArray(points);


	
	graphUiUtil = new GraphHopperUIUtil(
			IntegrationTestProperties.getTestProperty("graphHopperWebUrl"));

	String testON = IntegrationTestProperties.getTestProperty("testON");

	switch (testON.toUpperCase()) {
	case "WEB":

		graphUiUtil.getRouteFromUI(routeOptions, avoidances, points);
		break;
	case "SERVICE":
		graphUiUtil.getRouteFromServiceWithAvoidance(routeOptions,
				avoidances, points);
		break;
	default:

		if (points[0].split(",").length == 2) {
			graphUiUtil.getRouteFromServiceWithAvoidance(routeOptions,
					avoidances, points);
			graphUiUtil.getRouteFromUI(routeOptions, avoidances, points);
		} else {
			graphUiUtil.getRouteFromUI(routeOptions, avoidances, points);
		}

		break;

	}



}
}