package uk.co.ordnancesurvey.routing;

import java.io.IOException;
import java.text.ParseException;
import java.util.List;
import java.util.Map;

import org.eclipse.jetty.util.log.Log;

import com.thoughtworks.selenium.webdriven.commands.WaitForPageToLoad;

import javassist.CtField.Initializer;
import uk.co.ordnancesurvey.webtests.IntegrationTestProperties;
import cucumber.api.java.After;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;

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
			List<Map> wayPointList) {

		graphUiUtil.isWayPointonRouteMap(wayPointList);

	}

	@Then("^The total route time should be not more than \"([^\"]*)\"$")
	public void The_total_route_time_should_be_not_more_than(
			String totalRouteTime) throws ParseException {
		graphUiUtil.verifyTotalRouteTime(totalRouteTime);

	}

	@Then("^I should be able to verify the trackPoints on the route map:")
	public void I_should_be_able_to_verify_the_trackpoints_on_the_route_map(
			List<Map> trackPointsList) throws ParseException {

		graphUiUtil.isTrackPointonRouteMap(trackPointsList);

	}

	@Then("^I should be able to verify the trackPoints not on the route map:")
	public void I_should_be_able_to_verify_the_trackpoints_not_on_the_route_map(
			List<Map> trackPointsList) throws ParseException {

		graphUiUtil.isTrackPointNotonRouteMap(trackPointsList);

	}

	@Given("^I open the mapping appliaction$")
	public void I_open_the_mapping_application()  {
		System.out.println("Application Launching..");

		
	
	}

	@Then("^I should see appropriate map \"([^\"]*)\" loaded \"([^\"]*)\"$")
	public void I_should_see_appropriate_map(String expectedMap, String testID)
			throws IOException {
		graphUiUtil.compareMapImage(expectedMap, testID);
	}

	@When("^I pan to the \"([^\"]*)\" \"([^\"]*)\" times$")
	public void I_pan_to_the(String direction,int panningIndex) throws Throwable {
		
		for (int i = 0; i <panningIndex; i++) {
			graphUiUtil.panonMap(direction);
		}
		

	}
	
	@When("^I zoom into the layer \"([^\"]*)\"$")
	public void I_zoom_into_the_layer(String zoomlayer) throws InterruptedException {
		System.out.println("Zooming to layer "+zoomlayer);
		
		for (int i = 0; i < Integer.parseInt(zoomlayer); i++) {
			
			graphUiUtil.zoomIn();
		
		}


	}
	
	
	@When("^I zoom out the layer \"([^\"]*)\"$")
	public void I_zoom_out_the_layer(String zoomlayer) throws InterruptedException {
		System.out.println("Zooming to layer "+zoomlayer);
		
		for (int i = 0; i < 13; i++) {
			
			graphUiUtil.zoomIn();
		
		}
		
		for (int i = 0; i < Integer.parseInt(zoomlayer); i++) {
			
			graphUiUtil.zoomOut();
		
		}


	}
	
	
	

	@After({ "@Mapping" })
	public void closeBrowser() {
		graphUiUtil.logout();
		System.out.println("closed");

	}

}