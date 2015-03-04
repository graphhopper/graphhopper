package uk.co.ordnancesurvey.routing;

import java.io.IOException;
import java.text.ParseException;
import java.util.List;
import java.util.Map;

import uk.co.ordnancesurvey.webtests.IntegrationTestProperties;
import cucumber.api.Scenario;
import cucumber.api.java.After;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;

public class GraphHopperHooks {
	GraphHopperUIUtil graphUiUtil;

	String instruction;

	@Given("^I request a route between \"([^\"]*)\" and \"([^\"]*)\" as a \"([^\"]*)\" from RoutingAPI$")
	public void getRoute(String pointA, String pointB, String routeType)
			throws InterruptedException {

		if (routeType.equalsIgnoreCase("car"))

			graphUiUtil = new GraphHopperUIUtil(
					IntegrationTestProperties
							.getTestProperty("graphHopperWebUrl"));
		else
			graphUiUtil = new GraphHopperUIUtil(
					IntegrationTestProperties
							.getTestProperty("DPNgraphHopperWebUrl"));

		String testON = IntegrationTestProperties.getTestProperty("testON");

		switch (testON.toUpperCase()) {
		case "WEB":

			graphUiUtil.getRouteFromUI(pointA, pointB, routeType);
			break;
		case "SERVICE":
			graphUiUtil.getRouteFromService(pointA, pointB, routeType);
			break;
		default:

			if (pointA.split(",").length == 2) {
				graphUiUtil.getRouteFromService(pointA, pointB, routeType);
				graphUiUtil.getRouteFromUI(pointA, pointB, routeType);
			} else {
				graphUiUtil.getRouteFromUI(pointA, pointB, routeType);
			}

			break;

		}

	}

	@Then("^I should be able to verify the \"([^\"]*)\" waypoint \"([^\"]*)\" \"([^\"]*)\" \"([^\"]*)\" \"([^\"]*)\" \"([^\"]*)\" \"([^\"]*)\" on the route map$")
	public void I_should_be_able_to_verify_the_waypoint_on_the_route_map(
			String wayPointIndex, String wayPoint_Coordinates,
			String wayPointDescription, String azimuth, String direction,
			String time, String distance) {

		graphUiUtil.isWayPointonRouteMap(wayPointIndex, wayPoint_Coordinates,
				wayPointDescription, azimuth, direction, time, distance);

	}

	@Then("^I should be able to verify the waypoints on the route map:")
	public void I_should_be_able_to_verify_the_waypoints_on_the_route_map(
			List<Map<String, String>> wayPointList) {

		graphUiUtil.isWayPointonRouteMap(wayPointList);

	}

	@Then("^I should be able to verify the waypoints not on the route map:")
	public void I_should_be_able_to_verify_the_not_waypoints_on_the_route_map(
			List<Map<String, String>> wayPointList) {

		graphUiUtil.isWayPointNotonRouteMap(wayPointList);

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

	@After({ "@Routing" })
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

}
