package uk.co.ordnancesurvey.routing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import uk.co.ordnancesurvey.webtests.IntegrationTestProperties;
import cucumber.api.DataTable;
import cucumber.api.java.After;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;

public class GraphHopperHooks {
	GraphHopperUIUtil graphUiUtil = new GraphHopperUIUtil();

	String instruction;

	@Given("^I request a route between \"([^\"]*)\" and \"([^\"]*)\" as a \"([^\"]*)\" from RoutingAPI$")
	public void getRoute(String pointA, String pointB, String routeType) {

		String testON = IntegrationTestProperties.getTestProperty("testON");

		switch (testON.toUpperCase()) {
		case "WEB":
			graphUiUtil.getRouteFromUI(pointA, pointB, routeType);
			break;
		case "SERVICE":
			graphUiUtil.getRouteFromService(pointA, pointB, routeType);
			break;
		default:
			graphUiUtil.getRouteFromService(pointA, pointB, routeType);
			graphUiUtil.getRouteFromUI(pointA, pointB, routeType);
			break;
		}

	}

	@Then("^I shhould be able to verify the \"([^\"]*)\" waypoint \"([^\"]*)\" \"([^\"]*)\" \"([^\"]*)\" \"([^\"]*)\" \"([^\"]*)\" \"([^\"]*)\" on the route map$")
	public void I_should_be_able_to_verify_the_waypoint_on_the_route_map(
			String wayPointIndex, String wayPoint_Coordinates,
			String wayPointDescription, String azimuth, String direction,
			String time, String distance) {

		graphUiUtil.verifyWayPointonRouteMap(wayPointIndex,
				wayPoint_Coordinates, wayPointDescription, azimuth, direction,
				time, distance);

	}

	@Then("^I should be able to verify the waypoints on the route map:")
	public void I_should_be_able_to_verify_the_waypoints_on_the_route_map(
			List<Map> list) {

		graphUiUtil.verifyWayPointonRouteMap(list);

	}

	@After({ "@Routing" })
	public void closeBrowser() {
		graphUiUtil.logout();
		System.out.println("closed");
	}

}
