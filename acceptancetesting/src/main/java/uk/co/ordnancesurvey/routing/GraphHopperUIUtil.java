package uk.co.ordnancesurvey.routing;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import static uk.co.ordnancesurvey.routing.GraphHopperComponentIdentification.*;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import javax.imageio.ImageIO;

import org.alternativevision.gpx.beans.Route;
import org.alternativevision.gpx.beans.Waypoint;
import org.junit.Assert;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.ordnancesurvey.gpx.extensions.ExtensionConstants;
import uk.co.ordnancesurvey.gpx.graphhopper.GraphHopperGPXParserRouteTest;
import uk.co.ordnancesurvey.gpx.graphhopper.GraphHopperJSONParser;
import uk.co.ordnancesurvey.webtests.base.ComponentID;
import uk.co.ordnancesurvey.webtests.base.ImageComparison;
import uk.co.ordnancesurvey.webtests.multiplatform.MultiplatformTest;
import uk.co.ordnancesurvey.webtests.platforms.BrowserPlatformOptions;
import uk.co.ordnancesurvey.gpx.graphhopper.IntegrationTestProperties;

public class GraphHopperUIUtil extends MultiplatformTest {

	private String baseUrl;
	private String routeStepNumber;
	String testOn = IntegrationTestProperties.getTestProperty("testON");
	GraphHopperGPXParserRouteTest GPHService = new GraphHopperGPXParserRouteTest();
	GraphHopperJSONParser GPHJsonService = new GraphHopperJSONParser();
	GraphHopperJSONParser JSONService = new GraphHopperJSONParser();

	JavascriptExecutor js = (JavascriptExecutor) driver;
	WebElement we;
	private BufferedImage actualMap;

	private static final Logger LOG = LoggerFactory
			.getLogger(GraphHopperUIUtil.class);

	public GraphHopperUIUtil(String url) {

		super(BrowserPlatformOptions.getEnabledOptionsArrayList().get(0)[0]);
		try {
			baseUrl = url;
			init();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void init() throws InterruptedException {
		// baseUrl = IntegrationTestProperties
		// .getTestProperty("graphHopperWebUrl");
		// if (null == baseUrl) {
		// baseUrl = "http://os-graphhopper.elasticbeanstalk.com/";
		// }

		if (!testOn.equalsIgnoreCase("SERVICE")) {
			initialiseWebDriver();
		}

	}

	@Override
	public void logout() {
		if (!testOn.equalsIgnoreCase("SERVICE"))
			driver.close();
	}

	/**
	 * <p>
	 * getRouteFromUI is to get a route from web interface using the provided
	 * start, end and intermediate waypoints.
	 * <p>
	 * all avoidances will be considered while generating a route.
	 * 
	 * @param routeType
	 *            can be car/bike/foot
	 * @param avoidance
	 *            can be aroad,cliff.. etc and it can be "" if no avoidance is
	 *            need to be set
	 * @param points
	 *            start and end points along with any intermediate points
	 * @throws InterruptedException
	 */
	public void getRouteFromUI(String routeType, String avoidance,
			String... points) throws InterruptedException {

		switch (routeType) {
		case "car":
			clickElement(ROUTE_TYPE_CAR);

			break;
		case "bike":
			clickElement(ROUTE_TYPE_BIKE);
			break;
		case "foot":
			clickElement(ROUTE_TYPE_WALK);
			break;
		default:
			clickElement(ROUTE_TYPE_CAR);
			break;

		}

		for (int i = 0; i < points.length - 2; i++)

		{
			clickElement(ADD_WAYPOINT);
		}

		for (int i = 0; i < points.length; i++) {
			String point = points[i];

			int length = point.split(",").length;

			if (length == 2) {

				waypoint = new ComponentID(i + "_searchBox");
				typeIntoField(waypoint, point);

			}

			else {
				waypoint = new ComponentID(i + "_searchBox");
				typeIntoField(waypoint, point);
				clickElement(dropDown);
			}
		}

		if (avoidance != "") {
			clickElement(settingsButton);
			clickElement(avoidance_ARoad);
		}
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
		List<WebElement> WAY_POINTS = driver.findElements(By
				.xpath("//*[@id='instructions']/tbody/tr[*]/td[2]"));
		WAY_POINTS.get(Integer.parseInt(routeStepNumber) - 1).click();

		checkTableRow(INSTRUCTIONS, Integer.parseInt(this.routeStepNumber),
				stepInstruction);

	}

	public void getRouteFromServiceWithAvoidance(String routeType,
			String avoidance, String... points) {

		if (IntegrationTestProperties.getTestProperty("routeType")
				.equals("gpx")) {
			GPHService.parseRoute("gpx", routeType, points);
		}

		else {

			GPHJsonService.parse("json", avoidance, routeType, points);
		}

	}

	public void getRouteFromService(String routeType, String... points) {

		if (IntegrationTestProperties.getTestProperty("routeType")
				.equals("gpx")) {
			GPHService.parseRoute("gpx", routeType, points);
		}

		else {

			GPHJsonService.parse("json", "", routeType, points);
		}

	}

	public void verifyInstructionThroughService(String stepInstruction) {
		HashSet<Route> routeInstruction = GPHService.getRoutes();

		Assert.assertTrue(
				"The Route instruction is not found in the gpx response",
				GPHService.routeContainsTurn(stepInstruction.toUpperCase(),
						routeInstruction.iterator().next()));

	}

	private Waypoint buildWayPoint(String waypointco) throws ParseException {

		Waypoint wp = new Waypoint();
		String waypoint[] = waypointco.split(",");
		wp.setLatitude(new Double(waypoint[0]));
		wp.setLongitude(new Double(waypoint[1]));

		return wp;
	}

	public boolean isWayPointonRouteMap(String wayPointIndex,
			String wayPoint_Coordinates, String wayPointDescription,
			String azimuth, String direction, String time, String distance) {
		boolean isWayPointonRouteMap = false;
		Waypoint wp;

		switch (testOn.toUpperCase()) {
		case "WEB":

			verifyInstructionThroughUI(wayPointIndex, wayPointDescription);
			isWayPointonRouteMap = true;
			break;
		case "SERVICE":
			if (IntegrationTestProperties.getTestProperty("routeType").equals(
					"gpx")) {
				wp = buildWayPoint(wayPoint_Coordinates, wayPointDescription,
						azimuth, direction, time, distance);
				isWayPointonRouteMap = GPHService.isWayPointOnGPXRoutes(wp);

			} else {
				wp = GPHJsonService.buildWayPointForJson(wayPoint_Coordinates,
						wayPointDescription, time, distance);
				isWayPointonRouteMap = GPHJsonService.isWayPointinPath(wp);

			}

			break;

		default:
			verifyInstructionThroughUI(wayPointIndex, wayPointDescription);

			if (IntegrationTestProperties.getTestProperty("routeType").equals(
					"gpx")) {
				wp = buildWayPoint(wayPoint_Coordinates, wayPointDescription,
						azimuth, direction, time, distance);
				isWayPointonRouteMap = GPHService.isWayPointOnGPXRoutes(wp);

			} else {
				wp = GPHJsonService.buildWayPointForJson(wayPoint_Coordinates,
						wayPointDescription, time, distance);
				isWayPointonRouteMap = GPHJsonService.isWayPointinPath(wp);

			}
			break;
		}
		return isWayPointonRouteMap;

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
		LOG.info(wp.getExtensionData().toString());
		return wp;
	}

	public boolean isWayPointNotonRouteMap(
			List<Map<String, String>> wayPointList) {
		boolean isWayPointonRouteMap = isWayPointonRouteMap(wayPointList);
		return isWayPointonRouteMap;
	}

	public boolean isWayPointonRouteMap(List<Map<String, String>> waypointList) {
		boolean isWayPointonRouteMap = false;
		for (int i = 0; i < waypointList.size(); i++) {

			if (waypointList.get(i).size() > 2) {
				String wayPointIndex = (String) waypointList.get(i).get(
						"wayPointIndex");
				String waypointco = (String) waypointList.get(i).get(
						"waypointco");
				String waypointdesc = (String) waypointList.get(i).get(
						"waypointdesc");
				String azimuth = (String) waypointList.get(i).get("azimuth");
				String direction = (String) waypointList.get(i)
						.get("direction");
				String time = (String) waypointList.get(i).get("time");
				String distance = (String) waypointList.get(i).get("distance");
				isWayPointonRouteMap = isWayPointonRouteMap(wayPointIndex,
						waypointco, waypointdesc, azimuth, direction, time,
						distance);
			}

			else

			{

				String wayPointIndex = (String) waypointList.get(i).get(
						"wayPointIndex");
				String waypointdesc = (String) waypointList.get(i).get(
						"waypointdesc");
				verifyInstructionThroughUI(wayPointIndex, waypointdesc);
				isWayPointonRouteMap = true;

			}

		}
		return isWayPointonRouteMap;

	}

	public void verifyTotalRouteTime(String totalRouteTime)
			throws ParseException {

		SimpleDateFormat formatter = new SimpleDateFormat("H'h'mm'min'");
		formatter.setTimeZone(TimeZone.getTimeZone("GMT"));
		Date eTime, aTime;

		String actualTime = "0h00min";
		String expectedTime = totalRouteTime.trim().replaceAll(" ", "");
		eTime = formatter.parse(expectedTime);
		aTime = formatter.parse(actualTime);

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

			aTime.setTime(GPHService.getTotalRouteTime());
			LOG.info("The total route time expected is " + eTime.getTime()
					+ " and actual is " + aTime.getTime());
			assertTrue("The total route time expected " + eTime.getTime()
					+ " is not matchin with actual " + aTime.getTime(),
					aTime.getTime() <= eTime.getTime());

			actualTime = getValue(TOTAL_ROUTE_TIME).split("take ")[1].trim()
					.replaceAll(" ", "");
			if (!actualTime.contains("h")) {
				actualTime = "00h" + actualTime;
			}
			if (!actualTime.contains("min")) {
				actualTime = actualTime + "00min";
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

	public void isTrackPointonRouteMap(List<Map<String, String>> trackPointsList)
			throws ParseException {

		for (int i = 0; i < trackPointsList.size(); i++) {

			String waypointco = (String) trackPointsList.get(i).get(
					"trackPointco");
			// String time = (String) trackPointsList.get(i).get("time");

			Waypoint trackPoint = buildWayPoint(waypointco);
			assertTrue(GPHService.isWayPointOnTrack(trackPoint, GPHService
					.getTracks().iterator().next()));

		}

	}

	public void isTrackPointNotonRouteMap(
			List<Map<String, String>> trackPointsList) throws ParseException {

		for (int i = 0; i < trackPointsList.size(); i++) {

			String waypointco = (String) trackPointsList.get(i).get(
					"trackPointco");
			// String time = (String) trackPointsList.get(i).get("time");

			Waypoint trackPoint = buildWayPoint(waypointco);
			assertTrue(!GPHService.isWayPointOnTrack(trackPoint, GPHService
					.getTracks().iterator().next()));

		}

	}

	public BufferedImage takescreen(String testID) throws IOException {

		File file = new File(testID + "_screenshot.png");

		File screenshot = takeScreenShot();
		actualMap = resize(ImageIO.read(screenshot), 1000, 800);
		// actualMap = ImageIO.read(screenshot);

		ImageIO.write(actualMap, "png", file);
		return actualMap;

	}

	public byte[] takescreenAsBiteArray() throws IOException {

		byte[] screenshot = takeScreenShotAsBiteArray();

		return screenshot;

	}

	public void compareMapImage(String expectedMap, String testID)
			throws IOException {
		takescreen(testID);

		File file = new File(expectedMap);
		BufferedImage expactedImage = resize(ImageIO.read(file), 1000, 800);
		// BufferedImage expactedImage = ImageIO.read(file);
		System.out.println(" width" + expactedImage.getWidth());
		System.out.println(" Height" + expactedImage.getHeight());
		System.out.println(" width" + actualMap.getWidth());
		System.out.println(" Height" + actualMap.getHeight());

		ImageComparison img = new ImageComparison(expactedImage, actualMap);

		img.compare();
		if (!img.match()) {
			String failPath = expectedMap + ".fail-" + testID + ".png";
			String comparePath = expectedMap + ".actual-" + testID + ".png";
			ImageIO.write(img.getChangeIndicator(), "png", new File(failPath));
			ImageIO.write(actualMap, "png", new File(comparePath));
			ImageIO.write(expactedImage, "png", new File("new.png"));
			fail("Image comparison failed see " + failPath + " for details");
		}

	}

	public BufferedImage resize(BufferedImage img, int newW, int newH) {
		// Getting the width and height of the given image.
		int w = img.getWidth();
		int h = img.getHeight();
		// Creating a new image object with the new width and height and with
		// the old image type
		BufferedImage dimg = new BufferedImage(newW, newH, img.getType());
		Graphics2D g = dimg.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
				RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		// Creating a graphics image for the new Image.
		g.drawImage(img, 0, 0, newW, newH, 0, 0, w, h, null);
		g.dispose();
		return dimg;

	}

	public void verifyWayPointsThroughService() {

	}
}
