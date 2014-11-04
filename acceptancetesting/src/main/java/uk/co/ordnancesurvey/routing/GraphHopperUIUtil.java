package uk.co.ordnancesurvey.routing;

import static org.junit.Assert.assertTrue;
import static uk.co.ordnancesurvey.routing.GraphHopperComponentIdentification.FROM_ROUTE;
import static uk.co.ordnancesurvey.routing.GraphHopperComponentIdentification.INSTRUCTIONS;
import static uk.co.ordnancesurvey.routing.GraphHopperComponentIdentification.ROUTE_SEARCH;
import static uk.co.ordnancesurvey.routing.GraphHopperComponentIdentification.ROUTE_TYPE_BIKE;
import static uk.co.ordnancesurvey.routing.GraphHopperComponentIdentification.ROUTE_TYPE_CAR;
import static uk.co.ordnancesurvey.routing.GraphHopperComponentIdentification.ROUTE_TYPE_WALK;
import static uk.co.ordnancesurvey.routing.GraphHopperComponentIdentification.TOTAL_ROUTE_TIME;
import static uk.co.ordnancesurvey.routing.GraphHopperComponentIdentification.TO_ROUTE;
import static uk.co.ordnancesurvey.routing.GraphHopperComponentIdentification.WAYPOINT_ONMAP;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.alternativevision.gpx.beans.Route;
import org.alternativevision.gpx.beans.Waypoint;
import org.junit.Assert;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.ordnancesurvey.gpx.extensions.ExtensionConstants;
import uk.co.ordnancesurvey.gpx.graphhopper.GraphHopperGPXParserRouteTest;
import uk.co.ordnancesurvey.webtests.IntegrationTestProperties;
import uk.co.ordnancesurvey.webtests.multiplatform.MultiplatformTest;
import uk.co.ordnancesurvey.webtests.platforms.BrowserPlatformOptions;

public class GraphHopperUIUtil extends MultiplatformTest {

	private String baseUrl;
	private String routeStepNumber;
	String testOn = IntegrationTestProperties.getTestProperty("testON");
	GraphHopperGPXParserRouteTest GPHService = new GraphHopperGPXParserRouteTest();

	JavascriptExecutor js = (JavascriptExecutor) driver;
	WebElement we;

	private static final Logger LOG = LoggerFactory
			.getLogger(GraphHopperUIUtil.class);

	public GraphHopperUIUtil() {
		super(BrowserPlatformOptions.getEnabledOptionsArrayList().get(0)[0]);
		try {
			init();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void init() throws InterruptedException {
		baseUrl = IntegrationTestProperties
				.getTestProperty("graphHopperWebUrl");
		if (null == baseUrl) {
			baseUrl = "http://os-graphhopper.elasticbeanstalk.com/";
		}

		if (!testOn.equalsIgnoreCase("SERVICE")) {
			initialiseWebDriver();
			Thread.sleep(2000);

			panRighttonMap();

		}

	}

	@Override
	public void logout() {
		if (!testOn.equalsIgnoreCase("SERVICE"))
			driver.close();
	}

	public void getRouteFromUI(String pointA, String pointB, String routeType) {

		switch (routeType)

		{
		case "car":
			clickElement(ROUTE_TYPE_CAR);
			break;
		case "bike":
			clickElement(ROUTE_TYPE_BIKE);
			break;
		case "walk":
			clickElement(ROUTE_TYPE_WALK);
			break;
		default:
			clickElement(ROUTE_TYPE_CAR);
			break;

		}

		typeIntoField(FROM_ROUTE, pointA);
		typeIntoField(TO_ROUTE, pointB);
		clickElement(ROUTE_SEARCH);
		waitFor(INSTRUCTIONS);

	}

	@Override
	public void login() {
		driver.navigate().to(baseUrl);
	}

	public void verifyInstructionThroughUI(String routeStepNumber,
			String stepInstruction) {
		this.routeStepNumber = routeStepNumber;
		checkTableRow(INSTRUCTIONS, Integer.parseInt(this.routeStepNumber),
				stepInstruction);

	}

	public void getRouteFromService(String pointA, String pointB,
			String routeType) {
		GPHService.parseRoute(pointA + "," + pointB, "gpx", routeType);

	}

	public void verifyInstructionThroughService(String stepInstruction) {
		HashSet<Route> routeInstruction = GPHService.getRoutes();

		Assert.assertTrue(
				"The Route instruction is not found in the gpx response",
				GPHService.routeContainsTurn(stepInstruction.toUpperCase(),
						routeInstruction.iterator().next()));

	}

	public void panLeftonMap() {

		clickElement(FROM_ROUTE);

		Actions action = new Actions(driver);
		action.sendKeys(driver.findElement(By.xpath("//*[@id='map']")),
				Keys.ARROW_RIGHT).build().perform();

	}

	public void panRighttonMap() {

		Actions action = new Actions(driver);

		clickElement(FROM_ROUTE);

		action.sendKeys(driver.findElement(By.xpath("//*[@id='map']")),
				Keys.ARROW_RIGHT).build().perform();
	}

	private Waypoint buildWayPoint(String waypointco, String time)
			throws ParseException {

		Waypoint wp = new Waypoint();
		String waypoint[] = waypointco.split(",");
		wp.setLatitude(new Double(waypoint[0]));
		wp.setLongitude(new Double(waypoint[1]));

		SimpleDateFormat t = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
		Date date = t.parse(time);
		wp.setTime(date);
		return wp;
	}

	public void isWayPointonRouteMap(String wayPointIndex,
			String wayPoint_Coordinates, String wayPointDescription,
			String azimuth, String direction, String time, String distance) {
		final List<WebElement> WAY_POINTS;
		Waypoint wp;

		switch (testOn.toUpperCase()) {
		case "WEB":
			WAY_POINTS = driver.findElements(By
					.xpath("//*[@id='instructions']/tbody/tr[*]/td[2]"));
			WAY_POINTS.get(Integer.parseInt(wayPointIndex) - 1).click();
			Assert.assertTrue(getValue(WAYPOINT_ONMAP) + " comparison failed",
					wayPointDescription
							.equalsIgnoreCase(getValue(WAYPOINT_ONMAP)));
			break;
		case "SERVICE":
			wp = buildWayPoint(wayPoint_Coordinates, wayPointDescription,
					azimuth, direction, time, distance);
			Assert.assertTrue(GPHService.isWayPointOnGPXRoutes(wp));

			break;

		default:
			WAY_POINTS = driver.findElements(By
					.xpath("//*[@id='instructions']/tbody/tr[*]/td[2]"));
			WAY_POINTS.get(Integer.parseInt(wayPointIndex) - 1).click();
			Assert.assertTrue(getValue(WAYPOINT_ONMAP) + " comparison failed",
					wayPointDescription
							.equalsIgnoreCase(getValue(WAYPOINT_ONMAP)));
			wp = buildWayPoint(wayPoint_Coordinates, wayPointDescription,
					azimuth, direction, time, distance);
			Assert.assertTrue(GPHService.isWayPointOnGPXRoutes(wp));
			break;
		}

	}

	private Waypoint buildWayPoint(String wayPoint_Coordinates,
			String wayPointDescription, String azimuth, String direction,
			String time, String distance) {

		Waypoint wp = new Waypoint();
		String waypoint[] = wayPoint_Coordinates.split(",");
		wp.setLatitude(new Double(waypoint[0]));
		wp.setLongitude(new Double(waypoint[1]));

		wp.setDescription(wayPointDescription);
		wp.addExtensionData(ExtensionConstants.AZIMUTH, azimuth);
		wp.addExtensionData(ExtensionConstants.DIRECTION, direction);
		wp.addExtensionData(ExtensionConstants.TIME, time);
		wp.addExtensionData(ExtensionConstants.DISTANCE, distance);
		LOG.info(wp.toString());
		return wp;
	}

	public void isWayPointonRouteMap(List<Map> waypointList) {

		for (int i = 0; i < waypointList.size(); i++) {
			String wayPointIndex = (String) waypointList.get(i).get(
					"wayPointIndex");
			String waypointco = (String) waypointList.get(i).get("waypointco");
			String waypointdesc = (String) waypointList.get(i).get(
					"waypointdesc");
			String azimuth = (String) waypointList.get(i).get("azimuth");
			String direction = (String) waypointList.get(i).get("direction");
			String time = (String) waypointList.get(i).get("time");
			String distance = (String) waypointList.get(i).get("distance");
			isWayPointonRouteMap(wayPointIndex, waypointco, waypointdesc,
					azimuth, direction, time, distance);
		}

	}

	public void verifyTotalRouteTime(String totalRouteTime)
			throws ParseException {

		SimpleDateFormat formatter = new SimpleDateFormat("H'h'mm'min'");
		formatter.setTimeZone(TimeZone.getTimeZone("GMT"));
		Date eTime, aTime;

		String actualTime="0h00min";
		String expectedTime = totalRouteTime.trim().replaceAll(" ", "");
		eTime = formatter.parse(expectedTime);
		aTime=formatter.parse(actualTime);

		switch (testOn.toUpperCase()) {
		case "WEB":

			actualTime = getValue(TOTAL_ROUTE_TIME).split("take ")[1].trim()
					.replaceAll(" ", "");
			if (!actualTime.contains("h")) {
				actualTime = "00h" + actualTime;
			}
			aTime = formatter.parse(actualTime);

			LOG.info("The total route time expected is " + eTime.getTime()
					+ " Milliseconds and actual is " + aTime.getTime()
					+ " Milliseconds");
			assertTrue("The total route time expected " + eTime.getTime()
					+ " is not matchin with actual " + aTime.getTime(),
					aTime.getTime() <= eTime.getTime());

			break;

		case "SERVICE":
			aTime.setTime(GPHService.getTotalRouteTime());
			LOG.info("The total route time expected is " + eTime.getTime()
					+ " and actual is " + aTime.getTime());
			assertTrue("The total route time expected " + eTime.getTime()
					+ " is not matchin with actual " + aTime.getTime(),
					aTime.getTime() <= eTime.getTime());
			break;

		default:
			actualTime = getValue(TOTAL_ROUTE_TIME).split("take ")[1].trim()
			.replaceAll(" ", "");
	if (!actualTime.contains("h")) {
		actualTime = "00h" + actualTime;
	}
	aTime = formatter.parse(actualTime);

	LOG.info("The total route time expected is " + eTime.getTime()
			+ " Milliseconds and actual is " + aTime.getTime()
			+ " Milliseconds");
	assertTrue("The total route time expected " + eTime.getTime()
			+ " is not matchin with actual " + aTime.getTime(),
			aTime.getTime() <= eTime.getTime());
		}

	}

	public void isTrackPointonRouteMap(List<Map> trackPointsList)
			throws ParseException {

		for (int i = 0; i < trackPointsList.size(); i++) {

			String waypointco = (String) trackPointsList.get(i).get(
					"trackPointco");
			String time = (String) trackPointsList.get(i).get("time");

			Waypoint trackPoint = buildWayPoint(waypointco, time);
			assertTrue(GPHService.isWayPointOnTrack(trackPoint, GPHService
					.getTracks().iterator().next()));

		}

	}

}